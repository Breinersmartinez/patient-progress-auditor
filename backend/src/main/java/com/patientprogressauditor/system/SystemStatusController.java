package com.patientprogressauditor.system;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    public record SystemStatus(String status, String database, LocalDate today) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SystemStatusController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatus> status() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new SystemStatus("UP", "UP", LocalDate.now(clock)));
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(new SystemStatus("DOWN", "DOWN", null));
        }
    }
}
