package nl.appsource.cardserver.service;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

public interface CardServerJwtModem extends ReactiveJwtDecoder {

    Mono<Jwt> decode(String token);

    SignedJWT encode(String userId);

}
