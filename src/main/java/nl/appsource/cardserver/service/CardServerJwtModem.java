package nl.appsource.cardserver.service;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;

public interface CardServerJwtModem {
    Jwt decode(String token);

    SignedJWT encode(String userId);
}
