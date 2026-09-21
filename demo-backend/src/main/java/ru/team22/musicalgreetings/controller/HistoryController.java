package ru.team22.musicalgreetings.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.team22.musicalgreetings.security.AuthenticatedUser;
import ru.team22.musicalgreetings.DemoResponse;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    @GetMapping("/congrats/history")
    public DemoResponse congratsHistory(@AuthenticationPrincipal AuthenticatedUser user){
        return new DemoResponse(
                user,
                "Запрос прошёл через gateway, пользователь распознан"
        );
    }
}
