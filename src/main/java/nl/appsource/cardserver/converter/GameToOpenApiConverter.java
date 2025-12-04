package nl.appsource.cardserver.converter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.GameEngine;
import nl.appsource.cardserver.service.GameEngineImpl;
import org.openapitools.model.Card;
import org.openapitools.model.GamePlayerCardInner;
import org.openapitools.model.NorthSouthNumber;
import org.openapitools.model.Teams;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class GameToOpenApiConverter implements Converter<@NonNull Game, org.openapitools.model.Game> {

    @NonNull
    @Override
    public org.openapitools.model.Game convert(final Game source) {

        final org.openapitools.model.Game target = new org.openapitools.model.Game();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setPlayerCard(source.getPlayerCard()
            .entrySet()
            .stream()
            .map(cardIntegerEntry -> {
                final GamePlayerCardInner gamePlayerCardInner = new GamePlayerCardInner();
                gamePlayerCardInner.setCard(GameToOpenApiConverter.convertCard(cardIntegerEntry.getKey()));
                gamePlayerCardInner.setPlayer(cardIntegerEntry.getValue());
                return gamePlayerCardInner;
            })
            .collect(Collectors.toCollection(ArrayList::new)));
        target.setPlayers(source.getPlayers());
        target.setTrump(GameToOpenApiConverter.convertSuit(source.getTrump()));
        target.setTurns(GameToOpenApiConverter.convertToOpenApi(source.getTurns()));

        final Map<String, Boolean> says = new HashMap<>();

        if (source.getSay() != null) {
            source.getSay()
                .forEach((integer, aBoolean) -> says.put("" + integer, aBoolean));
        }

        target.setSays(says);

        final GameEngine gameEngine = new GameEngineImpl(source);

        if (!gameEngine.isCompleted()) {

            if (!gameEngine.getErIsGegaan()) {
                target.setWhoSay(Optional.of(gameEngine.calcWhoSay()));
                target.setWhosTurn(Optional.empty());
            } else {
                target.setWhoSay(Optional.empty());
                target.setWhosTurn(Optional.of(gameEngine.calcWhoHasTurn()));
            }
        }

        target.setLastTrickOpen(source.getLastTrickOpen());
        target.setVariant(source.getGameVariant());
        target.setDealCounter(source.getDealCounter());

        target.setNumberOfTurns(gameEngine.getTurnCount());
        target.setIsCompleted(source.getTurns()
            .size() == 32);
        target.setHasFullTrick(gameEngine.isFullTrick());
        target.setTricksPlayed(gameEngine.calcTricksPlayed());
        target.setIedereenPast(source
            .getSay()
            .values()
            .stream()
            .filter(aBoolean -> aBoolean.equals(Boolean.FALSE))
            .count() == 4);
        target.setNiemandGezegd(source
            .getSay()
            .isEmpty());
        target.setGeenKaartGespeeld(gameEngine.getTurnCount() == 0);
        target.setHuidigeTafelKaarten(gameEngine.getHuidigeTableCards().stream().map(GameToOpenApiConverter::convertCard).toList());

//        target.setTrickPoints(new ArrayList<>());
        target.setAllPoints(new NorthSouthNumber());

        //      target.setTrickRoem(new ArrayList<>());
        target.setAllRoem(new NorthSouthNumber());

        target.setErIsGegaan(gameEngine.getErIsGegaan());

        target.setPlayerPoints(new ArrayList<>(List.of(0, 0, 0, 0)));

        gameEngine.getGame().getSay().forEach((playerIndex, gegaan) -> {
            if (Boolean.FALSE.equals(gegaan)) {
                target.getHeeftGepast().add(playerIndex);
            }
        });

        target.setRoemGeklopt(source.getRoemGeklopt().stream().toList());

        if (gameEngine.getErIsGegaan()) {

            for (int trickNr = 0; trickNr < gameEngine.calcTricksPlayed(); trickNr++) {

                final int trickWinner = gameEngine.determineTrickWinningPlayer(trickNr);

                target.getTrickWinnerPlayer().add(trickNr, trickWinner);

                target.getTrickWinnerCard().add(trickNr, GameToOpenApiConverter.convertCard(gameEngine.determineTrickWinningCard(gameEngine.getTrickCards(trickNr))));

                final NorthSouthNumber northSouthNumber = new NorthSouthNumber();
                final NorthSouthNumber roemNorthSouthNumber = new NorthSouthNumber();

                final int points = gameEngine.calculateTrickPoints(trickNr);

                target.getPlayerPoints().set(trickWinner, target.getPlayerPoints().get(trickWinner) + points);

                final int roemPoints = gameEngine.calculateTrickRoem(trickNr);

                if (trickWinner % 2 == 0) {
                    northSouthNumber.setNorthSouth(points);
                    northSouthNumber.setEastWest(0);

                    roemNorthSouthNumber.setNorthSouth(roemPoints);
                    roemNorthSouthNumber.setEastWest(0);

                    target.getAllPoints().setNorthSouth(target.getAllPoints().getNorthSouth() + points);
                    target.getAllRoem().setNorthSouth(target.getAllRoem().getNorthSouth() + roemPoints);

                } else {
                    northSouthNumber.setNorthSouth(0);
                    northSouthNumber.setEastWest(points);


                    roemNorthSouthNumber.setNorthSouth(0);
                    roemNorthSouthNumber.setEastWest(roemPoints);

                    target.getAllPoints().setEastWest(target.getAllPoints().getEastWest() + points);
                    target.getAllRoem().setEastWest(target.getAllRoem().getEastWest() + roemPoints);
                }

                target.getTrickPoints().add(northSouthNumber);
                target.getTrickRoem().add(roemNorthSouthNumber);

            }

            final int elder = source.getSay().entrySet().stream().filter(integerBooleanEntry -> integerBooleanEntry.getValue().equals(true)).map(Map.Entry::getKey).findFirst().get();

            target.setElder(Optional.of(elder));
            target.setElderTeam(Optional.of(elder % 2 == 0 ? Teams.NOTH_SOUTH : Teams.EAST_WEST));

            if (gameEngine.isCompleted()) {

                if (target.getAllPoints()
                    .getNorthSouth() + target.getAllRoem()
                    .getNorthSouth() == target.getAllPoints()
                    .getEastWest() + target.getAllRoem()
                    .getEastWest()) {
                    target.setWinner(Optional.of(elder == 1 || elder == 3 ? Teams.NOTH_SOUTH : Teams.EAST_WEST));
                } else {
                    target.setWinner(Optional.of(target.getAllPoints()
                        .getNorthSouth() + target.getAllRoem()
                        .getNorthSouth() > target.getAllPoints()
                        .getEastWest() + target.getAllRoem()
                        .getEastWest() ? Teams.NOTH_SOUTH : Teams.EAST_WEST));
                }

            }
        }

        target.setBoomId(Optional.ofNullable(source.getBoomId()));


        final List<List<Integer>> verzakenResult = new ArrayList<>();

        IntStream.range(0, gameEngine.calcTricksPlayed()).forEach(vtrick -> {

            final ArrayList<Integer> verzakers = new ArrayList<>();

            IntStream.range(0, 3).forEach(speler -> {
                if (gameEngine.verzaakt(vtrick, speler)) {
                    verzakers.add(speler);
                }
            });

            verzakenResult.add(vtrick, verzakers);

        });

        if (!verzakenResult.isEmpty()) {
            target.setVerzaakt(verzakenResult);
        }

        return target;

    }

    public static List<Card> convertToOpenApi(final List<nl.appsource.cardserver.model.Card> source) {
        return source.stream()
            .map(GameToOpenApiConverter::convertCard)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<nl.appsource.cardserver.model.Card> convertToModel(final List<Card> source) {
        return source.stream()
            .map(GameToOpenApiConverter::convertCard)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static org.openapitools.model.Card convertCard(final nl.appsource.cardserver.model.Card source) {
        return org.openapitools.model.Card.fromValue(source.name());
    }

    public static nl.appsource.cardserver.model.Card convertCard(final org.openapitools.model.Card source) {
        return nl.appsource.cardserver.model.Card.valueOf(source.getValue());
    }


    public static org.openapitools.model.Suit convertSuit(final Suit suit) {
        return switch (suit) {
            case Clubs -> org.openapitools.model.Suit.CLUBS;
            case Hearts -> org.openapitools.model.Suit.HEARTS;
            case Spades -> org.openapitools.model.Suit.SPADES;
            case Diamonds -> org.openapitools.model.Suit.DIAMONDS;
        };
    }

    public static Suit convertSuit(final org.openapitools.model.Suit suit) {
        return switch (suit) {
            case org.openapitools.model.Suit.CLUBS -> Suit.Clubs;
            case org.openapitools.model.Suit.HEARTS -> Suit.Hearts;
            case org.openapitools.model.Suit.SPADES -> Suit.Spades;
            case org.openapitools.model.Suit.DIAMONDS -> Suit.Diamonds;
        };
    }

}
