package com.studentleague.sports.controller;

import com.studentleague.sports.dto.SportResponse;
import com.studentleague.sports.service.SportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sports")
@Tag(name = "Sports")
@SecurityRequirement(name = "bearerAuth")
public class SportController {

    private final SportService sportService;

    public SportController(SportService sportService) {
        this.sportService = sportService;
    }

    @GetMapping
    @Operation(summary = "List supported sports")
    public List<SportResponse> list() {
        return sportService.listSports();
    }
}
