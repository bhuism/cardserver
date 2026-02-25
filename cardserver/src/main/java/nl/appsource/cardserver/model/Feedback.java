package nl.appsource.cardserver.model;

import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @Field
    private String text;

}
