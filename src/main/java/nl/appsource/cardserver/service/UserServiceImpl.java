package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.UpdatePreferences;
import org.openapitools.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final SseEmitterRepository sseEmitterRepository;

    private final SseEventSender sseEventSender;

    @Override
    public Mono<User> findById(final String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Flux<User> getUsers(final List<String> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Override
    public Mono<User> findByEmail(final String email) {
        return userRepository.findByEmail(email);
    }

    public record InvitesResponse(Flux<String> incoming, Flux<String> outgoing, Flux<String> friends) {
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
            .then(sseEmitterRepository.sendOnlineListTo(userId))
            .then(sseEmitterRepository.sendOnlineListTo(friendId));
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
            .then(sseEmitterRepository.sendOnlineListTo(userId))
            .then(sseEmitterRepository.sendOnlineListTo(friendId));
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
                        .flatMap(savedUser -> {
                            return Flux.fromIterable(newFriendIds)
                                .flatMap(sseEmitterRepository::sendOnlineListTo)
                                .then(sseEmitterRepository.sendOnlineListTo(userId))
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
                    user.setGameVariant(updatePreferences.getGameVariant());
                    user.setScreenOrientation(updatePreferences.getScreenOrientation());
                    user.setTheme(updatePreferences.getTheme());
                    return userRepository.save(user);
                }));
    }


    @Override
    public Mono<Void> usersMessage(final String userId, final List<String> recipients, final String message) {
        return sseEventSender.sendUserIdMessage(recipients, new UserMessage().userId(userId)
            .message(message)
            .variant(UserMessage.VariantEnum.INFO));
    }

}
