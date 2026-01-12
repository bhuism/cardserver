package nl.appsource.cardserver;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class InstantConversionTest {

    @Getter
    @Setter
    public static class TestModel {
        private Instant test;
    }

    @Test
    void instantConversion() {

        JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .build();

        final JsonNode node = mapper.readTree("{ \"test\": \"1768248978881\" }");
        final TestModel testModel = mapper.treeToValue(node, TestModel.class);

        assertThat(testModel.getTest()).isEqualTo(Instant.ofEpochMilli(1768248978881L));
    }

}
