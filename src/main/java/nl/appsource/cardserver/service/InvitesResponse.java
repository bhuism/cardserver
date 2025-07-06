package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;
import reactor.core.publisher.Flux;


@RequiredArgsConstructor
@Getter
public final class InvitesResponse {
    private final Flux<User> incoming;
    private final Flux<User> outgoing;
    private final Flux<User> friends;

}
