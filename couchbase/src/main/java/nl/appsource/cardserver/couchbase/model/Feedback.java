package nl.appsource.cardserver.couchbase.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;

@Document
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Feedback extends BaseEntity {

    @Field
    @NotEmpty
    private String text;

}
