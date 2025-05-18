package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.GameService;
import org.openapitools.api.GameApi;
import org.openapitools.api.GamesApi;
import org.openapitools.model.Game;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class GameController implements GameApi, GamesApi {

    private final GameService gameService;

    @Override
    public ResponseEntity<Game> getGame(final String gameId) {

        final long start = System.currentTimeMillis();
        try {

            return gameService.findById(gameId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

        } finally {

//            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("headers {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );

            final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();
            final String email = "empty";

            log.info("{} {} getGame({}) took {} ms", remoteAddr, email, gameId, System.currentTimeMillis() - start);
        }
    }


    @Override
    public ResponseEntity<Set<String>> getGames() {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();
        final String principal = "" + authentication.getPrincipal();

        final long start = System.currentTimeMillis();
        try {
            final Set<String> games = gameService.findByCreator(principal);
            return new ResponseEntity<>(games, HttpStatus.OK);
        } finally {


//            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("header: {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );

            log.info("{} {} getGames() took {} ms", remoteAddr, principal, System.currentTimeMillis() - start);
        }

    }


}
