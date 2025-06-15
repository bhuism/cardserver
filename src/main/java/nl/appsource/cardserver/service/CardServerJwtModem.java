package nl.appsource.cardserver.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import nl.appsource.cardserver.config.CardServerProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardServerJwtModem implements JwtDecoder {

    private final CardServerProperties cardServerProperties;

    @SneakyThrows
    public Jwt decode(final String token) {

        return createJwt(token, JWTParser.parse(token));

//        final Jwt decode = new NimbusJwtDecoder(new DefaultJWTProcessor<>()).decode(token);


//        decode.
//        final SignedJWT signedJWT = SignedJWT.parse(token);
//        final JWSVerifier verifier = new MACVerifier(getHash());
////
//        if (!signedJWT.verify(verifier)) {
//            throw new IllegalArgumentException("JWT verification failed");
//        }
//
//
//        return jwsObject.getPayload().toString();


    }

    private Jwt createJwt(String token, JWT parsedJwt) throws ParseException {

        Map<String, Object> headers = new LinkedHashMap<>(parsedJwt.getHeader().toJSONObject());
        //JWTClaimsSet jwtClaimsSet = this.jwtProcessor.process(parsedJwt, null);
        Map<String, Object> claims = parsedJwt.getJWTClaimsSet().getClaims();

        // @formatter:off
        return Jwt.withTokenValue(token)
            .headers((h) -> h.putAll(headers))
            .claims((c) -> c.putAll(claims))
            .build();
    }

    @SneakyThrows
    public Jwt encode(final String userId) {

        final ImmutableSecret<SecurityContext> immutableSecret = new ImmutableSecret<>(getHash());

        final NimbusJwtEncoder nimbusJwtEncoder = new NimbusJwtEncoder(immutableSecret);

        final JwsHeader.Builder jwsHeaderBuilder = JwsHeader.with(MacAlgorithm.HS256);

        final JwtClaimsSet claims = JwtClaimsSet.builder().subject(userId).build();

        final JwtClaimsSet.Builder claimSetBuilder = JwtClaimsSet.from(claims);

        final JwtEncoderParameters jwtEncoderParameters = JwtEncoderParameters.from(jwsHeaderBuilder.build(), claimSetBuilder.build());

        final Jwt encode = nimbusJwtEncoder.encode(jwtEncoderParameters);

        final JWSSigner signer = new MACSigner(getHash());
        //final JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(Map.of("sub", userId, "alg", "HS256")));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject("userId")
            .issuer("https://api.cardserver.nl")
            .expirationTime(new Date(new Date().getTime() + 60 * 1000))
            .build();

        final SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

        signedJWT.sign(signer);

        return encode;

    }

    @SneakyThrows
    private byte[] getHash() {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] encodedhash = digest.digest(cardServerProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return encodedhash;
    }
}
