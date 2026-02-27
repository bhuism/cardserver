package nl.appsource.cardserver.service;

import nl.appsource.cardserver.couchbase.model.Card;
import nl.appsource.cardserver.couchbase.model.Game;
import nl.appsource.cardserver.couchbase.model.GameVariant;
import nl.appsource.cardserver.couchbase.model.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineImplTest {

    private Game game;
    private GameEngineImpl gameEngine;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId("test-game");
        game.setPlayers(List.of("p0", "p1", "p2", "p3"));
        game.setTrump(Suit.Hearts);
        game.setGameVariant(GameVariant.AMSTERDAMS);
        game.setPlayerCard(new HashMap<>());
        game.setTurns(new ArrayList<>());
        gameEngine = new GameEngineImpl(game);
    }

    private void setHand(int player, List<Card> cards) {
        for (Card card : cards) {
            game.getPlayerCard().put(card, player);
        }
    }

    private void playCard(int player, Card card) {
        game.getTurns().add(card);
    }

    @Test
    void testVerzaakt_FollowSuit_Success() {
        // Player 0 leads with CLUBS ACE
        // Player 1 has CLUBS TEN and plays it.
        // Should not be verzaakt.

        Card lead = Card.Ac;
        Card follow = Card.Tc;

        setHand(0, List.of(lead));
        setHand(1, List.of(follow, Card.Sh)); // Has trump too

        playCard(0, lead);
        playCard(1, follow);

        assertFalse(gameEngine.verzaakt(0, 1));
    }

    @Test
    void testVerzaakt_FollowSuit_Fail() {
        // Player 0 leads with CLUBS ACE
        // Player 1 has CLUBS TEN but plays HEARTS SEVEN (trump).
        // Should be verzaakt (must follow suit).

        Card lead = Card.Ac;
        Card follow = Card.Tc;
        Card played = Card.Sh;

        setHand(0, List.of(lead));
        setHand(1, List.of(follow, played));

        playCard(0, lead);
        playCard(1, played);

        assertTrue(gameEngine.verzaakt(0, 1));
    }

    @Test
    void testVerzaakt_MustTrump_Amsterdam_OpponentWinning() {
        // Amsterdam: Must trump if cannot follow suit and opponent is winning.
        // P0 (Opponent of P1) leads CLUBS ACE.
        // P1 has no CLUBS, but has HEARTS SEVEN (trump).
        // P1 plays DIAMONDS SEVEN.
        // Should be verzaakt.

        game.setGameVariant(GameVariant.AMSTERDAMS);

        Card lead = Card.Ac;
        Card trump = Card.Sh;
        Card discard = Card.Sd;

        setHand(0, List.of(lead));
        setHand(1, List.of(trump, discard));

        playCard(0, lead);
        playCard(1, discard);

        assertTrue(gameEngine.verzaakt(0, 1));
    }

    @Test
    void testVerzaakt_NoTrump_Amsterdam_PartnerWinning() {
        // Amsterdam: No need to trump if partner is winning.
        // P0 leads CLUBS 7.
        // P1 plays CLUBS ACE (Winning).
        // P2 plays CLUBS 8.
        // P3 has no CLUBS, has HEARTS 7.
        // P3 plays DIAMONDS 7.
        // P1 is winning. P3 is partner.
        // Should NOT be verzaakt.

        game.setGameVariant(GameVariant.AMSTERDAMS);

        Card c0 = Card.Sc;
        Card c1 = Card.Ac;
        Card c2 = Card.Ec;

        Card trump = Card.Sh;
        Card discard = Card.Sd;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1));
        setHand(2, List.of(c2));
        setHand(3, List.of(trump, discard));

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, c2);
        playCard(3, discard);

        assertFalse(gameEngine.verzaakt(0, 3));
    }

    @Test
    void testVerzaakt_MustTrump_Rotterdam_PartnerWinning() {
        // Rotterdam: Must trump even if partner is winning.
        // Same scenario as above.
        // P0 leads CLUBS 7.
        // P1 plays CLUBS ACE (Winning).
        // P2 plays CLUBS 8.
        // P3 has no CLUBS, has HEARTS 7.
        // P3 plays DIAMONDS 7.
        // Should be verzaakt.

        game.setGameVariant(GameVariant.ROTTERDAMS);

        Card c0 = Card.Sc;
        Card c1 = Card.Ac;
        Card c2 = Card.Ec;

        Card trump = Card.Sh;
        Card discard = Card.Sd;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1));
        setHand(2, List.of(c2));
        setHand(3, List.of(trump, discard));

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, c2);
        playCard(3, discard);

        assertTrue(gameEngine.verzaakt(0, 3));
    }

    @Test
    void testVerzaakt_OverTrump_Required() {
        // P0 leads CLUBS ACE.
        // P1 trumps with HEARTS 9.
        // P2 (Partner of P0) has HEARTS JACK (higher trump) and HEARTS 7 (lower trump).
        // P2 plays HEARTS 7.
        // Should be verzaakt (must overtrump if possible).

        Card c0 = Card.Ac;
        Card c1 = Card.Nh;

        Card highTrump = Card.Jh;
        Card lowTrump = Card.Sh;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1)); // P1 has no clubs
        setHand(2, List.of(highTrump, lowTrump)); // P2 has no clubs

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, lowTrump);

        assertTrue(gameEngine.verzaakt(0, 2));
    }

    @Test
    void testVerzaakt_OverTrump_NotPossible_UnderTrumpAllowed() {
        // P0 leads CLUBS ACE.
        // P1 trumps with HEARTS JACK.
        // P2 has HEARTS 9 (lower).
        // P2 plays HEARTS 9.
        // Should NOT be verzaakt (cannot overtrump, so undertrump is allowed/forced).

        Card c0 = Card.Ac;
        Card c1 = Card.Jh;
        Card c2 = Card.Nh;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1));
        setHand(2, List.of(c2));

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, c2);

        assertFalse(gameEngine.verzaakt(0, 2));
    }

    @Test
    void testVerzaakt_LeaderCannotRenege() {
        Card c0 = Card.Ac;
        setHand(0, List.of(c0));
        playCard(0, c0);
        assertFalse(gameEngine.verzaakt(0, 0));
    }

    @Test
    void testVerzaakt_OverTrumpPartner_Amsterdam_NotRequired() {
        // Amsterdam: Not required to overtrump partner.
        // P0 leads CLUBS ACE.
        // P1 (Partner of P3) trumps with HEARTS 9.
        // P2 plays CLUBS 7.
        // P3 (Partner of P1) has HEARTS JACK (higher) and HEARTS 7 (lower).
        // P3 plays HEARTS 7.
        // Should NOT be verzaakt.

        game.setGameVariant(GameVariant.AMSTERDAMS);

        Card c0 = Card.Ac;
        Card c1 = Card.Nh;
        Card c2 = Card.Sc;

        Card highTrump = Card.Jh;
        Card lowTrump = Card.Sh;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1));
        setHand(2, List.of(c2));
        setHand(3, List.of(highTrump, lowTrump));

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, c2);
        playCard(3, lowTrump);

        assertFalse(gameEngine.verzaakt(0, 3));
    }

    @Test
    void testVerzaakt_OverTrumpPartner_Rotterdam_Required() {
        // Rotterdam: Required to overtrump partner.
        // Same scenario as above.
        // P0 leads CLUBS ACE.
        // P1 (Partner of P3) trumps with HEARTS 9.
        // P2 plays CLUBS 7.
        // P3 (Partner of P1) has HEARTS JACK (higher) and HEARTS 7 (lower).
        // P3 plays HEARTS 7.
        // Should be verzaakt.

        game.setGameVariant(GameVariant.ROTTERDAMS);

        Card c0 = Card.Ac;
        Card c1 = Card.Nh;
        Card c2 = Card.Sc;

        Card highTrump = Card.Jh;
        Card lowTrump = Card.Sh;

        setHand(0, List.of(c0));
        setHand(1, List.of(c1));
        setHand(2, List.of(c2));
        setHand(3, List.of(highTrump, lowTrump));

        playCard(0, c0);
        playCard(1, c1);
        playCard(2, c2);
        playCard(3, lowTrump);

        assertTrue(gameEngine.verzaakt(0, 3));
    }
}
