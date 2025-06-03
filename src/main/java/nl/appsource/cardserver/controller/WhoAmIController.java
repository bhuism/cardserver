package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.WhoamiApi;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class WhoAmIController implements WhoamiApi {

    private final UserService userService;

    @Override
    public ResponseEntity<User> whoami() {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();

        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = principal.getClaims().get("email").toString();

        final long start = System.currentTimeMillis();
        try {
            return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } finally {


//            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("header: {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );

            log.info("{} {} whoami() took {} ms", remoteAddr, email, System.currentTimeMillis() - start);
        }

    }

    @Override
    public ResponseEntity<Set<User>> incomingFriends() {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String remoteAddr = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr();

        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = principal.getClaims().get("email").toString();

        final long start = System.currentTimeMillis();
        try {
            return userService.findByEmail(email)
                .map(userService::findAllIncomingInvites)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } finally {


//            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeaderNames().asIterator().forEachRemaining(headerName ->
//                log.info("header: {}={}", headerName, ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(headerName))
//            );

            log.info("{} {} whoami() took {} ms", remoteAddr, email, System.currentTimeMillis() - start);
        }
    }
}
