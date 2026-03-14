package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.SessionIdGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.example.eventhub.config.AppConfig;

@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionIdGenerator sidGenerator;
    private final AppConfig appConfig;

    @PostMapping("/session")
    public ResponseEntity<Void> session(
            @CookieValue(name = "X-Session-Id", required = false) String sid) {

        String currentSid = sid;
        HttpStatus status = HttpStatus.OK;

        if (currentSid != null && !currentSid.matches("^[a-f0-9]{32}$")) {
            currentSid = null;
        }

        if (currentSid == null || !sessionService.exists(currentSid)) {
            currentSid = sidGenerator.generateSid();
            sessionService.createSession(currentSid);
            status = HttpStatus.CREATED;
        } else {
            sessionService.updateSession(currentSid);
        }

        ResponseCookie cookie = ResponseCookie.from("X-Session-Id", currentSid)
                .httpOnly(true)
                .path("/")
                .maxAge(appConfig.getUserSessionTtl())
                .build();

        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

}