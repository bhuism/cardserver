package nl.appsource.cardserver.gameengine;

import lombok.extern.slf4j.Slf4j;
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
public record GameEngineRwImpl(String userId, Game game, GameEngine gameEngine, UserMessenger userMessenger) implements GameEngineRw {

    public interface UserMessenger {
        Mono<Void> sendUserMessage(String message);

        Mono<Void> sendGameMessage(String message);
    }

    public GameEngineRwImpl(final String userId, final Game game, final UserMessenger userMessenger) {
        this(userId, game, new GameEngineImpl(game), userMessenger);
    }

    @Override
    public Mono<Game> playCard(final Card card) {

        final int playerNum = this.game.getPlayers()
            .indexOf(userId);

        if (gameEngine.isCompleted()) {
            return userMessenger.sendUserMessage("Game allready completed").then(Mono.empty());
        }

        if (game.getLastTrickOpen()) {
            return userMessenger.sendUserMessage("Open trick, close trick first").then(Mono.empty());
        }

        final int cardOwner = gameEngine.whoHasCard(card);

        if (playerNum != cardOwner) {
            return userMessenger.sendUserMessage("Player does not have card " + card).then(Mono.empty());
        }

        if (game.getTurns()
            .stream()
            .anyMatch((c) -> c == card)) {
            return userMessenger.sendUserMessage("Card " + card + " allready played").then(Mono.empty());
        }

        final int gotTurn = gameEngine.calcWhoHasTurn();

        if (gotTurn != playerNum) {
            return userMessenger.sendUserMessage("It's player " + game.getPlayers().get(gotTurn) + " turn").then(Mono.empty());
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        //game.setUpdated(Instant.now());
        game.getTurns().add(card);
        game.setLastTrickOpen(false);

        return Mono.just(game);

    }

    @Override
    public Mono<Game> say(final Boolean say) {

        if (gameEngine.isCompleted()) {
            return userMessenger.sendUserMessage("Game allready completed").then(Mono.empty());
        }

        if (game.getLastTrickOpen()) {
            return userMessenger.sendUserMessage("Open trick, close trick first").then(Mono.empty());
        }

        if (game.getSay() == null) {
            game.setSay(new HashMap<>());
        }

        if (gameEngine.isErGegaan()) {
            return userMessenger.sendUserMessage("Er is al iemand gegaan").then(Mono.empty());
        }

        final int playerNum = this.game.getPlayers().indexOf(userId);

        if (game.getSay()
            .containsKey(playerNum)) {
            return userMessenger.sendUserMessage("Je hebt al gezegd").then(Mono.empty());
        }

        final int whoSay = gameEngine.calcWhoSay();

        if (whoSay != playerNum) {
            return userMessenger.sendUserMessage("It's player " + game.getPlayers().get(whoSay) + " turn").then(Mono.empty());
        }

        game.getSay().put(playerNum, say);

        return rotateTrump().then(Mono.just(game));

    }

    public Mono<Game> rotateTrump() {
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

            return Mono.just(game);
        } else {
            return Mono.empty();
        }

    }

    @Override
    public Mono<Game> openLastTrick() {
        if (!gameEngine.isCompleted() && gameEngine.getTurnCount() > 4 && gameEngine.getTurnCount() % 4 != 0) {
            if (!this.gameEngine.getGame().getLastTrickOpen()) {
                this.gameEngine.getGame().setLastTrickOpen(true);
                return Mono.just(game);
            }
        }
        return Mono.empty();
    }

    @Override
    public Mono<Game> closeLastTrick() {
        if (this.gameEngine.getGame().getLastTrickOpen()) {
            this.gameEngine.getGame().setLastTrickOpen(false);
            return Mono.just(game);
        } else {
            return Mono.empty();
        }
    }

    @Override
    public Mono<Game> claimRoem() {
        final int slagNr = gameEngine.calcTricksPlayed();

        final int correctedSlagNr = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

        final int roem = gameEngine.calculateTrickRoem(correctedSlagNr);
        if (roem > 0) {

            final boolean result = gameEngine.getGame()
                .getRoemGeklopt()
                .add(gameEngine.calcTricksPlayed());

            log.warn("Roem geklopt: {} in slag {} result: {}", roem, correctedSlagNr + 1, result);

            final String message = "Er is " + (result ? "" : "al ") + roem + " roem geklopt in slag " + (correctedSlagNr + 1);

            return userMessenger.sendGameMessage(message).then(result ? Mono.just(game) : Mono.empty());

        } else {
            final String message = "Er is geen roem in slag " + (correctedSlagNr + 1);

            return userMessenger.sendGameMessage(message).then(Mono.empty());

        }

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
