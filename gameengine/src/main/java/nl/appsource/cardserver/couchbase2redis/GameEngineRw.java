package nl.appsource.cardserver.couchbase2redis;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.couchbase.exception.ElderException;
import nl.appsource.cardserver.couchbase.exception.GameCompletedException;
import nl.appsource.cardserver.couchbase.exception.LastTrickOpenException;
import nl.appsource.cardserver.couchbase.exception.NotAPlayerException;
import nl.appsource.cardserver.couchbase.exception.NotPlayersTurnException;
import nl.appsource.cardserver.couchbase.utils.GameEngine;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static java.util.Collections.shuffle;

@Slf4j
public record GameEngineRw(Game game, GameEngine gameEngine) {

    public GameEngineRw(final Game game) {
        this(game, new GameEngineImpl(game));
    }

//    public Mono<GameEngineRw> playAiCard() {
//
//        if (!gameEngine.isAiTurn()) {
//            return Mono.empty();
//        }
//
//        final String userId = game.getPlayers()
//            .get(gameEngine.calcWhoHasTurn());
//
//        if (!AI_USER_ID.contains(userId)) {
//            throw new GameEngineException("Not an Ai player");
//        }
//
//        return playCard(userId, new AiPlayer(this.gameEngine).calcAiCard(userId));
//
//    }
//
//    public Mono<GameEngineRw> sayAi() {
//
//        if (!gameEngine.isAiSay()) {
//            return Mono.empty();
//        }
//
//        final int whoSay = gameEngine.calcWhoSay();
//
//        final String userId = this.game.getPlayers()
//            .get(whoSay);
//
//        return say(userId, new AiPlayer(this.gameEngine).decideBid(userId));
//
//    }

    public Mono<GameEngineRw> playCard(final String userId, final Card card) {

        final int playerNum = this.game.getPlayers()
            .indexOf(userId);

        if (gameEngine.isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers()
            .contains(userId)) {
            throw new RuntimeException("Not a player");
        }

        final int cardOwner = gameEngine.whoHasCard(card);

        if (playerNum != cardOwner) {
            log.warn("Player does not have card {}", card);
        }

        if (game.getTurns()
            .stream()
            .anyMatch((c) -> c == card)) {
            log.warn("Card {} allready played", card);
            throw new CardAlreadyPlayerException(card);
        }

        if (game.getLastTrickOpen()) {
            return Mono.empty();
        }

        final int gotTurn = gameEngine.calcWhoHasTurn();

        if (gotTurn != playerNum) {
            log.warn("playCard({}) It's player {} turn", card, game.getPlayers()
                .get(gotTurn));
            throw new NotPlayersTurnException();
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        //game.setUpdated(Instant.now());
        game.getTurns().add(card);
        game.setLastTrickOpen(false);

        return Mono.just(new GameEngineRw(game));

    }

    public Mono<GameEngineRw> say(final String userId, final Boolean say) {

//        final List<UserMessage> userMessages = new ArrayList<>();

        final int playerNum = this.game.getPlayers()
            .indexOf(userId);

        if (gameEngine.isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers()
            .contains(userId)) {
            throw new NotAPlayerException();
        }

        if (game.getSay() == null) {
            game.setSay(new HashMap<>());
        }

        if (gameEngine.isErGegaan()) {
            throw new ElderException("Er is al iemand gegaan");
        }

        if (game.getSay()
            .containsKey(playerNum)) {
            throw new ElderException("Je hebt al gezegd");
        }

        if (game.getLastTrickOpen()) {
            throw new LastTrickOpenException();
        }

        final int whoSay = gameEngine.calcWhoSay();

        if (whoSay != playerNum) {
            log.warn("say() It's player {} turn to say", game.getPlayers()
                .get(whoSay));
            throw new NotPlayersTurnException();
        }

        game.getSay()
            .put(playerNum, say);

        // userMessages.add(new UserMessage().message(userId + " " + (say ? "gaat!" : "past")).variant(say ? UserMessage.VariantEnum.SUCCESS : UserMessage.VariantEnum.INFO));

        return checkNiemandIsGegaanEnIedereenHeeftGezegd()
            .then(Mono.just(new GameEngineRw(game)));

        //game.setUpdated(Instant.now());

//        return userMessages;

        //return Mono.just(new GameEngineRw(game));

    }

    public Mono<GameEngineRw> checkNiemandIsGegaanEnIedereenHeeftGezegd() {
        if (gameEngine.niemandIsGegaanEnIedereenHeeftGezegd()) {
            if (game.getDealCounter() % 2 == 0) {

                final Suit oldTrump = this.game.getTrump();

                do {
                    game.setTrump(Suit.values()[ThreadLocalRandom.current().nextInt(Suit.values().length)]);
                }
                while (oldTrump == game.getTrump());
            } else {
                game.setTrump(Suit.values()[ThreadLocalRandom.current().nextInt(Suit.values().length)]);
                game.setPlayerCard(randomCards());
            }

            game.getSay()
                .clear();

            game.setDealCounter(game.getDealCounter() + 1);
            //game.setUpdated(Instant.now());

            return Mono.just(new GameEngineRw(game));
        } else {
            return Mono.empty();
        }

    }

    public Mono<GameEngineRw> openLastTrick() {
        if (!gameEngine.isCompleted() && gameEngine.getTurnCount() > 4 && gameEngine.getTurnCount() % 4 != 0) {
            if (!this.gameEngine.getGame().getLastTrickOpen()) {
                this.gameEngine.getGame().setLastTrickOpen(true);
                return Mono.just(new GameEngineRw(game));
            }
        }
        return Mono.empty();
    }

    public Mono<GameEngineRw> closeLastTrick() {
        if (this.gameEngine.getGame().getLastTrickOpen()) {
            this.gameEngine.getGame().setLastTrickOpen(false);
            return Mono.just(new GameEngineRw(game));
        } else {
            return Mono.empty();
        }
    }

    public Mono<GameEngineRw> claimRoem(final String userId) {
        final int slagNr = gameEngine.calcTricksPlayed();

        final int correctedSlagNr = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

        final int roem = gameEngine.calculateTrickRoem(correctedSlagNr);
        if (roem > 0) {
            final boolean result = gameEngine.getGame()
                .getRoemGeklopt()
                .add(gameEngine.calcTricksPlayed());

            log.warn("Roem geklopt: {} in slag {} result: {}", roem, correctedSlagNr + 1, result);

//            return sseEventSender.sendUserIdsMessage(Set.copyOf(getGame().getPlayers()), "Er is " + (result ? "" : "al ") + roem + " roem geklopt in slag " + (correctedSlagNr + 1),
//                    UserMessage.VariantEnum.INFO)
//                .then(Mono.just(new GameEngineRw(game)));

        }
//        else {
//            return sseEventSender.sendUserIdsMessage(Set.copyOf(getGame().getPlayers()), "Er is geen roem in slag " + (correctedSlagNr + 1),
//                    UserMessage.VariantEnum.WARNING)
//                .then(Mono.just(new GameEngineRw(game)));
//        }

        return Mono.just(new GameEngineRw(game));
    }

    private static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        shuffle(deck, ThreadLocalRandom.current());
        IntStream.range(0, deck.size())
            .forEach(index -> cards.put(deck.get(index), index % 4));
        return cards;
    }


}
