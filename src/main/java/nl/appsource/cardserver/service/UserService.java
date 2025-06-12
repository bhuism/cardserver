package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> findById(String userId);

    Optional<User> findByEmail(String email);

    List<User> findAllIncomingInvites(String userId);

}
