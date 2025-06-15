package nl.appsource.cardserver.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import nl.appsource.cardserver.config.CardServerProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardServerJwtModem {

    private final CardServerProperties cardServerProperties;

    @SneakyThrows
    public String decode(final String jwt) {

        final JWSObject jwsObject = JWSObject.parse(jwt);
        final JWSVerifier verifier = new MACVerifier(getHash());

        if (!jwsObject.verify(verifier)) {
            throw new IllegalArgumentException("JWT verification failed");
        }

        return jwsObject.getPayload().toString();
    }

    @SneakyThrows
    public String encode(final String userId) {

        final JWSSigner signer = new MACSigner(getHash());
        final JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(Map.of("sub", userId, "alg", "HS256")));

        jwsObject.sign(signer);

        return jwsObject.serialize();

    }

    @SneakyThrows
    private byte[] getHash() {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] encodedhash = digest.digest(cardServerProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return encodedhash;
    }
}
