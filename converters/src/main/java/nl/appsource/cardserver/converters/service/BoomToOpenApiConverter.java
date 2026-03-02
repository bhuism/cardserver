package nl.appsource.cardserver.converters.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.couchbase.model.Boom;
import nl.appsource.generated.openapi.model.AiRisc;
import nl.appsource.generated.openapi.model.GameVariant;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoomToOpenApiConverter implements Converter<@NonNull Boom, nl.appsource.generated.openapi.model.Boom> {

    @Override
    public @NonNull nl.appsource.generated.openapi.model.Boom convert(final Boom source) {

        final nl.appsource.generated.openapi.model.Boom target = new nl.appsource.generated.openapi.model.Boom();

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
    }

}
