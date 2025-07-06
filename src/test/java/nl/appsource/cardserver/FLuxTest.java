package nl.appsource.cardserver;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
public class FLuxTest {

    @Test
    void givenFlux_whenMultipleSubscribers_thenEachReceivesData() {
        List<Integer> incoming = List.of(1, 2, 3, 4, 5).cache();
        List<Integer> outgoing = List.of(4, 5, 6, 7, 8);

        Flux<Integer> incomingFlux = Flux.fromIterable(incoming);
        Flux<Integer> outgoingFlux = Flux.fromIterable(outgoing);

//        incomingFlux.subscribe(integer -> {
//            log.info(("subscribe incoming " + integer));
//        });
//
//        outgoingFlux.subscribe(integer -> {
//            log.info(("subscribe outgoing " + integer));
//        });

        Flux<Integer> onlyIncoming = incomingFlux.filterWhen(s1 -> outgoingFlux.all(s2 -> !s1.equals(s2)));
        Flux<Integer> friends = incomingFlux.filterWhen(s1 -> onlyIncoming.all(s2 -> !s1.equals(s2)));
        Flux<Integer> onlyOutgoing = outgoingFlux.filterWhen(s1 -> friends.all(s2 -> !s1.equals(s2)));

        onlyIncoming.subscribe(integer -> {
            log.info("onlyIncoming flux: " + integer);
        });

        friends.subscribe(integer -> {
            log.info("friends flux: " + integer);
        });

        onlyOutgoing.subscribe(integer -> {
            log.info("onlyOutgoing flux: " + integer);
        });

    }

}
