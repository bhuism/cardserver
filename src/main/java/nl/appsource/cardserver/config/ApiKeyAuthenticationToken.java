package nl.appsource.cardserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;


@Getter
@Setter
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;
    private final String apiSecret;

//    public ApiKeyAuthenticationToken(final String apiKeyArg, final String apiSecretArg) {
//        super(null);
//        this.apiKey = apiKeyArg;
//        this.apiSecret = apiSecretArg;
//        setAuthenticated(false);
//    }

    public ApiKeyAuthenticationToken(final String apiKeyArg, final String apiSecretArg, final Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKey = apiKeyArg;
        this.apiSecret = apiSecretArg;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.apiSecret;
    }

    @Override
    public Object getPrincipal() {
        return this.apiKey;
    }

    @Override
    public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException("Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }
        super.setAuthenticated(false);
    }
}
