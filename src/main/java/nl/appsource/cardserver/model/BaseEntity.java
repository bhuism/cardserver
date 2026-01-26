package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.couchbase.core.index.QueryIndexed;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public abstract class BaseEntity {

    @Id
    private String id;

    @CreatedDate
    private Instant created;

    @LastModifiedDate
    private Instant updated;

    @QueryIndexed
    @CreatedBy
    private String creator;

    @Version
    private long version;

}
