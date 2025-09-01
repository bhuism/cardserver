package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class User {

    @Id
    private String id;

    @Field
    @NotNull
    private Instant created;

    @Field
    @NotNull
    private Instant updated;

    @Field
    @NotEmpty
    private String displayName;

    @Field
    @NotEmpty
    private String email;

    private List<String> invites;

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
    private Boolean skipAnimation;

}
