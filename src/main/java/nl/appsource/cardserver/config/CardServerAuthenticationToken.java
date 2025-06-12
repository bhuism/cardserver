package nl.appsource.cardserver.config;

import lombok.Getter;
import nl.appsource.cardserver.model.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

import static java.util.Collections.singleton;

public class CardServerAuthenticationToken extends AbstractAuthenticationToken {

    private static final GrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");


    @Getter
    private final Object principal;

    @Getter
    private final Object credentials;

    // unauthenticated
    private CardServerAuthenticationToken(final Object aprincipal) {
        super(Collections.emptyList());
        this.principal = aprincipal;
        this.credentials = aprincipal;
        setAuthenticated(false);
    }

    // authenticated
    private CardServerAuthenticationToken(final Object aprincipal, final Collection<? extends GrantedAuthority> authorities, final Object details) {
        super(authorities);
        this.principal = aprincipal;
        this.credentials = null;
        setAuthenticated(true);
        setDetails(details);
    }

    public static CardServerAuthenticationToken unAuthenticated(final String userId) {
        return new CardServerAuthenticationToken(userId);
    }

    public static CardServerAuthenticationToken authenticated(final User user) {
        return new CardServerAuthenticationToken(user.getId(), singleton(ROLE_USER), user);
    }

}
