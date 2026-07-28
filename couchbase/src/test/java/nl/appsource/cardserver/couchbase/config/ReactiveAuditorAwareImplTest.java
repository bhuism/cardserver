package nl.appsource.cardserver.couchbase.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveAuditorAwareImplTest {

    private final ReactiveAuditorAwareImpl auditorAware = new ReactiveAuditorAwareImpl();

    @Test
    void shouldReturnEmptyWhenNoSecurityContext() {
        StepVerifier.create(auditorAware.getCurrentAuditor())
            .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenAuthenticationIsNull() {
        SecurityContext context = new SecurityContextImpl();
        // authentication is null by default in SecurityContextImpl

        StepVerifier.create(auditorAware.getCurrentAuditor()
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))))
            .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNotAuthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContext context = new SecurityContextImpl(authentication);

        StepVerifier.create(auditorAware.getCurrentAuditor()
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))))
            .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNameIsNull() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(null);
        SecurityContext context = new SecurityContextImpl(authentication);

        StepVerifier.create(auditorAware.getCurrentAuditor()
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))))
            .verifyComplete();
    }

    @Test
    void shouldReturnAuditorWhenNameIsSet() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn("user1");
        SecurityContext context = new SecurityContextImpl(authentication);

        StepVerifier.create(auditorAware.getCurrentAuditor()
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))))
            .expectNext("user1")
            .verifyComplete();
    }

}
