package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AddResponseHeaderFilter implements WebFilter {

    private final GitProperties gitProperties;

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        exchange.getResponse().getHeaders().add("CardServerVersion", gitProperties.getCommitId());
        return chain.filter(exchange);
    }

}
