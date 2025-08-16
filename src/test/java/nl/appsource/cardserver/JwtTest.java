package nl.appsource.cardserver;


import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerProperties;
import nl.appsource.cardserver.service.CardServerJwtModemImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class JwtTest {


    @Test
    public void testJwtEncodeDecode() {

        final CardServerJwtModemImpl cardServerJwtModem = new CardServerJwtModemImpl(new CardServerProperties().setJwtSecret("test123test123test123test123test123test123test123test123"));

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
