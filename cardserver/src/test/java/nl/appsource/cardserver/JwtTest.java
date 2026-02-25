package nl.appsource.cardserver;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerProperties;
import nl.appsource.cardserver.service.CardServerJwtModemImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class JwtTest {


    @Disabled
    @Test
    public void testJwtEncodeDecode() throws JOSEException, ParseException, JsonProcessingException {


        final CardServerJwtModemImpl cardServerJwtModem = new CardServerJwtModemImpl(new CardServerProperties().setJwtEd25519Secret("eyJrdHkiOiJPS1AiLCJkIjoiVTNPbk8tbm84c3NhNkNOYXdsenk5V2tmcDVHV05Bd005VjU3UUtlUXA5OCIsInVzZSI6InNpZyIsImNydiI6IkVkMjU1MTkiLCJraWQiOiJ0ZXN0LTgta2V5LUEtNzQ1OTkyIiwieCI6InViV09mdlhVNHVoSjc1dVBEM0J6QVZOazBGRXVEeGFTbzhMQm0zSHRnVzQiLCJhbGciOiJFZERTQSJ9Cg=="));

        cardServerJwtModem.init();

        final SignedJWT jwt = cardServerJwtModem.encode("dit is een test");

        final String tokenValue = jwt.serialize();

        log.info("token: {}", tokenValue);

        final Jwt actual = cardServerJwtModem.decode(tokenValue).block();

        log.info("header: {}", actual.getHeaders());
        log.info("claims: {}", actual.getClaims());

        assertThat(actual.getSubject()).isEqualTo("dit is een test");

    }

}
