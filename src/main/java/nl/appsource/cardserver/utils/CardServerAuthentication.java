package nl.appsource.cardserver.utils;

import nl.appsource.cardserver.model.User;

import java.util.Optional;

public record CardServerAuthentication(User user, Optional<String> appIdentifier) {
}
