package nl.appsource.cardserver.aiplayer.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngine;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Game;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.GAME_CHANGES;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl implements KafkaEventListener {

    private final JsonMapper jsonMapper;

    private final Environment environment;

    private final GameRepository gameRepository;

    @Getter
    private final Sinks.Many<GameEngine> queue = Sinks.many()
        .multicast()
        .directBestEffort();

    @KafkaListener(topics = GAME_CHANGES, groupId = "aiWorker-aiWorker")
    public void listen(final @Payload String string) {
        try {
            final Game game = jsonMapper.readValue(string, Game.class);
            final GameEngineImpl gameEngine = new GameEngineImpl(game);
            if (gameEngine.isAiSay() || gameEngine.isAiTurn()) {
                queue.emitNext(gameEngine, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1500)));
            }
        } catch (final Exception e) {
            log.error("Error processing Kafka event: payload={}", string, e);
        }
    }

    @Override
    public Flux<GameEngine> listen() {
        return queue.asFlux();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findUnfinishedGames()
                .flatMap(gameRepository::findById)
                .filter((game) -> !game.getLastTrickOpen())
                .doOnNext((game) -> log.info("AiWorker startup for game: {}", game.getId()))
                .retryWhen(reactor.util.retry.Retry.backoff(10, Duration.ofSeconds(2))
                    .doBeforeRetry(retrySignal -> log.warn("Retrying initial game scan due to error: {}", retrySignal.failure().getMessage())))
                .subscribe(
                    (Game game) -> {
                        final GameEngine gameEngine = new GameEngineImpl(game);
                        if (gameEngine.isAiSay() || gameEngine.isAiTurn()) {
                            queue.emitNext(gameEngine, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1500)));
                        }
                    },
                    error -> log.error("Error during initial game engine scan", error)
                );
        }
    }

}
