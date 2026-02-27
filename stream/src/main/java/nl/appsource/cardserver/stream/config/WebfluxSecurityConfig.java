package nl.appsource.cardserver.stream.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class WebfluxSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChainOauth(final ServerHttpSecurity http) {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getPrivateCorsConfigurationSource()))
            .securityMatcher(new OrServerWebExchangeMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**")))
            .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

        return http.build();
    }

//    @Bean
//    public ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuerUri) {
//        return NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri)
//            //.jwsAlgorithm(com.nimbusds.jose.JWSAlgorithm.EdDSA)
//            //.jwsAlgorithm(SignatureAlgorithm.RS256)
//            //.jwsAlgorithms(signatureAlgorithms -> signatureAlgorithms.addAll(List.of(JWSAlgorithm.EdDSA, SignatureAlgorithm.RS256)))
//            .build();
//    }

    public CorsConfigurationSource getPrivateCorsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of("https://www.klaversjassen.nl", "https://klaversjassen.nl", "http://localhost:4280"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
