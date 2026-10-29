package com.patientprogressauditor.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.user.AppRole;

// Carga el conjunto sintetico de demostracion (D11, RF-05, RF-39). Idempotente: ids deterministas y ON CONFLICT DO NOTHING.
// No llama al LLM: los eventos vienen ya confirmados. Solo existe con app.seed.demo=true y nunca corre con el perfil prod.
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String RESOURCE = "seed/demo-data.json";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalTime ENTRY_TIME = LocalTime.of(18, 0);

    record SeedData(List<SeedUser> users, List<SeedPatient> patients) {
    }

    record SeedUser(UUID id, String key, String displayName, String email, String role) {
    }

    record SeedPatient(UUID id, String alias, String bed, Integer age, LocalDate admittedOn, List<SeedEntry> entries) {
    }

    record SeedEntry(LocalDate date, String author, String text, List<SeedEvent> events) {
    }

    record SeedEvent(Symptom symptom, Intensity intensity, Integer occurrences) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final Environment environment;

    public DemoDataSeeder(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper,
            Environment environment) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
        this.environment = environment;
    }

    // Los informes sembrados (E14) citan las entradas por estos mismos ids.
    public static UUID entryId(String alias, LocalDate date) {
        return nameBased("entry|" + alias + "|" + date);
    }

    public static UUID eventId(UUID entryId, Symptom symptom) {
        return nameBased("event|" + entryId + "|" + symptom);
    }

    private static UUID nameBased(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "La semilla de demostracion (APP_SEED_DEMO=true) no puede correr con el perfil prod.");
        }
        SeedData data = read();
        transaction.executeWithoutResult(status -> insert(data));
        log.info("Semilla de demostracion aplicada: {} usuarios, {} pacientes, {} entradas.",
                data.users().size(), data.patients().size(),
                data.patients().stream().mapToInt(patient -> patient.entries().size()).sum());
    }

    private SeedData read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, SeedData.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RESOURCE, e);
        }
    }

    private void insert(SeedData data) {
        Map<String, SeedUser> usersByKey = new HashMap<>();
        for (SeedUser user : data.users()) {
            usersByKey.put(user.key(), user);
            jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo) VALUES (?, ?, ?, TRUE)"
                    + " ON CONFLICT DO NOTHING", user.id(), user.email(), user.displayName());
            jdbc.update("INSERT INTO app_user_roles (user_id, role_id) SELECT ?, id FROM app_roles WHERE code = ?"
                    + " ON CONFLICT DO NOTHING", user.id(), user.role());
        }
        SeedUser nurse = data.users().stream().filter(user -> AppRole.NURSING.equals(user.role())).findFirst()
                .orElseThrow();

        List<Object[]> patients = new ArrayList<>();
        List<Object[]> entries = new ArrayList<>();
        List<Object[]> events = new ArrayList<>();
        for (SeedPatient patient : data.patients()) {
            patients.add(new Object[] { patient.id(), patient.alias(), patient.bed(), patient.age(),
                    patient.admittedOn() });
            for (SeedEntry entry : patient.entries()) {
                SeedUser author = usersByKey.get(entry.author());
                OffsetDateTime createdAt = entry.date().atTime(ENTRY_TIME).atZone(BOGOTA).toOffsetDateTime();
                UUID entryId = entryId(patient.alias(), entry.date());
                entries.add(new Object[] { entryId, patient.id(), entry.date(), entry.text(), author.id(), createdAt });
                // Los eventos los confirma una enfermera: la autora si lo es, y si no, la primera.
                SeedUser confirmer = AppRole.NURSING.equals(author.role()) ? author : nurse;
                for (SeedEvent event : entry.events()) {
                    events.add(new Object[] { eventId(entryId, event.symptom()), entryId, event.symptom().name(),
                            event.intensity().name(), event.occurrences(), confirmer.id(), createdAt });
                }
            }
        }
        jdbc.batchUpdate("INSERT INTO patients (id, alias, bed, age, admitted_on) VALUES (?, ?, ?, ?, ?)"
                + " ON CONFLICT DO NOTHING", patients);
        jdbc.batchUpdate("INSERT INTO entries (id, patient_id, entry_date, body, author_id, source, structuring_status,"
                + " created_at) VALUES (?, ?, ?, ?, ?, 'TEXTO', 'ESTRUCTURADA', ?) ON CONFLICT DO NOTHING", entries);
        jdbc.batchUpdate("INSERT INTO entry_events (id, entry_id, event_type, symptom, intensity, occurrences, origin,"
                + " confirmed_by, confirmed_at) VALUES (?, ?, 'SINTOMA', ?, ?, ?, 'IA', ?, ?)"
                + " ON CONFLICT DO NOTHING", events);
    }
}
