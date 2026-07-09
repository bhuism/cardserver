package nl.appsource.cardserver.api;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LogTest {
    private static final Logger log = LoggerFactory.getLogger(LogTest.class);
    
    @Test
    public void testLogs() {
        log.info("Hello World Log Test");
    }
}
