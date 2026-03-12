package nl.appsource.cardsever.api;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

@Slf4j
public class FluxTest {

    @Test
    void givenFlux_whenMultipleSubscribers_thenEachReceivesData() {
        List<Integer> incoming = List.of(1, 2, 3, 4, 5);
        List<Integer> outgoing = List.of(4, 5, 6, 7, 8);

        Flux<Integer> incomingFlux = Flux.fromIterable(incoming).doOnSubscribe(subscription -> {
            log.info("Subscription onlyIncoming");
        }).cache();

        Flux<Integer> outgoingFlux = Flux.fromIterable(outgoing).doOnSubscribe(subscription -> {
            log.info("Subscription outgoingFlux");
        }).cache();

        Flux<Integer> onlyIncoming = incomingFlux.filterWhen(s1 -> outgoingFlux.all(s2 -> !s1.equals(s2))).doOnSubscribe(subscription -> {
            log.info("Subscription filtered onlyIncoming");
        }).cache();

        Flux<Integer> friends = incomingFlux.filterWhen(s1 -> onlyIncoming.all(s2 -> !s1.equals(s2))).doOnSubscribe(subscription -> {
            log.info("Subscription filtered friends");
        }).cache();

        Flux<Integer> onlyOutgoing = outgoingFlux.filterWhen(s1 -> friends.all(s2 -> !s1.equals(s2))).doOnSubscribe(subscription -> {
            log.info("Subscription filtered onlyOutgoing");
        });

        StepVerifier.create(onlyIncoming)
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .expectComplete()
            .verify();

        StepVerifier.create(friends)
            .expectNext(4)
            .expectNext(5)
            .expectComplete()
            .verify();

        StepVerifier.create(onlyOutgoing)
            .expectNext(6)
            .expectNext(7)
            .expectNext(8)
            .expectComplete()
            .verify();

    }

}
