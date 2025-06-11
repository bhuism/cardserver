package nl.appsource.cardserver.controller;

import nl.appsource.cardserver.model.CardNr;
import nl.appsource.cardserver.model.Suit;
import org.openapitools.model.Card;
import org.openapitools.model.Game;
import org.openapitools.model.GamePlayerCardInner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GameConverter {

    public static Game convert(final nl.appsource.cardserver.model.Game source) {

        final Game target = new Game();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setPlayerCard(source.getPlayerCard().entrySet().stream().map(cardIntegerEntry -> {
            final GamePlayerCardInner gamePlayerCardInner = new GamePlayerCardInner();
            gamePlayerCardInner.setCard(convert(cardIntegerEntry.getKey()));
            gamePlayerCardInner.setPlayer(cardIntegerEntry.getValue());
            return gamePlayerCardInner;
        }).collect(Collectors.toCollection(ArrayList::new)));
        target.setElder(Optional.ofNullable(source.getElder()));
        target.setEnded(source.getEnded());
        target.setPlayers(source.getPlayers());
        target.setTrump(convert(source.getTrump()));
        target.setTurns(convertToOpenApi(source.getTurns()));

        return target;
    }

    public static nl.appsource.cardserver.model.Game convert(final Game source) {

        nl.appsource.cardserver.model.Game target = new nl.appsource.cardserver.model.Game();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());

        target.setPlayerCard(source.getPlayerCard().stream()
            .collect(Collectors
                .toMap(pc -> convert(pc.getCard()), GamePlayerCardInner::getPlayer)
            )
        );

        target.setElder(source.getElder().orElse(null));
        target.setEnded(source.getEnded());
        target.setPlayers(source.getPlayers());
        target.setTrump(convert(source.getTrump()));
        target.setTurns(convertToModel(source.getTurns()));

        return target;
    }


    public static List<Card> convertToOpenApi(final List<nl.appsource.cardserver.model.Card> source) {
        return source.stream().map(GameConverter::convert).collect(Collectors.toCollection(ArrayList::new));
    }

    public static org.openapitools.model.Card convert(final nl.appsource.cardserver.model.Card source) {
        final org.openapitools.model.Card result = new org.openapitools.model.Card();
        result.setColor(convert(source.getSuit()));
        result.setCard(convert(source.getCardNr()));
        return result;
    }

    public static List<nl.appsource.cardserver.model.Card> convertToModel(final List<org.openapitools.model.Card> source) {
        return source.stream().map(GameConverter::convert).collect(Collectors.toCollection(ArrayList::new));
    }

    public static nl.appsource.cardserver.model.Card convert(final org.openapitools.model.Card source) {

        return switch (source.getColor()) {
            case HEARTS -> {
                yield switch (source.getCard()) {
                    case ACE -> nl.appsource.cardserver.model.Card.Ah;
                    case KING -> nl.appsource.cardserver.model.Card.Kh;
                    case QUEEN -> nl.appsource.cardserver.model.Card.Qh;
                    case JACK -> nl.appsource.cardserver.model.Card.Jh;
                    case TEN -> nl.appsource.cardserver.model.Card.Th;
                    case NINE -> nl.appsource.cardserver.model.Card.Nh;
                    case EIGHT -> nl.appsource.cardserver.model.Card.Eh;
                    case SEVEN -> nl.appsource.cardserver.model.Card.Sh;
                };
            }
            case CLUBS -> {
                yield switch (source.getCard()) {
                    case ACE -> nl.appsource.cardserver.model.Card.Ac;
                    case KING -> nl.appsource.cardserver.model.Card.Kc;
                    case QUEEN -> nl.appsource.cardserver.model.Card.Qc;
                    case JACK -> nl.appsource.cardserver.model.Card.Jc;
                    case TEN -> nl.appsource.cardserver.model.Card.Tc;
                    case NINE -> nl.appsource.cardserver.model.Card.Nc;
                    case EIGHT -> nl.appsource.cardserver.model.Card.Ec;
                    case SEVEN -> nl.appsource.cardserver.model.Card.Sc;
                };
            }
            case SPADES -> {
                yield switch (source.getCard()) {
                    case ACE -> nl.appsource.cardserver.model.Card.As;
                    case KING -> nl.appsource.cardserver.model.Card.Ks;
                    case QUEEN -> nl.appsource.cardserver.model.Card.Qs;
                    case JACK -> nl.appsource.cardserver.model.Card.Js;
                    case TEN -> nl.appsource.cardserver.model.Card.Ts;
                    case NINE -> nl.appsource.cardserver.model.Card.Ns;
                    case EIGHT -> nl.appsource.cardserver.model.Card.Es;
                    case SEVEN -> nl.appsource.cardserver.model.Card.Ss;
                };
            }
            case DIAMONDS -> {
                yield switch (source.getCard()) {
                    case ACE -> nl.appsource.cardserver.model.Card.Ad;
                    case KING -> nl.appsource.cardserver.model.Card.Kd;
                    case QUEEN -> nl.appsource.cardserver.model.Card.Qd;
                    case JACK -> nl.appsource.cardserver.model.Card.Jd;
                    case TEN -> nl.appsource.cardserver.model.Card.Td;
                    case NINE -> nl.appsource.cardserver.model.Card.Nd;
                    case EIGHT -> nl.appsource.cardserver.model.Card.Ed;
                    case SEVEN -> nl.appsource.cardserver.model.Card.Sd;
                };
            }
        };
    }

    private static final Map<CardNr, org.openapitools.model.CardNr> CARDCONVERTER = Map.of(
        CardNr.Ace, org.openapitools.model.CardNr.ACE,
        CardNr.King, org.openapitools.model.CardNr.KING,
        CardNr.Queen, org.openapitools.model.CardNr.QUEEN,
        CardNr.Jack, org.openapitools.model.CardNr.JACK,
        CardNr.Ten, org.openapitools.model.CardNr.TEN,
        CardNr.Nine, org.openapitools.model.CardNr.NINE,
        CardNr.Eight, org.openapitools.model.CardNr.EIGHT,
        CardNr.Seven, org.openapitools.model.CardNr.SEVEN
    );

    public static org.openapitools.model.CardNr convert(final CardNr source) {
        return Optional.ofNullable(source).map(CARDCONVERTER::get).orElse(null);
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
