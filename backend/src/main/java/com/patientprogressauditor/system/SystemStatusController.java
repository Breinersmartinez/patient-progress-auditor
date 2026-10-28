package com.patientprogressauditor.system;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    public record SystemStatus(String status, String database) {
    }

    private final JdbcTemplate jdbc;

    public SystemStatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatus> status() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new SystemStatus("UP", "UP"));
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(new SystemStatus("DOWN", "DOWN"));
        }
    }
}
