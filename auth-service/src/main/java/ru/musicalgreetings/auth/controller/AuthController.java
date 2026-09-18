package ru.musicalgreetings.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.musicalgreetings.auth.data.LoginResponse;
import ru.musicalgreetings.auth.data.RefreshResponse;
import ru.musicalgreetings.auth.service.AuthService;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping ("/auth/anonymous")
    @ResponseStatus(HttpStatus.CREATED)
    LoginResponse authAnonymous(){
        return authService.createAnonymousSession();
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
        authService.logout(refreshToken);
    }

    @PostMapping ("/auth/refresh")
    @ResponseStatus(HttpStatus.CREATED)
    RefreshResponse refreshJwt(@RequestHeader("X-Refresh-Token") String refreshToken){
        return authService.refreshJwt(refreshToken);
    }
}
