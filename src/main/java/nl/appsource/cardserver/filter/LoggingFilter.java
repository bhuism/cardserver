package nl.appsource.cardserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.lang.System.currentTimeMillis;

@Component
@Order(1)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final ThreadLocal<String> STRING_THREAD_LOCAL = new ThreadLocal<>() {
        @Override
        protected String initialValue() {
            return "";
        }
    };

    public static final void requestLogMessage(final String message) {
        STRING_THREAD_LOCAL.set(STRING_THREAD_LOCAL.get() + message);
    }

    @Override
    public void doFilterInternal(final HttpServletRequest servletRequest, final HttpServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {

        final long start = currentTimeMillis();
        final String remoteAddr = servletRequest.getRemoteAddr();

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = "" + principal.getClaims().get("email");

        //            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("headers {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );


        log.trace(
            "Starting a transaction for req : {}",
            servletRequest.getRequestURI());

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            log.info(
                "{} {}, {}, {} msec, {}",
                remoteAddr, servletRequest.getRequestURI(), email, currentTimeMillis() - start, STRING_THREAD_LOCAL.get());
        }
    }

}
