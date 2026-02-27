package nl.appsource.cardserver.controller;

import com.nimbusds.jose.jwk.JWKSet;
import nl.appsource.cardserver.config.CardServerJwtModem;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static nl.appsource.cardserver.config.CardServerJwtModemImpl.ISSUER;

@CrossOrigin
@Controller
@RequestMapping("/.well-known")
public class WellKnownController {

    private final JWKSet jwkSet;

    public WellKnownController(final CardServerJwtModem cardServerJwtModem) {
        jwkSet = new JWKSet(List.of(cardServerJwtModem.getPublicKey().toPublicJWK(), cardServerJwtModem.getPublicKeyEs512().toPublicJWK()));
    }

    @GetMapping("jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .body(jwkSet.toJSONObject());
    }

    @GetMapping("openid-configuration")
    public ResponseEntity<Map<String, Object>> getOpenIdConfiguration() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .body(Map.of(
                "jwks_uri", "https://api.klaversjassen.nl/.well-known/jwks.json",
                "issuer", ISSUER
            ));
    }

}
