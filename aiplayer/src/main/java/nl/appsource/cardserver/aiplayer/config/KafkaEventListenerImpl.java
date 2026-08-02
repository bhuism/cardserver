package nl.appsource.cardserver.aiplayer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.aiplayer.service.AiWorkerImpl;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Game;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.GAME_CHANGES;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl {

    private final JsonMapper jsonMapper;

    private final AiWorkerImpl aiWorker;

    @KafkaListener(topics = GAME_CHANGES, groupId = "aiWorker-aiWorker")
    public void listen(final @Payload Game game) {

        final GameEngineImpl gameEngine = new GameEngineImpl(game);

        if (gameEngine.isAiSay()) {
            final String aiSayPlayer = game.getPlayers()
                .get(gameEngine.calcWhoSay());
            aiWorker.say(game.getId(), aiSayPlayer)
                .block();
        } else if (gameEngine.isAiTurn()) {
            final String aiPlayPlayer = game.getPlayers()
                .get(gameEngine.calcWhoHasTurn());
            aiWorker.playCard(game.getId(), aiPlayPlayer)
                .block();
        }

    }

}
