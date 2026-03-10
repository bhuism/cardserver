package nl.appsource.cardserver.aiplayer.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
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
import static nl.appsource.cardserver.couchbase.utils.GameEngineImpl.isAiPlayer;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class AiWorker {

    private final RedisPubSubService redisPubSubService;

    private final GameRepository gameRepository;

    private final Environment environment;

    private final JsonMapper jsonMapper;

    @PostConstruct
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findAll()
                .filter((game) -> game.getTurns().size() != 32)
                .map(Game::getId)
                .subscribe(this::scheduleNext);
        }

        redisPubSubService.listenTo(AI_USER_ID).subscribe(myServerSentEvent -> {
            if (myServerSentEvent.event().equals("updateGame")) {
                log.info("gameUpdate to gameId={}", myServerSentEvent.data());
                final Game game = jsonMapper.convertValue(myServerSentEvent.data(), Game.class);
                scheduleNext(game.getId());
            }
        });

    }

    private Mono<String> scheduleNext(final String gameId) {

        return gameRepository.findById(gameId)
            .map(GameEngineImpl::new)
            .flatMap(gameEngine -> {

                if (gameEngine.isCompleted()) {
                    return Mono.empty();
                }

                if (gameEngine.getGame()
                    .getLastTrickOpen()) {
                    return Mono.empty();
                }


                if (gameEngine.isAiSay()) {
                    final String userId = gameEngine.game().getPlayers().get(gameEngine.calcWhoSay());

                    if (!isAiPlayer(userId)) {
                        return Mono.empty();
                    }

                    final boolean say = new AiPlayer(gameEngine).decideBid(userId);

                    return redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(new GameEvent().gameId(gameEngine.getGame().getId()).userId(userId).eventType(GameEvent.EventTypeEnum.SAY).say(say).executionTime(System.currentTimeMillis() + 2000 + ThreadLocalRandom.current().nextLong(1000))))
                        .then(Mono.just(gameId));

                } else if (gameEngine.isAiTurn()) {

                    final String userId = gameEngine.game().getPlayers().get(gameEngine.calcWhoHasTurn());

                    if (!isAiPlayer(userId)) {
                        return Mono.empty();
                    }

                    final Card card = new AiPlayer(gameEngine).calcAiCard(userId);

                    return redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(new GameEvent().gameId(gameEngine.getGame().getId()).userId(userId).eventType(GameEvent.EventTypeEnum.PLAY_CARD).card(convertCard(card)).executionTime(System.currentTimeMillis() + (gameEngine.isFullTrick() ? 4000 : 2000) + ThreadLocalRandom.current().nextLong(500))))
                        .then(Mono.just(gameId));

                } else {
                    return Mono.empty();
                }
            });

    }

}
