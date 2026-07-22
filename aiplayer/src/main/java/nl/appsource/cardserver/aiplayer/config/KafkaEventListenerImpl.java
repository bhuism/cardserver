package nl.appsource.cardserver.aiplayer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.aiplayer.service.AiWorkerImpl;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Game;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.COUCHBASE_CARDSERVER_EVENTS;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl {

    private final JsonMapper jsonMapper;

    private final AiWorkerImpl aiWorker;

    @KafkaListener(topics = COUCHBASE_CARDSERVER_EVENTS, groupId = "aiWorker-aiWorker")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {

        if (documentId == null || documentPayload == null) {
            return;
        }

        try {
            final ObjectNode document = (ObjectNode) jsonMapper.readTree(documentPayload);
            document.put("id", documentId);
            log.info("Got Kafka event: documentId={}", documentId);

            if (Game.class.getName().equals(document.get("_class").asString())) {
                log.info("Got Kafka event: documentId={}, document={}", documentId, document);
            }

            final Game game = jsonMapper.convertValue(document, Game.class);

            final GameEngineImpl gameEngine = new GameEngineImpl(game);

            if (gameEngine.isAiSay()) {
                final String aiSayPlayer = game.getPlayers().get(gameEngine.calcWhoSay());
                aiWorker.say(game.getId(), aiSayPlayer).block();
            } else if (gameEngine.isAiTurn()) {
                final String aiPlayPlayer = game.getPlayers().get(gameEngine.calcWhoHasTurn());
                aiWorker.playCard(game.getId(), aiPlayPlayer).block();
            }


        } catch (final Exception e) {
            log.error("Error processing Kafka event: documentId={}", documentId, e);
        }
    }

}
