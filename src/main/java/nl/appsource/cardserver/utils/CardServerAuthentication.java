package nl.appsource.cardserver.utils;

import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.model.User;

public record CardServerAuthentication(User user, SseSession sseSession) {
}
