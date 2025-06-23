package nl.appsource.cardserver.model.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayCardEvent {

    @JsonProperty
    private String userId;

    @JsonProperty
    private String gameId;

    @JsonProperty
    private org.openapitools.model.Card card;
}
