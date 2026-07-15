package nl.appsource.cardserver.stream.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class CouchbaseEvent {

    private String event;
    private String bucket;
    private String key;
    private Map<String, Object> content;
    private Long cas;
    private Integer expiry;

    @JsonProperty("vbucket")
    private Integer vBucket;

    @JsonProperty("vbucket_uuid")
    private Long vBucketUuid;

    private Long seqno;

}
