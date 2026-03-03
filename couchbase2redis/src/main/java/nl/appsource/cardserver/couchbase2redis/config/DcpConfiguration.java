package nl.appsource.cardserver.couchbase2redis.config;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.config.CardServerCouchbaseProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!citest")
public class DcpConfiguration {

    private final CardServerCouchbaseProperties cardServerCouchbaseProperties;

    private static final String HOSTNAME;

    private final GitProperties gitProperties;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

    @Bean(destroyMethod = "disconnect")
    public Client dcpClient() {

        log.info("Connecting to DCP stream");

        return Client.builder()
            .connectionString(cardServerCouchbaseProperties.getConnectionString())
            .bucket(cardServerCouchbaseProperties.getBucketName())
            .credentials(cardServerCouchbaseProperties.getUsername(), cardServerCouchbaseProperties.getPassword())
            .userAgent("cardserver", gitProperties.getShortCommitId(), HOSTNAME)
            .build();
    }

    @Bean
    public Flux<ByteBuf> dcpStream(final Client dcpClient) {

        log.info("Starting DCP stream");

        final Sinks.Many<ByteBuf> sink = Sinks.many().multicast().onBackpressureBuffer();

        dcpClient.controlEventHandler((flowController, event) -> {
            flowController.ack(event);
            event.release();
        });

        dcpClient.dataEventHandler((flowController, event) -> {
            flowController.ack(event);
            sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1500)));
        });

        dcpClient.connect().block();
        dcpClient.initializeState(StreamFrom.NOW, StreamTo.INFINITY).block();
        dcpClient.startStreaming().block();

        return sink.asFlux();
    }

}
