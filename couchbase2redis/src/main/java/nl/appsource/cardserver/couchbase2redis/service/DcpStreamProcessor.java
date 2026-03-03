package nl.appsource.cardserver.couchbase2redis.service;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBufInputStream;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.MessageUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;

import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateBoom;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateGame;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateUser;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!citest")
public class DcpStreamProcessor {

    private final Flux<ByteBuf> dcpStream;

    private final JsonMapper jsonMapper;

    private final RedisPublisher redisPublisher;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final UserRepository userRepository;

    @PostConstruct
    public void init() {
        dcpStream
            .filter(DcpMutationMessage::is)
            .flatMap(event -> {
                final String id = MessageUtil.getKeyAsString(event);
                try (InputStream is = new ByteBufInputStream(MessageUtil.getContent(event))) {
                    final JsonNode rootNode = jsonMapper.readTree(is);
                    if (rootNode.isObject()) {
                        ((ObjectNode) rootNode).put("id", id);
                        final String className = rootNode.get("_class").asString();

                        switch (className) {
                            case "nl.appsource.cardserver.model.Boom" -> {
                                final Boom boom = jsonMapper.treeToValue(rootNode, Boom.class);
                                boom.setId(id);
                                return redisPublisher.publish(Flux.fromIterable(boom.getPlayers())
                                    .mergeWith(Flux.just(boom.getCreator()))
                                    .distinct(), updateBoom(boomToOpenApiConverter.convert(boom))).then();
                            }
                            case "nl.appsource.cardserver.model.User" -> {
                                final User user = jsonMapper.treeToValue(rootNode, User.class);
                                user.setId(id);
                                final MyServerSentEvent updateUser = updateUser(userToOpenApiConverter.convert(user));
                                return redisPublisher.publish(user.getId(), updateUser)
                                    .thenMany(userRepository.getFriendIds(user.getId())
                                        .flatMap(friendId -> redisPublisher.publish(friendId, updateUser)))
                                    .then();
                            }
                            case "nl.appsource.cardserver.model.Game" -> {
                                final Game game = jsonMapper.treeToValue(rootNode, Game.class);
                                game.setId(id);
                                final MyServerSentEvent gameEvent = updateGame(gameToOpenApiConverter.convert(game));
                                return redisPublisher.publish(Flux.fromIterable(game.getPlayers())
                                    .mergeWith(Flux.just(game.getCreator()))
                                    .distinct(), gameEvent).then();
                            }
                            default -> {
                                return Flux.empty();
                            }
                        }
                    }
                } catch (IOException e) {
                    log.warn("Can not deserialize key: " + id);
                } finally {
                    event.release();
                }
                return Flux.empty();
            })
            .subscribe();
    }
}
