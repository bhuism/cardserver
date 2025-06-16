package nl.appsource.cardserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.CardServerJwtModem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class WebSecurityConfig {

    private final Environment environment;

    private final CardServerJwtModem cardServerJwtModem;

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChainOauth(final HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()))
            .securityMatcher("/whoami")
            .authorizeHttpRequests((authorizationManagerRequestMatcherRegistry -> {
                authorizationManagerRequestMatcherRegistry.requestMatchers(HttpMethod.POST, "/whoami")
                    .authenticated();
            }
            )).oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer -> httpSecurityOAuth2ResourceServerConfigurer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChainApi(final HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()))
            .securityMatcher("/api/v1/**")
            .authorizeHttpRequests((authorizationManagerRequestMatcherRegistry -> {
                authorizationManagerRequestMatcherRegistry.requestMatchers("/api/v1/**")
                    .authenticated();
            }
            )).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(cardServerJwtModem::decode)));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain securityFilterChainRest(final HttpSecurity http) throws Exception {

        final Set<String> privateUrls;
        if (environment.acceptsProfiles(Profiles.of("development", "citest"))) {
            privateUrls = Set.of("/h2-console/**");
        } else {
            privateUrls = Collections.emptySet();
        }

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()))
            .securityMatcher("/**")
            .authorizeHttpRequests((authorizationManagerRequestMatcherRegistry) -> {
                    authorizationManagerRequestMatcherRegistry
                        .requestMatchers(Stream.concat(Set.of("/", "/manage/**", "/index.html", "/star.png", "/schema/**", "/error/**").stream(), privateUrls.stream()).toArray(String[]::new))
                        .permitAll()
                        .anyRequest()
                        .denyAll();
                }
            );

        return http.build();
    }


    public CorsConfigurationSource getCorsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:4280", "https://www.klaversjassen.nl"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}

