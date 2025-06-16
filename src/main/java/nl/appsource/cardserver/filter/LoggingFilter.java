package nl.appsource.cardserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.lang.System.currentTimeMillis;

@Component
@Order(1)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final ThreadLocal<StringBuffer> STRING_THREAD_LOCAL = ThreadLocal.withInitial(StringBuffer::new);

    public static final void requestLogMessage(final String message) {
        STRING_THREAD_LOCAL.get().append(message);
    }

    @Override
    public void doFilterInternal(final HttpServletRequest servletRequest, final HttpServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {

        final long start = currentTimeMillis();
        final String remoteAddr = servletRequest.getRemoteAddr();

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String name = "" + authentication.getName();

        log.trace(
            "Starting a transaction for req : {}",
            servletRequest.getRequestURI());

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            log.info(
                "{} {}, {}, {} msec, {}",
                remoteAddr, servletRequest.getRequestURI(), name, currentTimeMillis() - start, STRING_THREAD_LOCAL.get());
            STRING_THREAD_LOCAL.remove();
        }
    }

}
