package nl.appsource.cardserver.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
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

    public static final String ISSUER = "https://api.klaversjassen.nl";

    private final CardServerProperties cardServerProperties;

    private JWSVerifier verifier;

    private JWSSigner signer;

    private OctetKeyPair okp;

    @PostConstruct
    public void init() throws JOSEException, ParseException {
        okp = OctetKeyPair.parse(cardServerProperties.getJwtEd25519Secret());
        verifier = new Ed25519Verifier(okp.toPublicJWK());
        signer = new Ed25519Signer(okp);
    }

    @Override
    public OctetKeyPair getPublicKey() {
        return okp.toPublicJWK();
    }

    @Override
    public Mono<Jwt> decode(final String token) {

        try {
            final SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new JwtException("JWT verification failed");
            }

            return Mono.just(createJwt(token, JWTParser.parse(token)));

        } catch (ParseException | JOSEException | IllegalStateException e) {
            throw new JwtException("JWT verification failed", e);
        }

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

    @Override
    public SignedJWT encode(final String userId) throws JOSEException {

        final String jwtId = UUID.randomUUID().toString();

        log.info("Creating a new jwt for user {} with id {}", userId, jwtId);

        final long now = new Date().getTime();

        final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(userId)
            .jwtID(jwtId)
            .audience("https://klaversjassen.nl")
            .notBeforeTime(new Date(now - Duration.ofMinutes(5).toSeconds()))
            .issuer(ISSUER)
            .issueTime(new Date(now))
            .expirationTime(new Date(now + Duration.ofDays(356 * 69).toSeconds()))
            .claim("scp", "USER")
            .build();

        final SignedJWT signedJWT = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(getPublicKey().getKeyID())
                .type(JOSEObjectType.JWT)
                .jwkURL(URI.create("https://api.klaversjassen.nl/.well-known/jwks.json"))
                .build(), claimsSet);

        signedJWT.sign(signer);

        return signedJWT;

    }

}
