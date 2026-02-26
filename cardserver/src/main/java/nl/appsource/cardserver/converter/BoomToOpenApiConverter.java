package nl.appsource.cardserver.converter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.generated.openapi.model.AiRisc;
import nl.appsource.generated.openapi.model.GameVariant;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BoomToOpenApiConverter implements Converter<@NonNull Boom, Mono<nl.appsource.generated.openapi.model.Boom>> {

    private final BoomRepository boomRepository;

    @Override
    public @NonNull Mono<nl.appsource.generated.openapi.model.Boom> convert(final Boom source) {

        return Mono.just(new nl.appsource.generated.openapi.model.Boom())
            .map(target -> {
                target.setId(source.getId());
                target.setCreated(source.getCreated());
                target.setUpdated(source.getUpdated());
                target.setCreator(source.getCreator());
                target.setDealer(source.getDealer());
                target.setPlayers(source.getPlayers());
                target.setGames(source.getGames());
                target.setGameVariant(GameVariant.valueOf(source.getGameVariant().name()));
                target.setAiRisc(AiRisc.valueOf(source.getAiRisc().name()));
                return target;
            }).flatMap(target -> boomRepository.isBoomComplete(source.getId()).map(completed -> {
                target.setIsCompleted(completed);
                return target;
            }));
    }


}
