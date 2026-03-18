package nl.appsource.cardserver.aiplayer.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngine;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ThreadLocalRandom;

import static nl.appsource.cardserver.converters.service.GameToOpenApiConverter.convertCard;
import static nl.appsource.cardserver.couchbase.utils.GameEngineImpl.AI_USER_ID;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class AiWorker {

    private final RedisStreamService redisStreamService;

    private final GameRepository gameRepository;

    private final Environment environment;

    private final JsonMapper jsonMapper;

    @PostConstruct
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findAll()
                .filter((game) -> game.getTurns().size() != 32)
                .filter((game) -> !game.getLastTrickOpen())
                .doOnNext((game) -> log.info("AiWorker startup for game: {}", game.getId()))
                .flatMap((Game game) -> {
                    final GameEngine gameEngine = new GameEngineImpl(game);
                    if (gameEngine.isAiSay()) {
                        final String userId = gameEngine.getGame().getPlayers().get(gameEngine.calcWhoSay());
                        return say(game.getId(), userId);
                    } else if (gameEngine.isAiTurn()) {
                        final String userId = gameEngine.getGame().getPlayers().get(gameEngine.calcWhoHasTurn());
                        return playCard(game.getId(), userId);
                    } else {
                        return Mono.empty();
                    }
                })
                .subscribe();
        }


        AI_USER_ID.forEach(aiUserId -> {
            redisStreamService.consumeFromStream(aiUserId, aiUserId + "-group", record -> {
                final String userId = record.getStream();

                if (!aiUserId.equals(userId)) {
                    log.error("Received event for wrong user: {} {}", aiUserId, userId);
                    return Mono.empty();
                }

                final GameEvent gameEvent = record.getValue();

                return switch (gameEvent.getEventType()) {
                    case SAY -> say(gameEvent.getGameId(), gameEvent.getUserId()).then();
                    case PLAY_CARD -> playCard(gameEvent.getGameId(), gameEvent.getUserId()).then();
                    default -> Mono.empty();
                };

            });
        });


//        redisPubSubService.listenToMessage(AI_USER_ID)
//            .doOnNext(myServerSentEventEntry -> {
////                log.info("AiWorker received event: {}", myServerSentEvent.event());
//                if (myServerSentEventEntry.getValue().event().equals("updateGame")) {
//                    final GameEvent gameEvent = myServerSentEventEntry.getValue().data();
////                    log.info("updateGame to game={} class={}", myServerSentEvent.data(), myServerSentEvent.data().getClass());
//                    final nl.appsource.generated.openapi.model.Game game = jsonMapper.convertValue(gameEvent, nl.appsource.generated.openapi.model.Game.class);
//                    scheduleNext(game.getId(), myServerSentEventEntry.getKey()).subscribe();
//                }
//            }).subscribe();

    }

    private Mono<String> say(final String gameId, final String userId) {

        log.info("say() for gameId={} userId={}", gameId, userId);

        if (userId == null || gameId == null) {
            log.error("say() for gameId={} userId={}", gameId, userId);
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

                    return redisStreamService.publishToStream("gameEvent", new GameEvent().gameId(gameEngine.getGame().getId()).userId(userId).eventType(GameEvent.EventTypeEnum.SAY).say(say).executionTime(System.currentTimeMillis() + 2000 + ThreadLocalRandom.current().nextLong(1000)))
                        .then(Mono.just(gameId));

                } else {
                    return Mono.empty();
                }

            });

    }

    private Mono<String> playCard(final String gameId, final String userId) {

        log.info("playcard() for gameId={} userId={}", gameId, userId);

        if (userId == null || gameId == null) {
            log.error("playCard() for gameId={} userId={}", gameId, userId);
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

                if (gameEngine.isAiTurn()) {

                    if (!userId.equals(gameEngine.game().getPlayers().get(gameEngine.calcWhoHasTurn()))) {
                        // not my turn
                        return Mono.empty();
                    }

                    final Card card = new AiPlayer(gameEngine).calcAiCard(userId);

//                    log.info("In Game {}, AiPLayer {} plays: {}", gameId, userId, card);

                    return redisStreamService.publishToStream("gameEvent", new GameEvent().gameId(gameEngine.getGame().getId()).userId(userId).eventType(GameEvent.EventTypeEnum.PLAY_CARD).card(convertCard(card)).executionTime(System.currentTimeMillis() + (gameEngine.isFullTrick() ? 4000 : 2000) + ThreadLocalRandom.current().nextLong(500)))
                        .then(Mono.just(gameId));

                } else {
                    return Mono.empty();
                }
            });

    }


}
