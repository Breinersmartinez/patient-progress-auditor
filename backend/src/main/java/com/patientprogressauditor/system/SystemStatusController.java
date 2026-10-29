package com.patientprogressauditor.system;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    // La fecha de hoy la decide el servidor, en America/Bogota (D10, RN-06); el navegador no.
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public record SystemStatus(String status, String database, LocalDate today) {
    }

    private final JdbcTemplate jdbc;

    public SystemStatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatus> status() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new SystemStatus("UP", "UP", LocalDate.now(BOGOTA)));
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(new SystemStatus("DOWN", "DOWN", null));
        }
    }
}
