package nl.appsource.cardserver.model.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openapitools.model.Game;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GameStateEvent {

//    @JsonProperty
//    private String userId;

    @JsonProperty
    private Game gameState;
}
