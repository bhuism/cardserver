package nl.appsource.cardserver.service;

import reactor.core.publisher.Flux;


public record InvitesResponse(Flux<String> incoming, Flux<String> outgoing, Flux<String> friends) {
}
