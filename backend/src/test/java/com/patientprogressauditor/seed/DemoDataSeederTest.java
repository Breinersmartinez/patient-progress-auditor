package com.patientprogressauditor.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.seed.demo=true")
class DemoDataSeederTest {

    private static final String SEED_ALIASES = "('P-014','P-021','P-007','P-033','P-040')";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private TransactionTemplate transaction;
    @Autowired private ObjectMapper mapper;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private Map<String, Integer> snapshot() {
        return Map.of(
                "users", count("SELECT count(*) FROM app_users WHERE is_demo"),
                "user_roles", count("SELECT count(*) FROM app_user_roles"),
                "patients", count("SELECT count(*) FROM patients WHERE alias IN " + SEED_ALIASES),
                "entries", count("SELECT count(*) FROM entries"),
                "events", count("SELECT count(*) FROM entry_events"));
    }

    @Test
    void loadsTheSyntheticSetOfD11() {
        assertThat(count("SELECT count(*) FROM app_users u JOIN app_user_roles ur ON ur.user_id = u.id"
                + " JOIN app_roles r ON r.id = ur.role_id WHERE u.is_demo AND r.code = 'ENFERMERIA'")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM app_users u JOIN app_user_roles ur ON ur.user_id = u.id"
                + " JOIN app_roles r ON r.id = ur.role_id WHERE u.is_demo AND r.code = 'MEDICO'")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM app_users WHERE is_demo AND password_hash IS NOT NULL")).isZero();
        assertThat(count("SELECT count(*) FROM patients WHERE alias IN " + SEED_ALIASES)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM (SELECT DISTINCT p.id, date_trunc('week', e.entry_date)"
                + " FROM entries e JOIN patients p ON p.id = e.patient_id WHERE p.alias IN " + SEED_ALIASES
                + ") weeks")).isEqualTo(18);
        assertThat(count("SELECT count(*) FROM entries e JOIN patients p ON p.id = e.patient_id"
                + " WHERE p.alias IN " + SEED_ALIASES)).isBetween(115, 125);
    }

    @Test
    void lastWeekEndsOnSunday25October2026AndAllEntriesAreAt1800Bogota() {
        assertThat(jdbc.queryForObject("SELECT max(entry_date)::text FROM entries", String.class))
                .isEqualTo("2026-10-25");
        assertThat(count("SELECT count(*) FROM entries WHERE (created_at AT TIME ZONE 'America/Bogota')::time"
                + " <> TIME '18:00'")).isZero();
    }

    @Test
    void p014HasNauseaInWeeks1To5AsTheHandCountSays() {
        // dias con nauseas por semana: 2, 0, 3, 0, 4; las semanas 2 y 4 no tienen filas
        assertThat(jdbc.queryForList(
                "SELECT count(DISTINCT e.entry_date) FROM entries e JOIN patients p ON p.id = e.patient_id"
                        + " JOIN entry_events ev ON ev.entry_id = e.id AND ev.symptom = 'NAUSEAS'"
                        + " WHERE p.alias = 'P-014' GROUP BY date_trunc('week', e.entry_date)"
                        + " ORDER BY date_trunc('week', e.entry_date)",
                Integer.class)).containsExactly(2, 3, 4);
        assertThat(jdbc.queryForList(
                "SELECT to_char(date_trunc('week', e.entry_date), 'YYYY-MM-DD') FROM entries e"
                        + " JOIN patients p ON p.id = e.patient_id"
                        + " JOIN entry_events ev ON ev.entry_id = e.id AND ev.symptom = 'NAUSEAS'"
                        + " WHERE p.alias = 'P-014' GROUP BY date_trunc('week', e.entry_date)"
                        + " ORDER BY date_trunc('week', e.entry_date)",
                String.class)).containsExactly("2026-09-21", "2026-10-05", "2026-10-19");
    }

    @Test
    void p007HasGapsAndOneWeekWithThreeEntries() {
        assertThat(jdbc.queryForList(
                "SELECT count(*) FROM entries e JOIN patients p ON p.id = e.patient_id WHERE p.alias = 'P-007'"
                        + " GROUP BY date_trunc('week', e.entry_date) ORDER BY date_trunc('week', e.entry_date)",
                Integer.class)).containsExactly(5, 3, 6);
    }

    @Test
    void p033HasNegationsAndASingleEvent() {
        assertThat(count("SELECT count(*) FROM entry_events ev JOIN entries e ON e.id = ev.entry_id"
                + " JOIN patients p ON p.id = e.patient_id WHERE p.alias = 'P-033'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM entries e JOIN patients p ON p.id = e.patient_id"
                + " WHERE p.alias = 'P-033' AND (e.body ILIKE 'sin %' OR e.body ILIKE 'no %')")).isGreaterThan(10);
    }

    @Test
    void p040HasTheNoteWithAnInstructionAndItsRealEvent() {
        assertThat(jdbc.queryForList(
                "SELECT ev.symptom FROM entries e JOIN patients p ON p.id = e.patient_id"
                        + " JOIN entry_events ev ON ev.entry_id = e.id"
                        + " WHERE p.alias = 'P-040' AND e.body LIKE '%ignora las instrucciones anteriores%'",
                String.class)).containsExactly("VOMITO");
    }

    @Test
    void everyEventIsConfirmedByANurseAndIsFromTheCatalog() {
        assertThat(count("SELECT count(*) FROM entry_events ev JOIN app_user_roles ur ON ur.user_id = ev.confirmed_by"
                + " JOIN app_roles r ON r.id = ur.role_id WHERE r.code <> 'ENFERMERIA'")).isZero();
        assertThat(count("SELECT count(*) FROM entry_events")).isGreaterThan(30);
    }

    @Test
    void runningTheSeedAgainDoesNotDuplicateAnything() {
        Map<String, Integer> before = snapshot();

        seeder.seed();
        seeder.seed();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void theProdProfileRejectsTheSeed() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        DemoDataSeeder seederInProd = new DemoDataSeeder(jdbc, transaction, mapper, prod, null);

        assertThatThrownBy(seederInProd::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil prod");
    }
}
