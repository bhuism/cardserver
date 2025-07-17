package nl.appsource.cardserver.model.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true, fluent = true)
public class NewGameEvent {

    @JsonProperty
    private String gameId;

    @JsonProperty
    private String displayNameCreator;

}
