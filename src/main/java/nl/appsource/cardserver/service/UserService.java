package nl.appsource.cardserver.service;

import org.openapitools.model.User;

import java.util.Optional;
import java.util.Set;

public interface UserService {

    Optional<User> findById(String userId);

    Optional<User> findByEmail(String email);

    Set<User> findAllIncomingInvites(User user);

}
