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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class User {

    @Id
    private String id;

    @NotNull
    private Instant created;

    private Instant updated;

    @NotEmpty
    private String displayName;

    @NotEmpty
    private String email;

    private List<String> invites;

    private Optional<Instant> lastLogin;

    @NotEmpty
    private String name;

    private String photoURL;

    @NotEmpty
    private String providerId;

}
