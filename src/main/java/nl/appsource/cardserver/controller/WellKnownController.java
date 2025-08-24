package nl.appsource.cardserver.controller;

import com.nimbusds.jose.jwk.JWKSet;
import nl.appsource.cardserver.service.CardServerJwtModem;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static nl.appsource.cardserver.service.CardServerJwtModemImpl.ISSUER;

@Controller
@RequestMapping("/.well-known")
public class WellKnownController {

    private final JWKSet jwkSet;

    public WellKnownController(final CardServerJwtModem cardServerJwtModem) {
        jwkSet = new JWKSet(cardServerJwtModem.getPublicKey().toPublicJWK());
    }

    @GetMapping("jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(jwkSet.toJSONObject());
    }

    @GetMapping("openid-configuration")
    public ResponseEntity<Map<String, Object>> getOpenIdConfiguration() {
        return ResponseEntity.ok(Map.of(
            "jwks_uri", "https://api.klaversjassen.nl/.well-known/jwks.json",
            "issuer", ISSUER
        ));
    }

}
