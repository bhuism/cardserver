//package nl.appsource.cardserver.migrator;
//
//import jakarta.annotation.PostConstruct;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import nl.appsource.cardserver.model.Card;
//import nl.appsource.cardserver.model.Game;
//import nl.appsource.cardserver.model.Suit;
//import nl.appsource.cardserver.model.User;
//import nl.appsource.cardserver.repository.GameRepository;
//import nl.appsource.cardserver.repository.UserRepository;
//import nl.appsource.generated.openapi.model.AiRisc;
//import nl.appsource.generated.openapi.model.GameVariant;
//import nl.appsource.generated.openapi.model.ScreenOrientation;
//import nl.appsource.generated.openapi.model.Theme;
//import org.springframework.context.annotation.Profile;
//import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Mono;
//import tools.jackson.databind.JsonNode;
//import tools.jackson.databind.json.JsonMapper;
//
//import java.io.File;
//import java.io.IOException;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Spliterator;
//import java.util.Spliterators;
//import java.util.stream.StreamSupport;
//
//
//@Service
//@Slf4j
//@AllArgsConstructor
//@Profile("never")
//public class Migrator {
//
//    public static final String TIME = "__time__";
//    public static final String COLLECTIONS = "__collections__";
//
//    private final UserRepository userRepository;
//
//    private final GameRepository gameRepository;
//
//    private final JsonMapper jsonMapper;
//
//    private final ReactiveCouchbaseTemplate reactiveCouchbaseTemplate;
//
//    @PostConstruct
//    public synchronized void run() {
//        migrate();
//        //loadUser("users.json");
//        //loadGames("games.json");
//    }
//
//    private void migrate() {
//
//        reactiveCouchbaseTemplate.findByQuery(Game.class)
//            .all()
//            .flatMap(game -> {
//                boolean changed = false;
//                if (game.getLastTrickOpen() == null) {
//                    game.setLastTrickOpen(false);
//                    changed = true;
//                }
//
//                if (game.getGameVariant() == null) {
//                    game.setGameVariant(GameVariant.HAAGS);
//                    changed = true;
//                }
//
//                if (game.getDealCounter() == null) {
//                    game.setDealCounter(0);
//                    changed = true;
//                }
//
//                if (game.getSay() == null) {
//                    game.setSay(new HashMap<>());
//                    changed = true;
//                }
//
//                if (game.getTurns() == null) {
//                    game.setTurns(new ArrayList<>());
//                    changed = true;
//                }
//
//                if (game.getRoemGeklopt() == null) {
//                    game.setRoemGeklopt(new HashSet<>());
//                    changed = true;
//                }
//
//                if (changed) {
//                    log.info("Migrated game {}", game.getId());
//                    return gameRepository.save(game);
//                } else {
//                    return Mono.empty();
//                }
//            })
//            .subscribe();
//
//        reactiveCouchbaseTemplate.findByQuery(User.class)
//            .all()
//            .flatMap(user -> {
//
//                boolean changed = false;
//
//                if (user.getUpdated() == null) {
//                    user.setUpdated(user.getCreated());
//                    changed = true;
//                }
//
//                if (user.getSkipAnimation() == null) {
//                    user.setSkipAnimation(false);
//                    changed = true;
//                }
//
//                if (user.getGameVariant() == null) {
//                    user.setGameVariant(GameVariant.ROTTERDAMS);
//                    changed = true;
//                }
//
//                if (user.getScreenOrientation() == null) {
//                    user.setScreenOrientation(ScreenOrientation.AUTO);
//                    changed = true;
//                }
//
//                if (user.getTheme() == null) {
//                    user.setTheme(Theme.AUTO);
//                    changed = true;
//                }
//
//                if (user.getCreator() == null) {
//                    user.setCreator(user.getId());
//                    changed = true;
//                }
//
//                if (user.getAiRisc() == null) {
//                    user.setAiRisc(AiRisc.MEDIUM);
//                    changed = true;
//                }
//
//                if (changed) {
//                    log.info("Migrated user {}", user.getId());
//                    return userRepository.save(user);
//                } else {
//                    return Mono.empty();
//                }
//
//            })
//            .subscribe();
//    }
//
//    private void loadGames(final String fileName) throws IOException {
//
//        log.info("Found {} games in db", gameRepository.count()
//            .block());
//
//        final File gameFile = new File(fileName);
//
//        if (gameFile.exists()) {
//
//            final JsonNode jsonNode = jsonMapper.readTree(gameFile);
//
//            final JsonNode data = jsonNode.get("data");
//
//            data.properties().iterator()
//                .forEachRemaining(gameNode -> {
//
//                    final String id = gameNode.getKey();
//
//                    log.info("Game {} {} found", id, gameRepository.existsById(id)
//                        .block() ? "" : " not");
//
//                    final Game game = new Game();
//
//                    game.setId(id);
//
//                    // set optionals
////                game.setElder(Optional.empty());
////                game.setUpdated(Optional.empty());
//
//                    final JsonNode gameNodeValue = gameNode.getValue();
//
//                    gameNodeValue.propertyNames()
//                        .forEach(fieldName -> {
//
//                            final JsonNode fieldValue = gameNodeValue.get(fieldName);
//
//                            //log.info(" found field: {} , type: {}", fieldName, field.getNodeType());
//
//                            switch (fieldName) {
//
//                                case "created":
//                                    final String created = fieldValue.get(TIME)
//                                        .textValue();
//                                    game.setCreated(Instant.parse(created));
//                                    break;
//                                case "updated":
//                                    game.setUpdated(Optional.ofNullable(fieldValue.get(TIME))
//                                        .map(JsonNode::textValue)
//                                        .map(Instant::parse)
//                                        .orElse(null));
//                                    break;
//                                case "creator":
//                                    game.setCreator(fieldValue.textValue());
//                                    break;
//                                case "dealer":
//                                    game.setDealer(fieldValue.intValue());
//                                    break;
//                                case "trump":
//                                    game.setTrump(convertSuit(fieldValue.intValue()));
//                                    break;
//                                case "elder":
//                                    final Map<Integer, Boolean> say = new HashMap<>();
//                                    if (fieldValue.isInt()) {
//                                        final int elder = fieldValue.intValue();
//                                        say.put(elder, true);
//                                    }
//                                    game.setSay(say);
//                                    break;
//                                case "ended":
//                                    break;
//                                case "playerCard":
//                                    final Map<Card, Integer> cards = new HashMap<>();
//                                    fieldValue.forEach(card -> {
//                                        final Card playerCard = cardConvert(card.get("card")
//                                            .textValue());
//                                        final Integer player = card.get("player")
//                                            .intValue();
//                                        cards.put(playerCard, player);
//                                    });
//                                    if (cards.size() != 32) {
//                                        throw new RuntimeException("Invalid card count: " + cards.size());
//                                    }
//                                    game.setPlayerCard(cards);
//                                    break;
//                                case "players":
//                                    final List<String> players = new ArrayList<>();
//                                    fieldValue.forEach(field -> {
//                                        players.add(field.textValue());
//                                    });
//                                    game.setPlayers(players);
//                                    break;
//                                case "turns":
//                                    final List<Card> turns = new ArrayList<>();
//                                    fieldValue.forEach(field -> {
//                                        turns.add(cardConvert(field.textValue()));
//                                    });
//                                    game.setTurns(turns);
//                                    break;
//                                case "uid":
//                                case COLLECTIONS:
//                                    break;
//                                case "choices":
//                                    break;
//                                default:
//                                    throw new RuntimeException("Unknown field: " + fieldName);
//                            }
//                        });
//
//                    log.info("Persisting game: {}", game.getId());
//
//                    if (gameRepository.existsById(game.getId()).block()) {
//                        gameRepository.deleteById(game.getId()).block();
//                    }
//
//                    gameRepository.save(game).subscribe();
//
//                });
//        }
//    }
//
//    private static final Suit[] SUITCONVERTER = {Suit.Clubs, Suit.Hearts, Suit.Spades, Suit.Diamonds};
//
//    private Suit convertSuit(final int i) {
//        return SUITCONVERTER[i];
//    }
//
//    private static Card cardConvert(final String stringValue) {
//        return Card.valueOf(stringValue.replace('9', 'N')
//            .replace('8', 'E')
//            .replace('7', 'S'));
//    }
//
//    private void loadUser(final String fileName) throws IOException {
//
//        log.info("Found {} users in db", userRepository.count().block());
//
//        final File userFile = new File(fileName);
//
//        if (userFile.exists()) {
//
//            // userRepository.deleteAll();
//
//            final JsonNode jsonNode = jsonMapper.readTree(userFile);
//
//            final JsonNode data = jsonNode.get("data");
//
//            data.properties().iterator()
//                .forEachRemaining(userNode -> {
//
//                    final String id = userNode.getKey();
//
////                log.info("User {} {}found", id, userRepository.existsById(id) ? "" : " not");
//
//                    final User user = new User();
//
//                    user.setId(id);
//
//                    final JsonNode userNodeValue = userNode.getValue();
//
//                    userNodeValue.propertyNames()
//                        .forEach(fieldName -> {
//
//                            final JsonNode fieldValue = userNodeValue.get(fieldName);
//
//                            //log.info(" found field: {} , type: {}", fieldName, field.getNodeType());
//
//                            switch (fieldName) {
//                                case "created":
//                                    final String created = fieldValue.get(TIME)
//                                        .textValue();
//                                    user.setCreated(Instant.parse(created));
//                                    break;
//                                case "updated":
//                                    final String updated = fieldValue.get(TIME)
//                                        .textValue();
//                                    user.setUpdated(Instant.parse(updated));
//                                    break;
//                                case "lastLogin":
//                                    final String lastLogin = fieldValue.get(TIME)
//                                        .textValue();
//                                    user.setLastLogin(Optional.ofNullable(lastLogin)
//                                        .map(Instant::parse)
//                                        .orElse(null));
//                                    break;
//                                case "displayName":
//                                    user.setDisplayName(fieldValue.textValue());
//                                    break;
//                                case "email":
//                                    user.setEmail(fieldValue.textValue());
//                                    break;
//                                case "invites":
//                                    final List<String> invites = StreamSupport.stream(Spliterators.spliteratorUnknownSize(fieldValue.iterator(), Spliterator.ORDERED), false)
//                                        .map(JsonNode::textValue)
//                                        .toList();
//                                    user.setInvites(invites);
//                                    break;
//                                case "name":
//                                    user.setName(fieldValue.textValue());
//                                    break;
//                                case "photoURL":
//                                    user.setPhotoURL(fieldValue.textValue());
//                                    break;
//                                case "providerId":
//                                    user.setProviderId(fieldValue.textValue());
//                                    break;
//                                case "uid":
//                                case "fcmToken":
//                                case COLLECTIONS:
//                                case "phoneNumber":
//                                    break;
//                                default:
//                                    throw new RuntimeException("Unknown field: " + fieldName);
//
//                            }
//
//                        });
//
//                    log.info("Persisting user: {}", user);
//
//                    userRepository.save(user)
//                        .subscribe();
//
//
//                });
//
//        }
//    }
//
//}
