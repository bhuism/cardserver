package nl.appsource.cardserver.stream.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBaseController {

//    @Autowired
//    private UserRepository userRepository;

    protected Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .mapNotNull(Authentication::getDetails)
            .cast(String.class)
            .filter(StringUtils::hasText);
    }


}
