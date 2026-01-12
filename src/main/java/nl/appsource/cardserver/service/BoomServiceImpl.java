package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.repository.BoomRepository;
import org.openapitools.model.GameVariant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static nl.appsource.cardserver.utils.IDTYPE.BOOM;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoomServiceImpl implements BoomService {

    private final BoomRepository boomRepository;

    private final SseEventSender sseEventSender;

    private static final Random RAND = new SecureRandom();

    @Override
    public Mono<Boom> getBoom(final String userId, final String boomId) {
        return boomRepository.findById(boomId);
    }

    @Override
    public Mono<Boom> createBoom(final String creator, final List<String> players, final GameVariant gameVariant) {
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
            })
            .flatMap(boomRepository::save)
            .flatMap((boom) -> sseEventSender.boomsChanged(boom.getPlayers()).then(Mono.just(boom)));
    }

    @Override
    public Flux<String> getBooms(final String userId) {
        return boomRepository.findByUserId(userId, Integer.MAX_VALUE);
    }
}
