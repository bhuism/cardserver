package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.CardNr;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import org.openapitools.model.Game;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {
    private final GameRepository gameRepository;

    @Override
    public Optional<Game> findById(final String gameId) {
//        return gameRepository.findOne(QGame.game.id.eq(gameId)).map(GameServiceImpl::convert);
        return gameRepository.findById(gameId).map(GameServiceImpl::convert);
    }

//    @Override
//    public Set<String> findAll() {
//        return gameRepository.findAll(Pageable.ofSize(10)).stream().map(nl.appsource.cardserver.model.Game::getId).collect(toSet());
//    }

    @Override
    public Set<String> findByCreator(final String creator) {

        return gameRepository.findIdByEmail(creator);
//        return gameRepository.findAll(QGame.game.creator.eq(creator)).stream().map(nl.appsource.cardserver.model.Game::getId).collect(toSet());
    }

    public static Game convert(final nl.appsource.cardserver.model.Game source) {

        final Game target = new Game();

        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setId(source.getId());
        target.setCreator(source.getCreator());
        target.setDealer(source.getDealer());
        target.setElder(Optional.ofNullable(source.getElder()));
        target.setEnded(source.getEnded());
        target.setPlayers(source.getPlayers());
        target.setTrump(convert(source.getTrump()));
        target.setTurns(convert(source.getTurns()));

        return target;
    }


    public static List<org.openapitools.model.Card> convert(final LinkedHashSet<Card> source) {
        return source.stream().map(GameServiceImpl::convert).toList();
    }

    public static org.openapitools.model.Card convert(final Card source) {
        final org.openapitools.model.Card result = new org.openapitools.model.Card();
        result.setColor(convert(source.getSuit()));
        result.setCard(convert(source.getCardNr()));
        return result;
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

}
