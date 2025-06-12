package nl.appsource.cardserver.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class CardSeverAuthFilter extends OncePerRequestFilter {

    private static final String CARDSERVER_AUTH_HEADER_KEY = "cardserverauth";
    private static final GrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");

    private final UserService userService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final String userId = request.getHeader(CARDSERVER_AUTH_HEADER_KEY);

        try {

            log.info("Start: userId={}", userId);

            if (StringUtils.hasText(userId) && userId.length() == 28) {
                userService.findById(userId).ifPresentOrElse(user -> {
                    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user.getId(), null, Collections.singletonList(ROLE_USER));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }, () -> {
                    log.warn("User {} not found", userId);
                });
            } else {
                log.warn("Invalid userId");
            }

            filterChain.doFilter(request, response);

        } finally {
            log.info("End: userId={}", userId);
        }
    }
}
