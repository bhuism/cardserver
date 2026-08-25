package nl.appsource.cardserver.aiplayer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.aiplayer.config.KafkaEventListener;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.openapi.service.KafkaSender;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static nl.appsource.cardserver.converters.service.GameToOpenApiConverter.convertCard;
import static nl.appsource.cardserver.openapi.config.KafkaTopics.GAME_EVENTS_TOPIC;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class AiWorkerImpl implements AiWorker {

    private final GameRepository gameRepository;

    private final KafkaSender kafkaSender;

    private final JsonMapper jsonMapper;

    private final KafkaEventListener kafkaEventListener;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {

        log.info("init()");

        kafkaEventListener.listen()
//            .doOnNext((message) -> log.info("Kafka message received: {}", message))
            .flatMap(gameEngine -> {
                if (gameEngine.isAiSay()) {
                    final String aiSayPlayer = gameEngine.getGame().getPlayers().get(gameEngine.calcWhoSay());
                    return say(gameEngine.getGame().getId(), aiSayPlayer);
                } else if (gameEngine.isAiTurn()) {
                    final String aiPlayPlayer = gameEngine.getGame().getPlayers().get(gameEngine.calcWhoHasTurn());
                    return playCard(gameEngine.getGame().getId(), aiPlayPlayer);
                } else {
                    return Mono.empty();
                }
            })
            .subscribe(
                null,
                error -> log.error("Error in AI worker main loop", error)
            );

    }

    @Override
    public Mono<Void> say(final String gameId, final String userId) {

        if (userId == null || gameId == null) {
            log.error("say() invalid arguments for gameId={} userId={}", gameId, userId);
            return Mono.empty();
        }

        return gameRepository.findById(gameId)
            .map(GameEngineImpl::new)
            .flatMap(gameEngine -> {

                if (gameEngine.isCompleted()) {
                    return Mono.empty();
                }

                if (gameEngine.getGame().getLastTrickOpen()) {
                    return Mono.empty();
                }

                if (gameEngine.isAiSay()) {

                    if (!userId.equals(gameEngine.game().getPlayers().get(gameEngine.calcWhoSay()))) {
                        // not my turn
                        return Mono.empty();
                    }

                    final boolean say = new AiPlayer(gameEngine).decideBid(userId);

//                    log.info("In Game {}, AiPLayer {} says: {}", gameId, userId, say ? "make" : "pass");

                    final GameEvent gameEvent = new GameEvent()
                        .uuid(UUID.randomUUID())
                        .gameId(gameEngine.getGame().getId())
                        .userId(userId)
                        .eventType(GameEvent.EventTypeEnum.SAY)
                        .say(say)
                        .executionTime(System.currentTimeMillis() + 1000 + ThreadLocalRandom.current().nextLong(1000));

                    kafkaSender.send(GAME_EVENTS_TOPIC, jsonMapper.writeValueAsString(gameEvent));

                    return Mono.empty();
                } else {
                    return Mono.empty();
                }

            });

    }

    @Override
    public Mono<Void> playCard(final String gameId, final String userId) {

        return gameRepository.findById(gameId)
            .map(GameEngineImpl::new)
            .flatMap(gameEngine -> {

                if (gameEngine.isCompleted()) {
                    return Mono.empty();
                }

                if (gameEngine.getGame().getLastTrickOpen()) {
                    return Mono.empty();
                }

                if (gameEngine.isAiTurn()) {

                    if (!userId.equals(gameEngine.game().getPlayers().get(gameEngine.calcWhoHasTurn()))) {
                        // not my turn
                        return Mono.empty();
                    }

                    final Card card = new AiPlayer(gameEngine).calcAiCard(userId);

//                    log.info("In Game {}, AiPLayer {} plays: {}", gameId, userId, card);

                    final GameEvent gameEvent = new GameEvent()
                        .uuid(UUID.randomUUID())
                        .gameId(gameEngine.getGame().getId())
                        .userId(userId)
                        .eventType(GameEvent.EventTypeEnum.PLAY_CARD)
                        .card(convertCard(card))
                        .executionTime(System.currentTimeMillis() + (gameEngine.isFullTrick() ? 3000 : 1000) + ThreadLocalRandom.current().nextLong(500));

                    kafkaSender.send(GAME_EVENTS_TOPIC, jsonMapper.writeValueAsString(gameEvent));

                    return Mono.empty();

                } else {
                    return Mono.empty();
                }
            });

    }


}
