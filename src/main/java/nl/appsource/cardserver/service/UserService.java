package nl.appsource.cardserver.service;

import jakarta.validation.constraints.NotNull;
import nl.appsource.cardserver.model.User;
import org.openapitools.model.UpdatePreferences;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface UserService {

    Mono<User> findById(String userId);

    Mono<User> findByEmail(String email);

    Mono<InvitesResponse> getInvites(String userId);

    Mono<User> save(User user);

    Flux<User> getUsers(List<String> userIds);

    Mono<Void> removeInvite(String userId, String friendId);

    Mono<Void> acceptInvite(String userId, String friendId);

    Mono<Integer> createInvite(String userId, String searchString);

    Mono<User> updatePreferences(String userId, @NotNull UpdatePreferences updatePreferences);

    Flux<ServerSentEvent<?>> subscribe(UUID appIdentifier, String userId, String remoteAddress);

}

