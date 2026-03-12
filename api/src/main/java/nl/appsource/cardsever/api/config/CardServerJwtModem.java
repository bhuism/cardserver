package nl.appsource.cardsever.api.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CardServerJwtModem extends ReactiveJwtDecoder {

    List<JWK> getJKSKets();

    Mono<Jwt> decode(String token);

    SignedJWT encode(String userId) throws JOSEException;


}
