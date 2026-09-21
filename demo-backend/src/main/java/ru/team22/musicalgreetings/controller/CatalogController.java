package ru.team22.musicalgreetings.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    @GetMapping("/holidays")
    public List<String> listHolidays(){
        return List.of("new year", "birthday");
    }

    @GetMapping("/holidays/{holidayId}")
    public String getHoliday(@PathVariable("holidayId") int holidayId){
        switch (holidayId){
            case 1 -> {
                return "new year";
            }
            case 2 -> {
                return "birthday";
            }
            default -> {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Праздник не найден");
            }
        }
    }


    @GetMapping("/music-types")
    public List<String> musicType(){
        return List.of("jazz", "rock");
    }
}
