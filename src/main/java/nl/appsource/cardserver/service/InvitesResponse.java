package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;

import java.util.List;

@RequiredArgsConstructor
@Getter
public final class InvitesResponse {
    private final List<User> incoming;
    private final List<User> outgoing;
    private final List<User> friends;

}
