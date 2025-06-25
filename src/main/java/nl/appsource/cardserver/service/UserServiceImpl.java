package nl.appsource.cardserver.service;


import com.couchbase.client.core.deps.com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singleton;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final SseEmitterRepository sseEmitterRepository;

    @Override
    public Optional<User> findById(final String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public List<User> getUsers(final List<String> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Override
    public Optional<User> findByEmail(final String email) {
        return userRepository.findOptionalByEmail(email);
    }


    @Override
    public Optional<InvitesResponse> getInvites(final String userId) {

        final Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        final User user = userOptional.get();

        final List<User> allIncoming = userRepository.findIncomingInvites(userId);
        final List<String> allIdIncoming = allIncoming.stream().map(User::getId).toList();
        final List<User> incomingInvites = allIncoming.stream().filter(u -> !user.getInvites().contains(u.getId())).toList();

        final List<User> outgoingInvites = new ArrayList<>();
        final List<User> friends = new ArrayList<>();

        userRepository.findAllById(user.getInvites())
            .forEach(inv -> {
                if (allIdIncoming.contains(inv.getId())) {
                    friends.add(inv);
                } else {
                    outgoingInvites.add(inv);
                }
            });

        return Optional.of(new InvitesResponse(incomingInvites, outgoingInvites, friends));

    }

    @Override
    public User save(final User user) {
        return userRepository.save(user);
    }

    @Override
    public void removeInvite(final String userId, final String friendId) {
        userRepository.findById(userId)
            .map(user -> {
                user.getInvites().remove(friendId);
                return user;
            })
            .map((user) -> userRepository.save(user))
            .ifPresent((changed) -> sseEmitterRepository.friendsChanged(singleton(friendId)));
    }

    @Override
    public void acceptInvite(final String userId, final String friendId) {
        userRepository.findById(userId)
            .map(user -> {
                user.getInvites().add(friendId);
                return user;
            }).map(userRepository::save)
            .ifPresent(user -> {
                sseEmitterRepository.friendsChanged(singleton(friendId));
            });
    }

    @Override
    public Optional<List<User>> createInvite(final String userId, final String searchString) {
        return userRepository.findById(userId)
            .flatMap(user -> Optional.of(userRepository.findInvitees(searchString)
                    .stream()
                    .filter(invitee -> !user.getInvites().contains(invitee.getId()))
                    .toList())
                .map(invitees -> {
                    final List<String> inviteeIds = invitees.stream().map(User::getId).toList();
                    user.getInvites().addAll(inviteeIds);
                    userRepository.save(user);
                    sseEmitterRepository.friendsChanged(inviteeIds);
                    return invitees;
                }));
    }

    @Override
    public Optional<User> updateName(final String userId, final String displayName) {

        if (userRepository.findByDisplayName(displayName).isPresent()) {
            return Optional.empty();
        }

        return userRepository.findById(userId)
            .map(user -> {
                user.setDisplayName(displayName);
                userRepository.save(user);
                return user;
            });
    }
}
