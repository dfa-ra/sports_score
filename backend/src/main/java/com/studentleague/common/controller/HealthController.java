package com.studentleague.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Liveness probe")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
