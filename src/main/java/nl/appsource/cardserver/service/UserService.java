package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> findById(String userId);

    Optional<User> findByEmail(String email);

    List<User> findIncomingInvites(String userId);

    User save(User user);

    List<User> getUsers(List<String> userIds);

    Optional<User> removeFriend(String userId, String friendId);
}
