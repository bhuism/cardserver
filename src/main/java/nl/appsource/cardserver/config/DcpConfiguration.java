package nl.appsource.cardserver.config;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.message.DcpDeletionMessage;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!citest")
public class DcpConfiguration {

    private final CardServerCouchbaseProperties cardServerCouchbaseProperties;

    @Bean
    public Client dcpClient() {
        final Client client = Client.builder()
            .connectionString(cardServerCouchbaseProperties.getConnectionString())
            .bucket(cardServerCouchbaseProperties.getBucketName())
            .credentials(cardServerCouchbaseProperties.getUsername(), cardServerCouchbaseProperties.getPassword())
            .build();

        client.controlEventHandler((flowController, event) -> {
            event.release();
        });

        client.dataEventHandler((flowController, event) -> {
            if (DcpMutationMessage.is(event)) {
                log.info("getContentAsString: " + MessageUtil.getContentAsString(event.asByteBuf()));
                final String key = MessageUtil.getCollectionIdAndKey(event, false).key();
                final String content = DcpMutationMessage.content(event).toString(StandardCharsets.UTF_8);
                log.info("Mutation: key={}, content={}", key, content);
            } else if (DcpDeletionMessage.is(event)) {
                final String key = MessageUtil.getCollectionIdAndKey(event, false).key();
                log.info("Deletion: key={}", key);
            }
            event.release();
        });

        client.connect().block();

        client.initializeState(StreamFrom.NOW, StreamTo.INFINITY).block();

        client.startStreaming().block();

        return client;
    }

    @Bean
    public DisposableBean dcpClientDisposer(final Client client) {
        return () -> client.disconnect().block();
    }

}
