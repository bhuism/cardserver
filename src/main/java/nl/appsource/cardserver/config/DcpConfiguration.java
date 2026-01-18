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
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.SseEvent;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.service.MyServerSentEvent;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
    public Client dcpClient(final SseSessionRepository sseSessionRepository) {
        final Client client = Client.builder()
            .connectionString(cardServerCouchbaseProperties.getConnectionString())
            .bucket(cardServerCouchbaseProperties.getBucketName())
            .credentials(cardServerCouchbaseProperties.getUsername(), cardServerCouchbaseProperties.getPassword())
            .build();

        client.controlEventHandler((flowController, event) -> {
            flowController.ack(event);
            event.release();
        });

//        client.systemEventHandler(event -> {
//            log.info("system event: keys: " + event.toMap().keySet());
//        });

        client.dataEventHandler((flowController, event) -> {

            final String id = MessageUtil.getKeyAsString(event);
            final long cas = MessageUtil.getCas(event);


            final long revSeq = DcpMutationMessage.revisionSeqno(event);

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

                                log.info("Received SseEvent from db: appIdentifier: {}, userId: {}, event: {} ", sseEvent.getAppIdentifier(), sseEvent.getUserId(), sseEvent.getEvent());
                                sseEmitterRepository.send(sseEvent.getAppIdentifier(), sseEvent.getUserId(), new MyServerSentEvent(sseEvent.getEvent(), sseEvent.getData()));

                            }
                            case "nl.appsource.cardserver.model.SseSession" -> {
                                final SseSession sseSession = jsonMapper.treeToValue(rootNode, SseSession.class);
                                sseSession.setId(id);
                            }
                            case "nl.appsource.cardserver.model.Feedback" -> {
                            }
                            case "nl.appsource.cardserver.model.Boom" -> {
                                final Boom boom = jsonMapper.treeToValue(rootNode, Boom.class);
                                boom.setId(id);
                                sseEmitterRepository.updateBoom(boom);
                            }
                            case "nl.appsource.cardserver.model.User" -> {
                                final User user = jsonMapper.treeToValue(rootNode, User.class);
                                user.setId(id);
                                sseEmitterRepository.updateUser(user);
                            }
                            case "nl.appsource.cardserver.model.Game" -> {
                                final Game game = jsonMapper.treeToValue(rootNode, Game.class);
                                game.setId(id);
                                sseEmitterRepository.updateGame(game);
                            }
                            default -> log.warn("Not handling unknown class mutation : " + className);
                        }

                    }

                } catch (IOException e) {
                    log.warn("Can not deserialize key: " + id);
                }

//                if (!SseSession.class.getName().equals(className) && !SseEvent.class.getName().equals(className)) {
//                    log.info("Mutation: key={} revSeq={} cas={} class={} version={}", id, revSeq, cas, className, version);
//                }

            } /* else if (DcpDeletionMessage.is(event)) {
                log.info("Deletion: key={} content={}", key, MessageUtil.getContentAsString(event));
            } else if (DcpExpirationMessage.is(event)) {
                log.info("Expiration: key={} content={}", key, MessageUtil.getContentAsString(event));
            } else {
                log.warn("Unknown opcode opcode={}", MessageUtil.getShortOpcodeName(event));
            } */

            flowController.ack(event);
            event.release();

        });

        client.connect().block();
        client.initializeState(StreamFrom.NOW, StreamTo.INFINITY).block();
        client.startStreaming().block();

        return client;
    }

//    @PreDestroy
//    public void destroy(final Client client) {
//        log.info("Stopping");
//        client.stopStreaming(Collections.emptyList()).block();
//        client.disconnect().block();
//    }

}
