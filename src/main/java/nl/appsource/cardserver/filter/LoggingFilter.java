package nl.appsource.cardserver.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static java.lang.System.currentTimeMillis;

@Component
@Order(1)
@Slf4j
public class LoggingFilter implements WebFilter {

    private static final ThreadLocal<StringBuffer> STRING_THREAD_LOCAL = ThreadLocal.withInitial(StringBuffer::new);

    public static final void requestLogMessage(final String message) {
        STRING_THREAD_LOCAL.get().append(message);
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange serverWebExchange, final WebFilterChain webFilterChain) {

        final long start = currentTimeMillis();
        final ServerHttpRequest request = serverWebExchange.getRequest();
        final String remoteAddr = "" + request.getRemoteAddress().getAddress();

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String name = authentication != null ? authentication.getName() : "null";

//        log.trace(
//            "Starting a transaction for req : {}",
//            servletRequest.getRequestURI());

        try {
            return webFilterChain.filter(serverWebExchange);
        } finally {
            if (!request.getPath().toString().startsWith("/manage")) {
                log.info(
                    "{} {} {}, {}, {} msec {}",
                    remoteAddr, request.getMethod(), request.getPath(), name, currentTimeMillis() - start, STRING_THREAD_LOCAL.get());
                STRING_THREAD_LOCAL.remove();
            }
        }

    }


}
