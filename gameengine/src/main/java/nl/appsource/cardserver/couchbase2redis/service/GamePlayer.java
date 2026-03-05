package nl.appsource.cardserver.couchbase2redis.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class GamePlayer {

    private final RedisPubSubService redisPubSubService;

    @PostConstruct
    public void init() {
        log.info("GamePlayer init");
        redisPubSubService.listenTo("updateGame").subscribe(myServerSentEvent -> {
            if (myServerSentEvent.event().equals("updateGame")) {
                log.info("gameUpdate to gameId={}", myServerSentEvent.data());
            }
        });

    }

}
