package nl.appsource.cardserver.converter;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.controller.GameConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.GamePlayerCardInner;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static nl.appsource.cardserver.controller.GameConverter.convertToOpenApi;

@RequiredArgsConstructor
@Component
public class GameToOpenApiConverter implements Converter<Game, org.openapitools.model.Game> {

    private final UserRepository userRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public org.openapitools.model.Game convert(Game source) {

        final org.openapitools.model.Game target = new org.openapitools.model.Game();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setPlayerCard(source.getPlayerCard().entrySet().stream().map(cardIntegerEntry -> {
            final GamePlayerCardInner gamePlayerCardInner = new GamePlayerCardInner();
            gamePlayerCardInner.setCard(GameConverter.convert(cardIntegerEntry.getKey()));
            gamePlayerCardInner.setPlayer(cardIntegerEntry.getValue());
            return gamePlayerCardInner;
        }).collect(toSet()));
        target.setElder(Optional.ofNullable(source.getElder()));
        target.setEnded(source.getEnded());
        target.setPlayers(
            source.getPlayers()
                .stream()
                .map(userRepository::findById)
                .flatMap(Optional::stream)
                .map(userToOpenApiConverter::convert)
                .collect(Collectors.toCollection(ArrayList::new))
        );

        target.setTrump(GameConverter.convert(source.getTrump()));
        target.setTurns(convertToOpenApi(source.getTurns()));

        return target;

    }
}
