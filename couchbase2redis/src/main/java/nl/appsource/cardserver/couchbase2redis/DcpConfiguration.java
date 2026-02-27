package nl.appsource.cardserver.couchbase2redis;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBufInputStream;
import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.model.Boom;
import nl.appsource.cardserver.couchbase.model.Game;
import nl.appsource.cardserver.couchbase.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!citest")
public class DcpConfiguration {

    private final CardServerCouchbaseProperties cardServerCouchbaseProperties;

    private final JsonMapper jsonMapper;

    private final RedisPublisher redisPublisher;

//    private final SseEmitterRepository sseEmitterRepository;
//
    private final BoomToOpenApiConverter boomToOpenApiConverter;
//
    private final UserToOpenApiConverter userToOpenApiConverter;
//
    private final GameToOpenApiConverter gameToOpenApiConverter;
//
//    private final UserRepository userRepository;

//    private final SingleEventRepository singleEventRepository;

    private static final String HOSTNAME;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

//    @PostConstruct
//    public void postConstruct() {
//        jsonMapper = JsonMapper.builder()
//            .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
//            .build();
//    }

    @Bean
    public Client dcpClient() {
        final Client client = Client.builder()
            .connectionString(cardServerCouchbaseProperties.getConnectionString())
            .bucket(cardServerCouchbaseProperties.getBucketName())
            .credentials(cardServerCouchbaseProperties.getUsername(), cardServerCouchbaseProperties.getPassword())
            .build();

        client.controlEventHandler((flowController, event) -> {
            flowController.ack(event);
            event.release();
        });

        client.dataEventHandler((flowController, event) -> {

            final String id = MessageUtil.getKeyAsString(event);

            if (DcpMutationMessage.is(event)) {

                try (InputStream is = new ByteBufInputStream(MessageUtil.getContent(event))) {

                    final JsonNode rootNode = jsonMapper.readTree(is);

                    if (rootNode.isObject()) {

                        ((ObjectNode) rootNode).put("id", id);

                        final String className = rootNode.get("_class").asString();

                        switch (className) {
//                            case "nl.appsource.cardserver.model.SseEvent" -> {
//                                final SseEvent sseEvent = jsonMapper.treeToValue(rootNode, SseEvent.class);
//                                sseEvent.setId(id);
//
//                                if (sseEvent.getAppIdentifier() == null && sseEvent.getUserId() == null) {
//                                    log.warn("received sse event without userId and appIdentifier");
//                                }
//
////                                log.info("Received SseEvent from db: appIdentifier: {}, userId: {}, event: {} ", sseEvent.getAppIdentifier(), sseEvent.getUserId(), sseEvent.getEvent());
//                                sseEmitterRepository.send(sseEvent.getAppIdentifier(), sseEvent.getUserId(), new MyServerSentEvent(sseEvent.getEvent(), sseEvent.getData()));
//
//                            }
                            case "nl.appsource.cardserver.model.Boom" -> {
                                final Boom boom = jsonMapper.treeToValue(rootNode, Boom.class);
                                boom.setId(id);

                                redisPublisher.publish(Flux.fromIterable(boom.getPlayers())
                                    .mergeWith(Flux.just(boom.getCreator()))
                                    .distinct(), jsonMapper.writeValueAsString(boomToOpenApiConverter.convert(boom))).subscribe();

                            }
                            case "nl.appsource.cardserver.model.User" -> {
                                final User user = jsonMapper.treeToValue(rootNode, User.class);
                                user.setId(id);

                                redisPublisher.publish(user.getId(), jsonMapper.writeValueAsString(userToOpenApiConverter.convert(user))).subscribe();

                            }
                            case "nl.appsource.cardserver.model.Game" -> {
                                final Game game = jsonMapper.treeToValue(rootNode, Game.class);
                                game.setId(id);

                                redisPublisher.publish(Flux.fromIterable(game.getPlayers())
                                    .mergeWith(Flux.just(game.getCreator()))
                                    .distinct(), jsonMapper.writeValueAsString(gameToOpenApiConverter.convert(game))).subscribe();

                            }

//                            case "nl.appsource.cardserver.model.SingleEvent" -> {
//                                final SingleEvent singleEvent = jsonMapper.treeToValue(rootNode, SingleEvent.class);
//                                singleEvent.setId(id);
//
//                                //                              log.info("Got SingleEvent " + id + ", lockedBy: " + singleEvent.getLockedBy() + ", handledBy: " + singleEvent.getHandledBy() + " raw=" + MessageUtil.getContentAsString(event));
//
//                                if (singleEvent.isUnlocked()) {
////                                    log.info("Got SingleEvent unlocked, trying to lock " + id);
//                                    // try to get the lock
//                                    singleEventRepository.lockById(singleEvent.getId(), HOSTNAME)
//                                        .onErrorComplete(throwable -> throwable.getClass().equals(CasMismatchException.class))
//                                        .subscribe();
//                                } else if (singleEvent.isLockedBy(HOSTNAME)) {
//                                    // we can process it
//                                    log.info("Processing SingleEvent id=" + id + " in host=" + HOSTNAME + ", event=" + singleEvent.getEvent());
//                                    singleEventRepository.handledBy(singleEvent.getId(), HOSTNAME).subscribe();
//                                }
//                            }
                            default -> {
                            }
                        }

                    }


                } catch (IOException e) {
                    log.warn("Can not deserialize key: " + id);
                }

            }

            flowController.ack(event);
            event.release();

        });

        client.connect().block();
        client.initializeState(StreamFrom.NOW, StreamTo.INFINITY).block();
        client.startStreaming().block();

        return client;
    }

}
