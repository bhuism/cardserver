package nl.appsource.cardserver.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class LogPatternExtractor {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(LogPatternExtractor.class, args);
        Environment env = ctx.getEnvironment();
        System.out.println("CONSOLE_LOG_PATTERN: " + env.getProperty("CONSOLE_LOG_PATTERN"));
        System.out.println("logging.pattern.console: " + env.getProperty("logging.pattern.console"));
        ctx.close();
    }
}
