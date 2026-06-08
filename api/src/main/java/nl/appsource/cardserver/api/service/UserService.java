package nl.appsource.cardserver.api.service;

import jakarta.validation.constraints.NotNull;
import nl.appsource.cardserver.model.User;
import nl.appsource.generated.openapi.model.UpdatePreferences;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface UserService {

    Mono<UserServiceImpl.InvitesResponse> getInvites(String userId);

    Flux<User> getUsers(Set<String> userIds);

    Mono<Void> removeInvite(String userId, String friendId);

    Mono<Void> acceptInvite(String userId, String friendId);

    Mono<Integer> createInvite(String userId, String searchString);

    Mono<User> updatePreferences(String userId, @NotNull UpdatePreferences updatePreferences);

    Mono<Void> usersMessage(String userId, Set<String> recipients, String message);

}

