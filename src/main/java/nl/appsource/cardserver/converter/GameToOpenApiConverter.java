package nl.appsource.cardserver.converter;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.Card;
import org.openapitools.model.GamePlayerCardInner;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class GameToOpenApiConverter implements Converter<Game, org.openapitools.model.Game> {

    private final UserRepository userRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public org.openapitools.model.Game convert(final Game source) {

        final org.openapitools.model.Game target = new org.openapitools.model.Game();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setPlayerCard(source.getPlayerCard().entrySet().stream().map(cardIntegerEntry -> {
            final GamePlayerCardInner gamePlayerCardInner = new GamePlayerCardInner();
            gamePlayerCardInner.setCard(GameToOpenApiConverter.convertCard(cardIntegerEntry.getKey()));
            gamePlayerCardInner.setPlayer(cardIntegerEntry.getValue());
            return gamePlayerCardInner;
        }).collect(Collectors.toCollection(ArrayList::new)));
        target.setElder(Optional.ofNullable(source.getElder()));
        target.setPlayers(source.getPlayers());
        target.setTrump(GameToOpenApiConverter.convertSuit(source.getTrump()));
        target.setTurns(GameToOpenApiConverter.convertToOpenApi(source.getTurns()));

        return target;

    }

    private static List<Card> convertToOpenApi(final List<nl.appsource.cardserver.model.Card> source) {
        return source.stream().map(GameToOpenApiConverter::convertCard).collect(Collectors.toCollection(ArrayList::new));
    }

    public static org.openapitools.model.Card convertCard(final nl.appsource.cardserver.model.Card source) {
        return org.openapitools.model.Card.fromValue(source.name());
    }

    public static nl.appsource.cardserver.model.Card convertCard(final org.openapitools.model.Card source) {
        return nl.appsource.cardserver.model.Card.valueOf(source.getValue());
    }

//    public static org.openapitools.model.Card convertCard(final nl.appsource.cardserver.model.Card source) {
//        return org.openapitools.model.Card.valueOf(source.name());
//    }


    private static final Map<Suit, org.openapitools.model.Suit> SUITCONVERTER = Map.of(
        Suit.Clubs, org.openapitools.model.Suit.CLUBS,
        Suit.Hearts, org.openapitools.model.Suit.HEARTS,
        Suit.Spades, org.openapitools.model.Suit.SPADES,
        Suit.Diamonds, org.openapitools.model.Suit.DIAMONDS);

    private static org.openapitools.model.Suit convertSuit(final Suit trump) {
        return Optional.ofNullable(trump).map(SUITCONVERTER::get).orElse(null);
    }

//    private static final Map<org.openapitools.model.Suit, Suit> SUITCONVERTER_REVERSE = Map.of(
//        org.openapitools.model.Suit.CLUBS, Suit.Clubs,
//        org.openapitools.model.Suit.HEARTS, Suit.Hearts,
//        org.openapitools.model.Suit.SPADES, Suit.Spades,
//        org.openapitools.model.Suit.DIAMONDS, Suit.Diamonds);


}
