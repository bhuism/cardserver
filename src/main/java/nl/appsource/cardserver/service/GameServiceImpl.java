package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.CardNr;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import org.openapitools.model.Game;
import org.openapitools.model.GamePlayerCardInner;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public Game createGame(final Game game) {

        final nl.appsource.cardserver.model.Game convertedGame = convert(game);

        convertedGame.setId(idGen());

        final nl.appsource.cardserver.model.Game savedGame = gameRepository.save(convertedGame);

        return convert(savedGame);
    }

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
        }).collect(Collectors.toSet()));
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


    public static Set<org.openapitools.model.Card> convertToOpenApi(final LinkedHashSet<Card> source) {
        return source.stream().map(GameServiceImpl::convert).collect(Collectors.toSet());
    }

    public static org.openapitools.model.Card convert(final Card source) {
        final org.openapitools.model.Card result = new org.openapitools.model.Card();
        result.setColor(convert(source.getSuit()));
        result.setCard(convert(source.getCardNr()));
        return result;
    }

    public static LinkedHashSet<Card> convertToModel(final Set<org.openapitools.model.Card> source) {
        return source.stream().map(GameServiceImpl::convert).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Card convert(final org.openapitools.model.Card source) {

        return switch (source.getColor()) {
            case HEARTS -> {
                yield switch (source.getCard()) {
                    case ACE -> Card.Ah;
                    case KING -> Card.Kh;
                    case QUEEN -> Card.Qh;
                    case JACK -> Card.Jh;
                    case TEN -> Card.Th;
                    case NINE -> Card.Nh;
                    case EIGHT -> Card.Eh;
                    case SEVEN -> Card.Sh;
                };
            }
            case CLUBS -> {
                yield switch (source.getCard()) {
                    case ACE -> Card.Ac;
                    case KING -> Card.Kc;
                    case QUEEN -> Card.Qc;
                    case JACK -> Card.Jc;
                    case TEN -> Card.Tc;
                    case NINE -> Card.Nc;
                    case EIGHT -> Card.Ec;
                    case SEVEN -> Card.Sc;
                };
            }
            case SPADES -> {
                yield switch (source.getCard()) {
                    case ACE -> Card.As;
                    case KING -> Card.Ks;
                    case QUEEN -> Card.Qs;
                    case JACK -> Card.Js;
                    case TEN -> Card.Ts;
                    case NINE -> Card.Ns;
                    case EIGHT -> Card.Es;
                    case SEVEN -> Card.Ss;
                };
            }
            case DIAMONDS -> {
                yield switch (source.getCard()) {
                    case ACE -> Card.Ad;
                    case KING -> Card.Kd;
                    case QUEEN -> Card.Qd;
                    case JACK -> Card.Jd;
                    case TEN -> Card.Td;
                    case NINE -> Card.Nd;
                    case EIGHT -> Card.Ed;
                    case SEVEN -> Card.Sd;
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

    public static Suit convert(final org.openapitools.model.Suit trump) {
        return Optional.ofNullable(trump).map(SUITCONVERTER_REVERSE::get).orElse(null);
    }

    private static final Random RANDOM = new SecureRandom();

    public static String idGen() {
        final int length = 20;
        final String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final StringBuilder result = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(characters.length());
            result.append(characters.charAt(index));
        }

        return result.toString();
    }

}
