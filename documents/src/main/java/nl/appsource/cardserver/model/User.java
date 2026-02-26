package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class User extends BaseEntity {

    @Field
    @NotEmpty
    private String displayName;

    @Field
    @NotEmpty
    @QueryIndexed
    private String email;

    @QueryIndexed
    private List<String> invites = new ArrayList<>();

    private Instant lastLogin;

    @Field
    @NotEmpty
    private String name;

    @Field
    private String photoURL;

    @NotEmpty
    @Field
    private String providerId;

    @NotNull
    @Field
    private Boolean skipAnimation = false;

    @NotNull
    @Field
    private GameVariant gameVariant = GameVariant.ROTTERDAMS;

    @NotNull
    @Field
    private ScreenOrientation screenOrientation = ScreenOrientation.AUTO;

    @NotNull
    @Field
    private Theme theme = Theme.DARK;

    @NotNull
    @Field
    private AiRisc aiRisc = AiRisc.MEDIUM;

    @NotNull
    @Field
    private Boolean autoKnock = Boolean.FALSE;

}
