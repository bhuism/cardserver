package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Boom extends BaseEntity {

    @Field
    @NotNull
    private GameVariant gameVariant;

    @NotNull
    @Field
    private Integer dealer;

    @Field
    @NotNull
    @QueryIndexed
    private List<String> players = new ArrayList<>();

    @Field
    @NotNull
    private List<String> games = new ArrayList<>();

    @NotNull
    @Field
    private AiRisc aiRisc = AiRisc.MEDIUM;

}
