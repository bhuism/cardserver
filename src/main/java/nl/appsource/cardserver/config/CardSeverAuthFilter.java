package nl.appsource.cardserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardSeverAuthFilter extends OncePerRequestFilter {

    private final UserService userService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws ServletException, IOException {

        Optional.ofNullable(request)
            .map(r -> r.getHeader("cardserverauth"))
            .filter(a -> a instanceof String)
            .map(a -> (String) a)
            .filter(s -> StringUtils.hasText(s))
            .filter(s -> ((String) s).length() == 28)
            .flatMap(userService::findById)
            .map(user -> CardServerAuthenticationToken.authenticated(user))
            .ifPresentOrElse(cardServerAuthenticationToken -> {
                log.info("Succesfull CardSever API authentication");
                SecurityContextHolder.setContext(new SecurityContextImpl(cardServerAuthenticationToken));
            }, () -> log.info("Unsuccessfull CardServer API authentication"));

        filterChain.doFilter(request, response);
    }
}
