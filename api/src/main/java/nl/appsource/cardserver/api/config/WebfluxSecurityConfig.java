package nl.appsource.cardserver.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@Slf4j
public class WebfluxSecurityConfig {

    private final UserRepository userRepository;

    @Bean
    public MyJwtAuthenticationConverter jwtAuthenticationConverter() {
        return new MyJwtAuthenticationConverter(userRepository);
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityFilterChainPrivate(final ServerHttpSecurity http) {

        return http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getPrivateCorsConfigurationSource()))
            .securityMatcher(new OrServerWebExchangeMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**")))
            .authorizeExchange((exchanges) -> exchanges.anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .build();
    }

    @Bean
    @Order(3)
    public SecurityWebFilterChain securityFilterChainPublic(final ServerHttpSecurity http) {

        // allow from everywhere
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getPublicCorsConfigurationSource()))
            .authorizeExchange(
                exchanges -> exchanges.pathMatchers(HttpMethod.GET, "/", "/actuator/**", "/index.html", "/logo192.png", "/schema/**", "/error/**", "/favicon.ico", "/.well-known/jwks.json", "/.well-known/openid-configuration", "/version.json", "/webjars/**", "/public/**")
                    .permitAll()
            )
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .build();
    }

    @Bean
    @Order(4)
    public SecurityWebFilterChain securityFilterChainDenyAll(final ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
            .build();
    }

    public CorsConfigurationSource getPublicCorsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

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
