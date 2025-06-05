package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.GameService;
import org.openapitools.api.GamesApi;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class GameController implements GamesApi {

    private final GameService gameService;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Game> getGame(final String gameId) {
        LoggingFilter.requestLogMessage("getGame(" + gameId + ")");
        return gameService.findById(gameId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<Game>> getGames() {

        LoggingFilter.requestLogMessage("getGames()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();

        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = principal.getClaims().get("email").toString();

        final long start = System.currentTimeMillis();
        try {
            final List<Game> games = gameService.findByCreator(email);
            return new ResponseEntity<>(games, HttpStatus.OK);
        } finally {


//            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("header: {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );

            log.info("{} {} getGames() took {} ms", remoteAddr, email, System.currentTimeMillis() - start);
        }

    }

    @Override
    public ResponseEntity<Game> createGame(final CreateGame createGame) {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();

        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = principal.getClaims().get("email").toString();


        final User user = userRepository.findOptionalByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));


        final long start = System.currentTimeMillis();
        try {
            return ResponseEntity.ok(gameService.createGame(user.getId(), createGame.getPlayers()));
        } finally {
            log.info("{} {} createGame() took {} ms", remoteAddr, email, System.currentTimeMillis() - start);
        }
    }

    @Override
    public ResponseEntity<Void> deleteGame(final String gameId) {
        LoggingFilter.requestLogMessage("deleteGame(" + gameId + ")");
        gameService.deleteGame(gameId);
        return ResponseEntity.ok().build();
    }
}
