package nl.appsource.cardserver.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@Service
@Slf4j
@AllArgsConstructor
public class UserMigrator {

    public static final String TIME = "__time__";
    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    @PostConstruct
    @SuppressWarnings("AvoidNestedBlocks")
    public void init() throws IOException {
        log.info("PostConstruct");

        log.info("Found {} users", userRepository.count());

        final File userFile = new File("users.json");

        final JsonNode jsonNode = objectMapper.readTree(userFile);

        final JsonNode data = jsonNode.get("data");

        data.fields().forEachRemaining(userNode -> {

            final String id = userNode.getKey();

            //log.info("Found user id: {}", id);


            /* if (!userRepository.existsById(id)) */
            {

                //log.info("not found");

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
                        case "__collections__":
                        case "phoneNumber":
                            break;
                        default:
                            throw new RuntimeException("Unknown field: " + fieldName);

                    }

                });

                log.info("Found user: {}", user);

                userRepository.save(user);


            }


        });


    }

}
