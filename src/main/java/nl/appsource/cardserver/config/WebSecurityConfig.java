package nl.appsource.cardserver.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.util.StringUtils.hasText;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class WebSecurityConfig {

    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {

        final Set<String> privateUrls;
        if (environment.acceptsProfiles(Profiles.of("development", "citest"))) {
            privateUrls = Set.of("/h2-console/**");
        } else {
            privateUrls = Collections.emptySet();
        }

        http
            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()));

        http
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers(Stream.concat(Set.of("/", "/websocket/**", "/manage/**", "/index.html", "/star.png", "/schema/**", "/error/**").stream(), privateUrls.stream()).toArray(String[]::new))
                .permitAll()
                .anyRequest().authenticated()
            ).logout(LogoutConfigurer::permitAll);

        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            http.oauth2ResourceServer(oauth2 -> {
                oauth2.jwt(jwt -> {
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter());
                });
            });
        }


        return http.build();
    }

    @Bean
    @Profile({"development", "production"})
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        log.info("Construct jwtAuthenticationConverter()");

        // return new JwtAuthenticationConverter();

        return new Converter<>() {

            private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

            @Override
            public AbstractAuthenticationToken convert(final Jwt jwt) {
                final Collection<GrantedAuthority> authorities = this.jwtGrantedAuthoritiesConverter.convert(jwt);
                final String principalClaimValue = jwt.getClaimAsString(JwtClaimNames.SUB);

                final String email = jwt.getClaimAsString("email");

                if (!hasText(email)) {
                    throw new InvalidBearerTokenException("No email in JWT");
                }

//                if (!accountService.accountExists(email)) {
//                    log.error("Failed login for user: {}", email);
//                    throw new UsernameNotFoundException(email);
//                }

                return new JwtAuthenticationToken(jwt, authorities, principalClaimValue);
            }
        };

    }

    @Bean
    @Profile({"development", "production"})
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("https://accounts.google.com");
    }

    public CorsConfigurationSource getCorsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://www.klaversjassen.nl"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}

