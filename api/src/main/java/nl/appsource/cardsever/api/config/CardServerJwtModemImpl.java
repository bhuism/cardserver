package nl.appsource.cardsever.api.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
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

    private ECKey es512eckey;

    @PostConstruct
    public void init() throws JOSEException, ParseException {

        es512eckey = ECKey.parse(cardServerProperties.getJwtEs512Secret());

        verifier = new ECDSAVerifier(es512eckey.toPublicJWK());

        signer = new ECDSASigner(es512eckey);

    }

    @Override
    public List<JWK> getJKSKets() {
        return List.of(es512eckey.toPublicJWK());
    }

    @Override
    public Mono<Jwt> decode(final String token) {

        try {
            final SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new BadJwtException("JWT verification failed");
            }

            return Mono.just(createJwt(token, JWTParser.parse(token)));

        } catch (ParseException | JOSEException | IllegalStateException e) {
            throw new BadJwtException("JWT verification failed", e);
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

        final Instant now = Instant.now();

        final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(userId)
            .jwtID(jwtId)
            .audience("https://klaversjassen.nl")
            .notBeforeTime(Date.from(now.minus(Duration.ofMinutes(5))))
            .issuer(ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofDays(365L * 69))))
//            .expirationTime(Date.from(now.plus(Duration.ofDays(1))))
            .claim("scp", "USER")
            .build();

        final SignedJWT signedJWT = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES512)
                .keyID(es512eckey.toPublicJWK().getKeyID())
                .type(JOSEObjectType.JWT)
                .jwkURL(URI.create("https://api.klaversjassen.nl/.well-known/jwks.json"))
                .build(), claimsSet);

        signedJWT.sign(signer);

        return signedJWT;

    }

}
