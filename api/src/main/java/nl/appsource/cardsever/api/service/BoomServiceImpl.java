package nl.appsource.cardsever.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.AiRisc;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.GameVariant;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static nl.appsource.cardserver.utils.IDTYPE.BOOM;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoomServiceImpl implements BoomService {

    private final BoomRepository boomRepository;

    private final SseEventSender sseEventSender;

    private final GameRepository gameRepository;

    private final GameService gameService;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final RedisPubSubService redisPubSubService;

    private static final Random RAND = new SecureRandom();

    private Mono<Boom> sendUpdateBoom(final Boom boom) {
        return redisPubSubService.broadCast(Flux.fromIterable(boom.getPlayers())
                .mergeWith(Flux.just(boom.getCreator(), boom.getId()))
                .distinct(), MyServerSentEvent.updateBoom(boomToOpenApiConverter.convert(boom)))
            .thenReturn(boom);
    }

    @Override
    public Mono<Boom> getBoom(final String userId, final String boomId) {
        return boomRepository.findById(boomId);
    }

    @Override
    public Mono<Boom> createBoom(final String creator, final List<String> players, final GameVariant gameVariant, final AiRisc aiRisc) {
        if (players.size() != 4) {
            throw new IllegalArgumentException("need 4 players");
        }

        if (!StringUtils.hasText(creator)) {
            throw new IllegalArgumentException("creator cannot be empty");
        }

        if (!players.contains(creator)) {
            throw new IllegalArgumentException("creator needs to be a player");
        }

        log.info("Creating a new boom with players {}", players);

        return Mono.just(new Boom())
            .doOnNext((boom) -> {
                boom.setId(idGen(BOOM, 20));
                boom.setPlayers(new ArrayList<>(players));
                boom.setDealer(RAND.nextInt(4));
                boom.setGameVariant(gameVariant);
                boom.setAiRisc(aiRisc);
            })
            .flatMap(boomRepository::save)
            .flatMap(this::sendUpdateBoom)
            .flatMap((boom) -> sseEventSender.boomsChanged(Set.copyOf(boom.getPlayers())).then(Mono.just(boom)));
    }

    @Override
    public Flux<String> getBooms(final String userId) {
        return boomRepository.findByUserId(userId, Integer.MAX_VALUE);
    }

    @Override
    public Mono<Game> playBoom(final String userId, final String boomId) {
        return boomRepository.findById(boomId)
            .flatMap((boom) -> {
                return Mono.justOrEmpty(boom.getGames().getLast())
                    .flatMap(gameRepository::findById)
                    .filter(game -> !new GameEngineImpl(game).isCompleted())
                    .switchIfEmpty(Mono.defer(() -> {
                        if (boom.getGames().size() < 16) {
                            return calcDealer(boom)
                                .flatMap(dealer -> {
                                    return gameService.createGame(userId, boom.getPlayers(), boom.getGameVariant(), boom.getId(), dealer, boom.getAiRisc())
                                        .flatMap(game -> {
                                            boom.getGames().add(game.getId());
                                            return boomRepository.save(boom).flatMap(this::sendUpdateBoom).thenReturn(game);
                                        });
                                });
                        } else {
                            return Mono.empty();
                        }
                    }));
            });

    }

    private Mono<Integer> calcDealer(final nl.appsource.cardserver.model.Boom boom) {

        if (boom.getGames().isEmpty()) {
            return Mono.just(RAND.nextInt(4));
        }

        return boomRepository.findById(boom.getGames().getLast())
            .map(game -> (game.getDealer() + 1) % 4);

    }

}
