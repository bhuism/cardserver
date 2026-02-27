package nl.appsource.cardserver;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerJwtModemImpl;
import nl.appsource.cardserver.config.CardServerProperties;
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

        final CardServerJwtModemImpl cardServerJwtModem = new CardServerJwtModemImpl(new CardServerProperties().setJwtEs512Secret("eyJrdHkiOiJFQyIsImQiOiJBTGpyMVRxdk5iT1JfSHhId0psQm5KNU15YlUxRHo4R2J1NGFyRW9K\n" +
            "SXJKNzZlVmRwUVRwQndBbUF1N09EaFczOUx1LWlacXAycUZFX1RFUjZMYjdaeUg2IiwiY3J2Ijoi\n" +
            "UC01MjEiLCJraWQiOiJlczUxMi1rZXktMSIsIngiOiJBQ3RyVlVOMVhyb0dYQnhWcG51UXBLaWpt\n" +
            "SXJFSXYwLWk4eVNmUl9LZGo5bVd6Y3o3NXp0VFZtRUNfbGxmWkxYWWF2TmNZb280aHpQTFJWNTU0\n" +
            "b0JjbG1rIiwieSI6IkFVM0dPRGwyZWZ3dmc1Y2x6NVdlbjNRcnY2WEh3Y2FWVWdKYVhyeG1JdFZ6\n" +
            "QlBqc0ViSlI0ajhIQzRobzItOG8xTlJoaDl4MHpocVVqV3lXNXRvNXlkcGYifQo="));

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
