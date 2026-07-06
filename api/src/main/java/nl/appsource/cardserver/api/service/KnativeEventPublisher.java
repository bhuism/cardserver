//package nl.appsource.cardserver.api.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Profile;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//@Slf4j
//@Service
//@Profile({"production", "development"})
//public class KnativeEventPublisher {
//
//    private final WebClient webClient;
//    private final String brokerUrl;
//
//    public KnativeEventPublisher(final WebClient.Builder webClientBuilder, final @Value("${K_SINK:http://kourier.impl.nl}") String brokerUrl) {
//        this.webClient = webClientBuilder.build();
//        this.brokerUrl = brokerUrl;
//    }
//
//    public Mono<ResponseEntity<Void>> publish(final CloudEvent event) {
//
//        log.info("Publishing event to Knative eventing");
//
//        return webClient.post()
//            .uri(brokerUrl)
//            .bodyValue(event)
//            .retrieve()
//            .toBodilessEntity();
//    }
//}
