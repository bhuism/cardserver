package nl.appsource.cardserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.GameEngineException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalDefaultExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity defaultErrorHandler(final HttpServletRequest req, final Exception e) {
        log.error("General error handling request: {}", req.getRequestURI(), e);
        return ResponseEntity.internalServerError().build();
    }

    @ExceptionHandler(value = GameEngineException.class)
    public ResponseEntity gameEngineExceptionErrorHandler(final HttpServletRequest req, final GameEngineException e) throws Exception {
        log.error("{}  error handling request: {}", GameEngineException.class.getName(), req.getRequestURI(), e);
        return ResponseEntity.unprocessableEntity().body(e.getClass().getSimpleName() + ":" + e.getMessage());
    }

}
