package nl.appsource.cardserver.stream.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class WebfluxSecurityConfig {

    private ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        final ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();
        reactiveJwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(source -> Flux.just(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ROLE_USER")));
        return reactiveJwtAuthenticationConverter;
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityFilterChainApi(final ServerHttpSecurity http) {

        // allowed with a valid KlaversJassen Jwt
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
            .securityMatcher(new OrServerWebExchangeMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**")))
            .authorizeExchange((exchanges) -> exchanges.anyExchange().authenticated())
            .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer -> httpSecurityOAuth2ResourceServerConfigurer.jwt(customizer -> {
                customizer.jwtDecoder(NimbusReactiveJwtDecoder.withIssuerLocation("https://auth.impl.nl/realms/klaversjassen").build());
                customizer.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter());
            }));
        return http.build();
    }

    @Bean
    @Order(200)
    public SecurityWebFilterChain securityFilterChainManagement(final ServerHttpSecurity http) {
        return http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**"))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.GET, "/actuator/**").permitAll())
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        final CorsConfiguration publicConfig = new CorsConfiguration();
        publicConfig.setAllowedOriginPatterns(List.of("*"));
        publicConfig.setAllowedMethods(List.of("GET"));

        source.registerCorsConfiguration("/actuator/**", publicConfig);

        final CorsConfiguration privateConfig = new CorsConfiguration();
        privateConfig.setAllowCredentials(true);
        privateConfig.setAllowedOriginPatterns(List.of(
            "https://www.klaversjassen.nl",
            "https://klaversjassen.nl",
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "http://localhost",
            "http://127.0.0.1"
        ));
        privateConfig.setAllowedHeaders(List.of("*"));
        privateConfig.setAllowedMethods(List.of("POST"));
        privateConfig.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/v1/**", privateConfig);

        return source;
    }
}
