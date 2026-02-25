package nl.appsource.cardserver.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

public interface CardServerJwtModem extends ReactiveJwtDecoder {

    OctetKeyPair getPublicKey();

    Mono<Jwt> decode(String token);

    SignedJWT encode(String userId) throws JOSEException;


}
