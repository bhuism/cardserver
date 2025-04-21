package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Game {

    @Id
    private String id;

    @NotNull
    private Instant created;

    private Instant updated;

    @NotNull
    private String creator;

    @NotNull
    private Integer dealer;

    private Integer elder;

    private Integer trump;

    private Map<DeckCard, Integer> playerCard;

    private Boolean ended;

    private Set<String> players;

    private TreeSet<DeckCard> turns;

}
