package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.openapitools.model.GameVariant;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Game extends BaseEntity {

    @NotNull
    @Field
    private Integer dealer;

    @Field
    @NotNull
    private Map<Integer, Boolean> say = new HashMap<>();

    @NotNull
    @Field
    private Suit trump;

    @Field
    @NotNull
    private Map<Card, Integer> playerCard = new HashMap<>();

    @Field
    @NotNull
    private List<String> players = new ArrayList<>();

    @Field
    @NotNull
    private List<Card> turns = new ArrayList<>();

    @Field
    @NotNull
    private Boolean lastTrickOpen = false;

    @Field
    @NotNull
    private GameVariant gameVariant;

    @Field
    @NotNull
    private Integer dealCounter = 0;

    @Field
    @NotNull
    private Set<Integer> roemGeklopt = new HashSet<>();

    @Field
    private String boomId;

}
