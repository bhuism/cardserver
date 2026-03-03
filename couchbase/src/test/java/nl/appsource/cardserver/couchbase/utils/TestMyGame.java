package nl.appsource.cardserver.couchbase.utils;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMyGame {

    private Game mockGame(Suit trump, List<Card> turns) {
        final Game game = new Game();
        game.setId("test-game");
        game.setPlayers(List.of("p1", "p2", "p3", "p4"));
        game.setTrump(trump);
        game.setDealer(0);
        game.setTurns(turns);
        return game;
    }

    @Test
    public void shouldReturn20ForA3CardSequenceRoem() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.Kc, Card.Qc, Card.Tc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(20);
    }

    @Test
    public void shouldReturn50ForA4CardSequenceRoem() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.Kc, Card.Qc, Card.Jc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(50);
    }

    @Test
    public void shouldReturn0ForA4CardNonSequenceRoem() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.Kc, Card.Qc, Card.Jc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(50);
    }

    @Test
    public void shouldReturn0ForA3CardNonSequenceRoem() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.Kc, Card.Tc, Card.Sc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(0);
    }

    @Test
    public void shouldReturn0ForA3CardNonSequenceRoemB() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.Kc, Card.Jd, Card.Tc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(0);
    }

    @Test
    public void shouldReturn100ForFourOfaKindAces() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Ac, Card.As, Card.Ad, Card.Ah));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(100);
    }

    @Test
    public void shouldReturn200ForFourOfaKindJacks() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Jc, Card.Js, Card.Jd, Card.Jh));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(200);
    }

    @Test
    public void shouldReturn20ForStuk() {
        final Game game = mockGame(Suit.Clubs, List.of(Card.Kc, Card.Qc, Card.Ad, Card.Ah));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(20);
    }

    @Test
    public void shouldReturn0ForStukNonTrump() {
        final Game game = mockGame(Suit.Spades, List.of(Card.Kc, Card.Qc, Card.Ad, Card.Ah));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(0);
    }

    @Test
    public void shouldReturn70Fora4cardsquenceSutk() {
        final Game game = mockGame(Suit.Clubs, List.of(Card.Ac, Card.Kc, Card.Qc, Card.Jc));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(70);
    }

    @Test
    public void shouldReturn40Fora3cardsquenceStuk() {
        final Game game = mockGame(Suit.Clubs, List.of(Card.Kc, Card.Qc, Card.Jc, Card.Ad));
        final GameEngineImpl gameEngine = new GameEngineImpl(game);
        assertThat(gameEngine.calculateTrickRoem(0)).isEqualTo(40);
    }


}
