package nl.appsource.cardserver.admin;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;

@EnableAdminServer
@SpringBootApplication
@EnableWebFluxSecurity
public class AdminApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(final ServerHttpSecurity http, final AdminServerProperties adminServerProperties) {
        return http.authorizeExchange(spec -> spec.pathMatchers(adminServerProperties.getContextPath() + "/assets/**")
                .permitAll()
                .pathMatchers(adminServerProperties.getContextPath() + "/login")
                .permitAll()
                .pathMatchers("/manage/**")
                .permitAll()
                .anyExchange()
                .authenticated())
            .formLogin(spec -> spec.loginPage(adminServerProperties.getContextPath() + "/login")
                .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler(adminServerProperties.getContextPath())))
            .logout(spec -> spec.logoutUrl(adminServerProperties.getContextPath() + "/logout"))
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .build();
    }
}
