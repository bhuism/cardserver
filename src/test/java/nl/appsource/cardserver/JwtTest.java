package nl.appsource.cardserver;


import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.config.CardServerProperties;
import nl.appsource.cardserver.service.CardServerJwtModem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class JwtTest {

    private CardServerJwtModem cardServerJwtModem = new CardServerJwtModem(new CardServerProperties().setJwtSecret("test123"));

    @Test
    public void testJwtEncodeDecode() {


        final String jwt = cardServerJwtModem.encode("dit is een test");

        log.info("encrypted: {}", jwt);

        final String actual = cardServerJwtModem.decode(jwt);

        assertThat(actual).isEqualTo("dit is een test");


    }

}
