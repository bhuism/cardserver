package nl.appsource.cardserver.converter;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Boom;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoomToOpenApiConverter implements Converter<Boom, org.openapitools.model.Boom> {

    @Override
    public org.openapitools.model.Boom convert(final Boom source) {

        final org.openapitools.model.Boom target = new org.openapitools.model.Boom();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setPlayers(source.getPlayers());
        target.setGames(source.getGames());

        return target;

    }


}
