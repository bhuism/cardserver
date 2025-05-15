package nl.appsource.cardserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Collections;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends GenericFilterBean {

    private static final String API_KEY_HEADER = "API-Key";
    private static final String API_SECRET_HEADER = "API-Secret";

    private final CardServerProperties cardServerProperties;

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {

        try {

            final String apiKey = ((HttpServletRequest) request).getHeader(API_KEY_HEADER);
            final String apiSecret = ((HttpServletRequest) request).getHeader(API_SECRET_HEADER);

//        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
//            final String value = request.getHeader(headerName);
//            log.info("header: {}={}", headerName, value);
//        });

            if (hasText(apiKey) && hasText(apiSecret)) {
                if (cardServerProperties.getApiSecret().equals(apiSecret)) {
                    final ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(apiKey, apiSecret, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
                }
            }

        } finally {
            chain.doFilter(request, response);
        }
    }

//    @Override
//    public Authentication attemptAuthentication(final HttpServletRequest request, final HttpServletResponse response)
//        throws AuthenticationException {
//
//        final String apiKey = request.getHeader(API_KEY_HEADER);
//        final String apiSecret = request.getHeader(API_SECRET_HEADER);
//
////        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
////            final String value = request.getHeader(headerName);
////            log.info("header: {}={}", headerName, value);
////        });
//
//        if (isBlank(apiKey) || isBlank(apiKey)) {
//            return null;
//        }
//
//        final Authentication auth = new ApiKeyAuthenticationToken(apiKey, apiSecret);
//
//        return getAuthenticationManager().authenticate(auth);
//    }
//
//    @Override
//    protected void successfulAuthentication(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain, final Authentication authResult) throws IOException, ServletException {
//        super.successfulAuthentication(request, response, chain, authResult);
//        chain.doFilter(request, response);
//    }
}
