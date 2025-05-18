package nl.appsource.cardserver.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class WebSecurityConfig {

    private final Environment environment;

    private final ApiKeyAuthFilter apiKeyAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {

        final Set<String> privateUrls;
        if (environment.acceptsProfiles(Profiles.of("development", "citest"))) {
            privateUrls = Set.of("/h2-console/**");
        } else {
            privateUrls = Collections.emptySet();
        }

        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS));

        //        http
//            .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(getCorsConfigurationSource()));

        http
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers(Stream.concat(Set.of("/", "/websocket/**", "/manage/**", "/index.html", "/star.png", "/schema/**", "/error/**", "/api/v1/login").stream(), privateUrls.stream()).toArray(String[]::new))
                .permitAll()
                .anyRequest().authenticated()
            ).addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    //    public CorsConfigurationSource getCorsConfigurationSource() {
//        final CorsConfiguration configuration = new CorsConfiguration();
//        configuration.setAllowCredentials(true);
//        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://www.klaversjassen.nl"));
//        configuration.setAllowedHeaders(Collections.singletonList("*"));
//        configuration.setAllowedMethods(Collections.singletonList("*"));
//        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }

}

