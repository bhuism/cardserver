package nl.appsource.cardserver.controller;

import nl.appsource.cardserver.model.Suit;
import org.openapitools.model.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GameConverter {

//    public static Game convert(final nl.appsource.cardserver.model.Game source) {
//
//        final Game target = new Game();
//
//        target.setId(source.getId());
//        target.setCreated(source.getCreated());
//        target.setUpdated(source.getUpdated());
//        target.setCreator(source.getCreator());
//        target.setDealer(source.getDealer());
//        target.setPlayerCard(source.getPlayerCard().entrySet().stream().map(cardIntegerEntry -> {
//            final GamePlayerCardInner gamePlayerCardInner = new GamePlayerCardInner();
//            gamePlayerCardInner.setCard(convert(cardIntegerEntry.getKey()));
//            gamePlayerCardInner.setPlayer(cardIntegerEntry.getValue());
//            return gamePlayerCardInner;
//        }).collect(toSet()));
//        target.setElder(Optional.ofNullable(source.getElder()));
//        target.setEnded(source.getEnded());
//        target.setPlayers(source.getPlayers());
//        target.setTrump(convert(source.getTrump()));
//        target.setTurns(convertToOpenApi(source.getTurns()));
//
//        return target;
//    }

//    public static nl.appsource.cardserver.model.Game convert(final Game source) {
//
//        nl.appsource.cardserver.model.Game target = new nl.appsource.cardserver.model.Game();
//
//        target.setId(source.getId());
//        target.setCreated(source.getCreated());
//        target.setUpdated(source.getUpdated());
//        target.setCreator(source.getCreator());
//        target.setDealer(source.getDealer());
//
//        target.setPlayerCard(source.getPlayerCard().stream()
//            .collect(Collectors
//                .toMap(pc -> convert(pc.getCard()), GamePlayerCardInner::getPlayer)
//            )
//        );
//
//        target.setElder(source.getElder().orElse(null));
//        target.setEnded(source.getEnded());
//        target.setPlayers(source.getPlayers());
//        target.setTrump(convert(source.getTrump()));
//        target.setTurns(convertToModel(source.getTurns()));
//
//        return target;
//    }

    public static List<Card> convertToOpenApi(final List<nl.appsource.cardserver.model.Card> source) {
        return source.stream().map(GameConverter::convert).collect(Collectors.toCollection(ArrayList::new));
    }

    public static org.openapitools.model.Card convert(final nl.appsource.cardserver.model.Card source) {
        return org.openapitools.model.Card.fromValue(source.name());
    }

//    public static List<nl.appsource.cardserver.model.Card> convertToModel(final List<org.openapitools.model.Card> source) {
//        return source.stream().map(GameConverter::convert).collect(Collectors.toCollection(ArrayList::new));
//    }

    public static nl.appsource.cardserver.model.Card convert(final org.openapitools.model.Card source) {
        return nl.appsource.cardserver.model.Card.valueOf(source.getValue());
    }


    private static final Map<Suit, org.openapitools.model.Suit> SUITCONVERTER = Map.of(
        Suit.Clubs, org.openapitools.model.Suit.CLUBS,
        Suit.Hearts, org.openapitools.model.Suit.HEARTS,
        Suit.Spades, org.openapitools.model.Suit.SPADES,
        Suit.Diamonds, org.openapitools.model.Suit.DIAMONDS);

    public static org.openapitools.model.Suit convert(final Suit trump) {
        return Optional.ofNullable(trump).map(SUITCONVERTER::get).orElse(null);
    }

    private static final Map<org.openapitools.model.Suit, Suit> SUITCONVERTER_REVERSE = Map.of(
        org.openapitools.model.Suit.CLUBS, Suit.Clubs,
        org.openapitools.model.Suit.HEARTS, Suit.Hearts,
        org.openapitools.model.Suit.SPADES, Suit.Spades,
        org.openapitools.model.Suit.DIAMONDS, Suit.Diamonds);


//    public static nl.appsource.cardserver.model.Card convert(final PlayCard playCard) {
//        return null;
//    }

    public static Suit convert(final org.openapitools.model.Suit trump) {
        return Optional.ofNullable(trump).map(SUITCONVERTER_REVERSE::get).orElse(null);
    }

}
