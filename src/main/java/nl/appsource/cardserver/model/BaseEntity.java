package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.index.CompositeQueryIndex;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Field;

import java.time.Instant;

@CompositeQueryIndex(fields = {"id", "creator"})
@Getter
@Setter
public abstract class BaseEntity {

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


}
