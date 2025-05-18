package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.index.CompositeQueryIndex;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@CompositeQueryIndex(fields = {"id", "creator"})
public class Game {

    @Id
    private String id;

    @Field
    @NotNull
    private Instant created;

    @Field
    private Instant updated;

    @NotNull
    @Field
    @QueryIndexed
    private String creator;

    @NotNull
    @Field
    private Integer dealer;

    @Field
    private Integer elder;

    @NotNull
    @Field
    private Suit trump;

    @Field
    @NotNull
    private Map<Card, Integer> playerCard;

    @Field
    @NotNull
    private Boolean ended;

    @Field
    @NotNull
    private Set<String> players;

    @Field
    private LinkedHashSet<Card> turns;

}
