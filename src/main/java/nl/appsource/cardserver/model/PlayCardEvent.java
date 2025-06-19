package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PlayCardEvent {

    private final String userId;

    private final String gameId;

    private final org.openapitools.model.Card card;
}
