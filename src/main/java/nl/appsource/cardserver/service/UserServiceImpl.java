package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

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


        final List<User> incomingInvites = userRepository.findIncomingInvites(userId).stream().filter(u -> !user.getInvites().contains(u.getId())).toList();
        final List<String> incomingIdInvites = incomingInvites.stream().map(User::getId).toList();
        final List<User> outgoingInvites = new ArrayList<>();
        final List<User> friends = new ArrayList<>();


        userRepository.findAllById(user.getInvites())
            .forEach(inv -> {
                if (incomingIdInvites.contains(inv.getId())) {
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
    public Optional<User> removeInvite(final String userId, final String friendId) {
        return userRepository.findById(userId)
            .map(user -> {
                user.getInvites().remove(friendId);
                return user;
            }).map(userRepository::save);
    }

    @Override
    public Optional<User> acceptInvite(final String userId, final String friendId) {
        return userRepository.findById(userId)
            .map(user -> {
                user.getInvites().add(friendId);
                return user;
            }).map(userRepository::save);
    }

    @Override
    public Optional<User> createInvite(final String userId, final String searchString) {
        return userRepository.findById(userId)
            .flatMap(user -> userRepository.findOptionalBySearchString(searchString)
                .map(friend -> {
                    if (!user.getInvites().contains(friend.getId())) {
                        user.getInvites().add(friend.getId());
                        userRepository.save(user);
                        return friend;
                    } else {
                        return null;
                    }
                }));
    }
}
