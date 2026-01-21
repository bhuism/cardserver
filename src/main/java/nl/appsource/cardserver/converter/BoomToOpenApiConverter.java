package nl.appsource.cardserver.converter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.repository.BoomRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BoomToOpenApiConverter implements Converter<@NonNull Boom, Mono<org.openapitools.model.Boom>> {

    private final BoomRepository boomRepository;

    @Override
    public @NonNull Mono<org.openapitools.model.Boom> convert(final Boom source) {

        return Mono.just(new org.openapitools.model.Boom())
            .map(target -> {
                target.setId(source.getId());
                target.setCreated(source.getCreated());
                target.setUpdated(source.getUpdated());
                target.setCreator(source.getCreator());
                target.setDealer(source.getDealer());
                target.setPlayers(source.getPlayers());
                target.setGames(source.getGames());
                target.setGameVariant(source.getGameVariant());
                target.setAiRisc(source.getAiRisc());
                return target;
            }).flatMap(target -> boomRepository.isBoomComplete(source.getId()).map(completed -> {
                target.setIsCompleted(completed);
                return target;
            }));
    }


}
