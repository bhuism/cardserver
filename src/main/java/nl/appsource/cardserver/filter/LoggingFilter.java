package nl.appsource.cardserver.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Slf4j
public class LoggingFilter {

    private static final ThreadLocal<StringBuffer> STRING_THREAD_LOCAL = ThreadLocal.withInitial(StringBuffer::new);

    public static void requestLogMessage(final String message) {
        STRING_THREAD_LOCAL.get().append(message);
    }

//    @Override
//    public Mono<Void> filter(final ServerWebExchange serverWebExchange, final WebFilterChain webFilterChain) {
//
//        final long start = currentTimeMillis();
//        final ServerHttpRequest request = serverWebExchange.getRequest();
//        final String remoteAddr = "" + (request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress() : "");
////        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
////        final String name = authentication != null ? authentication.getName() : "null";
//
//        return webFilterChain.filter(serverWebExchange)
//            .flatMap((unused) -> ReactiveSecurityContextHolder.getContext())
//            .map(SecurityContext::getAuthentication)
//            .map(Authentication::getName)
//            .switchIfEmpty(Mono.just("null"))
//            .flatMap(name -> {
//
//                if (!request.getPath().toString().startsWith("/manage")) {
//                    log.info(
//                        "{} {} {}, {}, {} msec {}",
//                        remoteAddr, request.getMethod(), request.getPath(), name, currentTimeMillis() - start, STRING_THREAD_LOCAL.get());
//                }
//
//                STRING_THREAD_LOCAL.remove();
//                return Mono.empty();
//            });


//        return ReactiveSecurityContextHolder.getContext()
//            .flatMap(securityContext -> {
//                return webFilterChain.filter(serverWebExchange);
//            })
//            .doFinally(signalType -> {
//                if (!request.getPath().toString().startsWith("/manage")) {
//                    log.info(
//                        "{} {} {}, {}, {} msec {}",
//                        remoteAddr, request.getMethod(), request.getPath(), name, currentTimeMillis() - start, STRING_THREAD_LOCAL.get());
//                    STRING_THREAD_LOCAL.remove();
//                }
//            });
//    }


}
