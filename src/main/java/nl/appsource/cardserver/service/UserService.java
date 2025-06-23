package nl.appsource.cardserver.service;

import jakarta.validation.constraints.NotNull;
import nl.appsource.cardserver.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> findById(String userId);

    Optional<User> findByEmail(String email);

    Optional<InvitesResponse> getInvites(String userId);

    User save(User user);

    List<User> getUsers(List<String> userIds);

    void removeInvite(String userId, String friendId);

    void acceptInvite(String userId, String friendId);

    Optional<List<User>> createInvite(String userId, String searchString);

    Optional<User> updateName(String userId, @NotNull String displayName);
}

