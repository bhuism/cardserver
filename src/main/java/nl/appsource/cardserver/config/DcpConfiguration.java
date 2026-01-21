package nl.appsource.cardserver.config;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBufInputStream;
import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.MessageUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.SingleEvent;
import nl.appsource.cardserver.model.SseEvent;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.SingleEventRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.MyServerSentEvent;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

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

    private JsonMapper jsonMapper;

    private final SseEmitterRepository sseEmitterRepository;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final UserRepository userRepository;

    private final SingleEventRepository singleEventRepository;

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

    @PostConstruct
    public void postConstruct() {
        jsonMapper = JsonMapper.builder()
            .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .build();
    }

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

                String className = "";

                try (InputStream is = new ByteBufInputStream(MessageUtil.getContent(event))) {

                    final JsonNode rootNode = jsonMapper.readTree(is);

                    if (rootNode.isObject()) {
                        className = rootNode.get("_class").asString();
                        switch (className) {
                            case "nl.appsource.cardserver.model.SseEvent" -> {
                                final SseEvent sseEvent = jsonMapper.treeToValue(rootNode, SseEvent.class);
                                sseEvent.setId(id);

//                                log.info("Received SseEvent from db: appIdentifier: {}, userId: {}, event: {} ", sseEvent.getAppIdentifier(), sseEvent.getUserId(), sseEvent.getEvent());
                                sseEmitterRepository.send(sseEvent.getAppIdentifier(), sseEvent.getUserId(), new MyServerSentEvent(sseEvent.getEvent(), sseEvent.getData()));

                            }
                            case "nl.appsource.cardserver.model.Boom" -> {
                                final Boom boom = jsonMapper.treeToValue(rootNode, Boom.class);
                                boom.setId(id);

                                boomToOpenApiConverter.convert(boom).subscribe(convertedBoom ->
                                    Flux.fromIterable(boom.getPlayers())
                                        .concatWith(Flux.just(boom.getCreator()))
                                        .distinct()
                                        .subscribe(player -> sseEmitterRepository.send(null, player, MyServerSentEvent.updateBoom(convertedBoom))));

                            }
                            case "nl.appsource.cardserver.model.User" -> {
                                final User user = jsonMapper.treeToValue(rootNode, User.class);
                                user.setId(id);

                                final org.openapitools.model.User convertedUser = userToOpenApiConverter.convert(user);
                                userRepository.getOnlineFriends(user.getId()).mergeWith(Mono.just(user.getId())).subscribe(friend -> sseEmitterRepository.send(null, friend, MyServerSentEvent.updateUser(convertedUser)));

                            }
                            case "nl.appsource.cardserver.model.Game" -> {
                                final Game game = jsonMapper.treeToValue(rootNode, Game.class);
                                game.setId(id);

                                final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
                                game.getPlayers().forEach(player -> sseEmitterRepository.send(null, player, MyServerSentEvent.updateGame(convertedGame)));

                            }

                            case "nl.appsource.cardserver.model.SingleEvent" -> {
                                final SingleEvent singleEvent = jsonMapper.treeToValue(rootNode, SingleEvent.class);
                                singleEvent.setId(id);

                                //                              log.info("Got SingleEvent " + id + ", lockedBy: " + singleEvent.getLockedBy() + ", handledBy: " + singleEvent.getHandledBy() + " raw=" + MessageUtil.getContentAsString(event));

                                if (singleEvent.isUnlocked()) {
//                                    log.info("Got SingleEvent unlocked, trying to lock " + id);
                                    // try to get the lock
                                    singleEventRepository.lockById(singleEvent.getId(), HOSTNAME)
                                        .subscribe(unused -> {
                                        }, throwable -> {
                                            log.info("Got excetion locking SingleEvent " + id + ", exception=: " + throwable.getClass().getName());
                                        });
                                } else if (singleEvent.isLockedBy(HOSTNAME)) {
                                    // we can process it
                                    log.info("Processing SingleEvent id=" + id + " in host=" + HOSTNAME + ", event=" + singleEvent.getEvent());
                                    singleEventRepository.handledBy(singleEvent.getId(), HOSTNAME).subscribe();
                                }
                            }
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
