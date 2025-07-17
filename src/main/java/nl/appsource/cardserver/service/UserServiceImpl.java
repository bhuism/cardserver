package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final SseEmitterRepository sseEmitterRepository;

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

    @Override
    public Mono<InvitesResponse> getInvites(final String userId) {

        return userRepository.findById(userId).flatMap(user -> {

            final Flux<User> incomingFlux = userRepository.findIncomingInvites(userId).cache();
            final Flux<String> outgoingFlux = Flux.fromIterable(user.getInvites()).cache();
            final Flux<User> onlyIncoming = incomingFlux.filterWhen(s1 -> outgoingFlux.all(s2 -> !s1.getId().equals(s2))).cache();
            final Flux<User> friends = incomingFlux.filterWhen(s1 -> onlyIncoming.all(s2 -> !s1.getId().equals(s2.getId()))).cache();
            final Flux<String> onlyOutgoing = outgoingFlux.filterWhen(s1 -> friends.all(s2 -> !s1.equals(s2.getId())));
            final InvitesResponse invitesResponse = new InvitesResponse(onlyIncoming, onlyOutgoing.flatMap(userRepository::findById), friends);
            return Mono.just(invitesResponse);

        });

    }

    @Override
    public Mono<User> save(final User user) {
        return userRepository.save(user);
    }

    @Override
    public Mono<Void> removeInvite(final String userId, final String friendId) {
        return userRepository.findById(userId).flatMap(user -> {
            if (user.getInvites().remove(friendId)) {
                return Mono.just(user);
            } else {
                return Mono.empty();
            }

        }).flatMap(userRepository::save).flatMap((user) -> {
            sseEmitterRepository.friendsChanged(Set.of(friendId, user.getId()));
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> acceptInvite(final String userId, final String friendId) {
        return userRepository.findById(userId).map(user -> {
            user.getInvites().add(friendId);
            return user;
        }).flatMap(userRepository::save).flatMap((user) -> {
            sseEmitterRepository.friendsChanged(Set.of(friendId, user.getId()));
            sseEmitterRepository.sendOnlineListToFriendsOf(userId);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Integer> createInvite(final String userId, final String searchString) {
        return userRepository.findById(userId)
            .map(
                (user) -> {
                    final Set<String> newFriendIds = userRepository.searchInvitees(searchString)
                        .map(User::getId)
                        .filter(inviteeId -> !user.getInvites().contains(inviteeId))
                        .collect(Collectors.toSet())
                        .block();
                    user.getInvites().addAll(newFriendIds);
                    userRepository.save(user).subscribe((usere) -> {
                        sseEmitterRepository.friendsChanged(newFriendIds);
                    });
                    return newFriendIds.size();
                }
            );
    }

    @Override
    public Mono<User> updateName(final String userId, final String displayName) {

        return userRepository.existsByDisplayName(displayName).flatMap((aBoolean -> {
            if (aBoolean) {
                return Mono.error(new Exception("Username already exists"));
            } else {
                return Mono.just(userId);
            }
        })).flatMap(userRepository::findById).flatMap(user -> {
            user.setDisplayName(displayName);
            return userRepository.save(user);
        });
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId) {
        return sseEmitterRepository.subscribe(userId);
    }
}
