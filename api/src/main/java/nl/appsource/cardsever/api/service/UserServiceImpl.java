package nl.appsource.cardsever.api.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.AiRisc;
import nl.appsource.cardserver.model.GameVariant;
import nl.appsource.cardserver.model.ScreenOrientation;
import nl.appsource.cardserver.model.Theme;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.generated.openapi.model.UpdatePreferences;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final SseEventSender sseEventSender;

    private final RedisPubSubService redisPubSubService;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public Flux<User> getUsers(final Set<String> userIds) {
        return userRepository.findAllById(userIds);
    }

    public record InvitesResponse(Flux<String> incoming, Flux<String> outgoing, Flux<String> friends) {
    }

    private Mono<User> sendUpdateUser(final User user) {
        final MyServerSentEvent updateUser = updateUser(userToOpenApiConverter.convert(user));
        return redisPubSubService.broadCast(userRepository.getFriendIds(user.getId()).mergeWith(Flux.just(user.getId())), updateUser).thenReturn(user);
    }

    @Override
    public Mono<InvitesResponse> getInvites(final String userId) {
        return userRepository.findById(userId)
            .map(user -> {
                final Flux<String> incomingFlux = userRepository.findIncomingInvites(userId).cache();
                final Flux<String> outgoingFlux = Flux.fromIterable(user.getInvites()).cache();
                final Flux<String> onlyIncoming = incomingFlux.filterWhen(s1 -> outgoingFlux.all(s2 -> !s1.equals(s2))).cache();
                final Flux<String> friends = incomingFlux.filterWhen(s1 -> onlyIncoming.all(s2 -> !s1.equals(s2))).cache();
                final Flux<String> onlyOutgoing = outgoingFlux.filterWhen(s1 -> friends.all(s2 -> !s1.equals(s2)));
                return new InvitesResponse(onlyIncoming, onlyOutgoing, friends);
            });
    }

    @Override
    public Mono<Void> removeInvite(final String userId, final String friendId) {
        return userRepository.findById(userId)
            .flatMap(user -> {
                if (user.getInvites()
                    .remove(friendId)) {
                    return Mono.just(user);
                } else {
                    return Mono.empty();
                }

            })
            .flatMap(userRepository::save)
            .flatMap(user -> sseEventSender.friendsChanged(Set.of(friendId, user.getId())))
            .then(sseEventSender.sendOnlineListTo(userId, userRepository.getOnlineFriends(userId)))
            .then(sseEventSender.sendOnlineListTo(friendId, userRepository.getOnlineFriends(friendId)));
    }

    @Override
    public Mono<Void> acceptInvite(final String userId, final String friendId) {
        return userRepository.findById(userId)
            .map(user -> {
                user.getInvites()
                    .add(friendId);
                return user;
            })
            .flatMap(userRepository::save)
            .flatMap(user -> sseEventSender.friendsChanged(Set.of(friendId, user.getId())))
            .then(sseEventSender.sendOnlineListTo(userId, userRepository.getOnlineFriends(userId)))
            .then(sseEventSender.sendOnlineListTo(friendId, userRepository.getOnlineFriends(friendId)));
    }

    @Override
    public Mono<Integer> createInvite(final String userId, final String searchString) {
        return userRepository.findById(userId)
            .flatMap(user -> userRepository.searchInvitees(searchString)
                .map(User::getId)
                .filter(inviteeId -> !user.getInvites().contains(inviteeId))
                .collect(Collectors.toSet())
                .flatMap(newFriendIds -> {
                    if (newFriendIds.isEmpty()) {
                        return Mono.just(0);
                    }
                    user.getInvites().addAll(newFriendIds);
                    return userRepository.save(user)
                        .flatMap(this::sendUpdateUser)
                        .flatMap(savedUser -> {
                            return Flux.fromIterable(newFriendIds)
//                                .flatMap(sseEventSender::sendOnlineListTo)
//                                .then(sseEventSender.sendOnlineListTo(userId))
                                .then(sseEventSender.friendsChanged(newFriendIds))
                                .then(sseEventSender.friendsChanged(singleton(userId)));
                        })
                        .thenReturn(newFriendIds.size());
                }));
    }

    @Override
    public Mono<User> updatePreferences(final String userId, final UpdatePreferences updatePreferences) {
        return userRepository.existsByDisplayNameAndIdNot(updatePreferences.getDisplayName(), userId)
            .filter(exists -> !exists)
            .switchIfEmpty(Mono.error(new Exception("Username already exists")))
            .then(userRepository.findById(userId)
                .flatMap(user -> {
                    user.setDisplayName(updatePreferences.getDisplayName());
                    user.setSkipAnimation(updatePreferences.getSkipAnimation());
                    user.setGameVariant(GameVariant.valueOf(updatePreferences.getGameVariant().name()));
                    user.setScreenOrientation(ScreenOrientation.valueOf(updatePreferences.getScreenOrientation().name()));
                    user.setTheme(Theme.valueOf(updatePreferences.getTheme().name()));
                    user.setAiRisc(AiRisc.valueOf(updatePreferences.getAiRisc().name()));
                    user.setAutoKnock(updatePreferences.getAutoKnock());
                    return userRepository.save(user).flatMap(this::sendUpdateUser);
                }));
    }


    @Override
    public Mono<Void> usersMessage(final String userId, final Set<String> recipients, final String message) {
        return sseEventSender.sendUserIdsMessage(recipients, userId, message, UserMessage.VariantEnum.INFO);
    }

}
