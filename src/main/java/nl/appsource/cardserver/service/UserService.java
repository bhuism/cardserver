package nl.appsource.cardserver.service;

import jakarta.validation.constraints.NotNull;
import nl.appsource.cardserver.model.User;
import org.openapitools.model.UpdatePreferences;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface UserService {

    Mono<User> findById(String userId);

    Mono<User> findByEmail(String email);

    Mono<UserServiceImpl.InvitesResponse> getInvites(String userId);

    Flux<User> getUsers(List<String> userIds);

    Mono<Void> removeInvite(String userId, String friendId);

    Mono<Void> acceptInvite(String userId, String friendId);

    Mono<Integer> createInvite(String userId, String searchString);

    Mono<User> updatePreferences(String userId, @NotNull UpdatePreferences updatePreferences);

    Mono<Void> usersMessage(String userId, List<String> recipients, String message);

    Mono<String> updateUpdated(String userId);

}

