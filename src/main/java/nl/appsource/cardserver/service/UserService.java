package nl.appsource.cardserver.service;

import org.openapitools.model.User;

import java.util.Optional;

public interface UserService {

    Optional<User> findById(String userId);

}
