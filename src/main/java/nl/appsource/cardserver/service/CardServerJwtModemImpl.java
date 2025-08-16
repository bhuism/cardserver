package nl.appsource.cardserver.service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServerJwtModemImpl implements CardServerJwtModem {

    private final CardServerProperties cardServerProperties;

    private JWSVerifier verifier;

    private JWSSigner signer;

    @SneakyThrows
    @PostConstruct
    public void init() {
        verifier = new MACVerifier(getHash());
        signer = new MACSigner(getHash());
    }

    @SneakyThrows
    @Override
    public Jwt decode(final String token) {

        final SignedJWT signedJWT = SignedJWT.parse(token);

        if (!signedJWT.verify(verifier)) {
            throw new JwtException("JWT verification failed");
        }

        return createJwt(token, JWTParser.parse(token));

    }

    private Jwt createJwt(final String token, final JWT parsedJwt) throws ParseException {

        final Map<String, Object> headers = new LinkedHashMap<>(parsedJwt.getHeader().toJSONObject());
        final Map<String, Object> claims = parsedJwt.getJWTClaimsSet().getClaims();

        // @formatter:off
        return Jwt.withTokenValue(token)
            .headers((h) -> h.putAll(headers))
            .claims((c) -> c.putAll(MappedJwtClaimSetConverter.withDefaults(emptyMap()).convert(claims)))
            .build();
    }

    @SneakyThrows @Override public SignedJWT encode(final String userId) {

        final long now = new Date().getTime();

        final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(userId)
            .jwtID(UUID.randomUUID().toString())
            .audience("https://klaversjassen.nl")
            .notBeforeTime(new Date(now - Duration.ofMinutes(5).toSeconds()))
            .issuer("https://api.cardserver.nl")
            .issueTime(new Date(now))
            .expirationTime(new Date(now + Duration.ofDays(356 * 69).toSeconds()))
            .claim("scp", "USER")
            .build();

        final SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet);

        signedJWT.sign(signer);

        return signedJWT;

    }

    @SneakyThrows
    private byte[] getHash() {
        return cardServerProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }
}
