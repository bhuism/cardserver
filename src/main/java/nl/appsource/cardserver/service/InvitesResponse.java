package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;


@RequiredArgsConstructor
@Getter
public final class InvitesResponse {
    private final Flux<String> incoming;
    private final Flux<String> outgoing;
    private final Flux<String> friends;

}
