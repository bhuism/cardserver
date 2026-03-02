package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class PubSubService {

    private final ReactiveRedisMessageListenerContainer container;

    private Disposable topicSubscription;

    // Start listening to the topic
    public void listenTo(final String topicName) {
        // Prevent multiple active subscriptions if called multiple times
        if (topicSubscription != null && !topicSubscription.isDisposed()) {
            return;
        }

        Flux<Message<String, String>> messageFlux = container.receive(ChannelTopic.of(topicName));

        this.topicSubscription = messageFlux
            .doOnNext(message -> {
                String channel = message.getChannel();
                String body = message.getMessage();
                // Process the incoming message
                System.out.println("Received: " + body + " from " + channel);
            })
            .doOnError(error -> {
                // Handle connection drops or serialization errors
                System.err.println("Error in stream: " + error.getMessage());
            })
            .subscribe();
    }

    // Stop listening to the topic
    public void stopListening() {
        if (topicSubscription != null && !topicSubscription.isDisposed()) {
            // Disposing the subscription triggers the UNSUBSCRIBE command in Redis
            topicSubscription.dispose();
            System.out.println("Unsubscribed from topic.");
        }
    }
}
