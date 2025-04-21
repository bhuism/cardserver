package nl.appsource.cardserver.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.DeckCard;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@Service
@Slf4j
@AllArgsConstructor
public class Migrator {

    public static final String TIME = "__time__";
    public static final String COLLECTIONS = "__collections__";

    private final UserRepository userRepository;

    private final GameRepository gameRepository;

    private final ObjectMapper objectMapper;

    @PostConstruct
    @SuppressWarnings("AvoidNestedBlocks")
    public void init() throws IOException {
        log.info("PostConstruct");
        userRepository.deleteAll();
        loadUser("users.json");
        gameRepository.deleteAll();
        loadGames("games.json");
    }

    private void loadGames(final String fileName) throws IOException {

        log.info("Found {} games in db", gameRepository.count());

        final File gameFile = new File(fileName);

        if (gameFile.exists()) {

            final JsonNode jsonNode = objectMapper.readTree(gameFile);

            final JsonNode data = jsonNode.get("data");

            data.fields().forEachRemaining(gameNode -> {

                final String id = gameNode.getKey();

                log.info("Game {} {}found", id, gameRepository.existsById(id) ? "" : " not");

                final Game game = new Game();

                game.setId(id);
                final JsonNode gameNodeValue = gameNode.getValue();

                gameNodeValue.fieldNames().forEachRemaining(fieldName -> {

                    final JsonNode fieldValue = gameNodeValue.get(fieldName);

                    //log.info(" found field: {} , type: {}", fieldName, field.getNodeType());

                    switch (fieldName) {

                        case "created":
                            final String created = fieldValue.get(TIME).textValue();
                            game.setCreated(Instant.parse(created));
                            break;
                        case "updated":
                            final String updated = fieldValue.get(TIME).textValue();
                            game.setUpdated(Instant.parse(updated));
                            break;
                        case "creator":
                            game.setCreator(fieldValue.textValue());
                            break;

                        case "dealer":
                            game.setDealer(fieldValue.intValue());
                            break;
                        case "trump":
                            game.setTrump(fieldValue.intValue());
                            break;

                        case "elder":
                            game.setElder(fieldValue.intValue());
                            break;

                        case "ended":
                            game.setEnded(fieldValue.asBoolean());
                            break;

                        case "playerCard":
                            final Map<DeckCard, Integer> cards = new HashMap<>();
                            fieldValue.forEach(card -> {
                                final DeckCard playerCard = cardConvert(card.get("card").textValue());
                                final Integer player = card.get("player").intValue();
                                cards.put(playerCard, player);
                            });
                            if (cards.size() != 32) {
                                throw new RuntimeException("Invalid card count: " + cards.size());
                            }
                            game.setPlayerCard(cards);
                            break;

                        case "players":
                            final Set<String> players = StreamSupport.stream(fieldValue.spliterator(), false).map(JsonNode::textValue).collect(Collectors.toSet());
                            game.setPlayers(players);
                            break;

                        case "turns":
                            final LinkedHashSet<DeckCard> turns = StreamSupport
                                .stream(Spliterators.spliteratorUnknownSize(
                                    fieldValue.iterator(),
                                    Spliterator.ORDERED), false)
                                .map(JsonNode::textValue)
                                .map(Migrator::cardConvert)
                                .collect(Collectors.toCollection(LinkedHashSet::new));

                            game.setTurns(turns);
                            break;

                        case "uid":
                        case COLLECTIONS:
                        case "choices":
                            break;
                        default:
                            throw new RuntimeException("Unknown field: " + fieldName);

                    }
                });

                log.info("Persisting game: {}", game);

                gameRepository.save(game);

            });
        }
    }

    private static DeckCard cardConvert(final String stringValue) {
        return DeckCard.valueOf(stringValue.replace('9', 'N').replace('8', 'E').replace('7', 'S'));
    }

    private void loadUser(final String fileName) throws IOException {

        log.info("Found {} users in db", userRepository.count());

        final File userFile = new File(fileName);

        if (userFile.exists()) {

            final JsonNode jsonNode = objectMapper.readTree(userFile);

            final JsonNode data = jsonNode.get("data");

            data.fields().forEachRemaining(userNode -> {

                final String id = userNode.getKey();

                log.info("User {} {}found", id, userRepository.existsById(id) ? "" : " not");

                final User user = new User();

                user.setId(id);

                final JsonNode userNodeValue = userNode.getValue();

                userNodeValue.fieldNames().forEachRemaining(fieldName -> {

                    final JsonNode fieldValue = userNodeValue.get(fieldName);

                    //log.info(" found field: {} , type: {}", fieldName, field.getNodeType());

                    switch (fieldName) {
                        case "created":
                            final String created = fieldValue.get(TIME).textValue();
                            user.setCreated(Instant.parse(created));
                            break;
                        case "updated":
                            final String updated = fieldValue.get(TIME).textValue();
                            user.setUpdated(Instant.parse(updated));
                            break;
                        case "lastLogin":
                            final String lastLogin = fieldValue.get(TIME).textValue();
                            user.setLastLogin(Instant.parse(lastLogin));
                            break;
                        case "displayName":
                            user.setDisplayName(fieldValue.textValue());
                            break;
                        case "email":
                            user.setEmail(fieldValue.textValue());
                            break;
                        case "invites":
                            final Set<String> invites = StreamSupport.stream(Spliterators.spliteratorUnknownSize(fieldValue.iterator(), Spliterator.ORDERED), false).map(JsonNode::textValue).collect(Collectors.toSet());
                            user.setInvites(invites);
                            break;
                        case "name":
                            user.setName(fieldValue.textValue());
                            break;
                        case "photoURL":
                            user.setPhotoURL(fieldValue.textValue());
                            break;
                        case "providerId":
                            user.setProviderId(fieldValue.textValue());
                            break;
                        case "uid":
                        case "fcmToken":
                        case COLLECTIONS:
                        case "phoneNumber":
                            break;
                        default:
                            throw new RuntimeException("Unknown field: " + fieldName);

                    }

                });

                log.info("Persisting user: {}", user);

                userRepository.save(user);


            });

        }
    }

}
