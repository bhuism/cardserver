package nl.appsource.cardserver.gameengine.service;

import nl.appsource.generated.openapi.model.GameEvent;

public interface Worker {
    void scheduleGameEvent(GameEvent gameEvent);
}
