package nl.appsource.cardserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.CardServerJwtModem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@Slf4j
public class WebfluxSecurityConfig {

    private final Environment environment;

    private final CardServerJwtModem cardServerJwtModem;

    @Bean
    @Order(1)
    public SecurityWebFilterChain securityFilterChainOauth(final ServerHttpSecurity http) throws Exception {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
//            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
//            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource())).securityMatcher(new PathPatternParserServerWebExchangeMatcher("/whoami", HttpMethod.POST)).authorizeExchange((exchanges) -> exchanges.anyExchange().authenticated()).oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer -> {
                //httpSecurityOAuth2ResourceServerConfigurer.jwt(Customizer.withDefaults());
                httpSecurityOAuth2ResourceServerConfigurer.jwt(customizer -> customizer.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()));
            });
        return http.build();
    }

    private ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        final ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();
        reactiveJwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(source -> Flux.just(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ROLE_USER")));
        return reactiveJwtAuthenticationConverter;
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityFilterChainApi(final ServerHttpSecurity http) throws Exception {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
//            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
//            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()))
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**"))
            .authorizeExchange((exchanges) -> exchanges.anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.jwtDecoder(token -> Mono.just(cardServerJwtModem.decode(token)))));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityWebFilterChain securityFilterChainRest(final ServerHttpSecurity http) throws Exception {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
//            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource())).authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.GET, "/", "/manage/**", "/index.html", "/logo192.png", "/logo512.png", "/schema/**", "/error/**").permitAll().anyExchange().denyAll());

        return http.build();
    }

    public CorsConfigurationSource getCorsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of("https://www.klaversjassen.nl", "http://localhost:3000"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setExposedHeaders(Collections.singletonList("*"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}

