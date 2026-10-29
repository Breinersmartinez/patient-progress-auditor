package com.patientprogressauditor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;

// Aprobar y editar a la vez (sin transaccion de prueba: cada peticion confirma por su cuenta). La fila del informe se
// bloquea en las dos operaciones, asi que o la edicion entra antes y se aprueba SU version, o la aprobacion entra antes y
// la edicion recibe 409. Nunca queda aprobada una version y creada otra despues.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class ReportConcurrencyTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;

    private ReportFixtures fx;
    private UUID patient;

    @AfterEach
    void clean() {
        if (patient != null) {
            fx.delete(patient);
        }
    }

    @Test
    void approvingAndEditingAtTheSameTimeNeverLeavesAnApprovedVersionThatIsNotTheLastOne() throws Exception {
        fx = new ReportFixtures(jdbc, entries, mapper);
        patient = fx.patient("P-033");
        fx.delete(patient);
        List<Entry> week = fx.weekEntries(patient, WEEK);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int editedFirst = 0;
        int approvedFirst = 0;
        try {
            for (int round = 0; round < 12; round++) {
                UUID report = fx.report(patient, WEEK, "PENDIENTE_REVISION");
                String edit = mapper.createObjectNode().put("summary", "Resumen de la ronda " + round)
                        .<com.fasterxml.jackson.databind.node.ObjectNode>set("claims", mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("claimKey", "c1").put("text", "Las náuseas se registraron en 3 días de la semana.")
                                .put("criterion", "Texto de los registros de la semana")
                                .<com.fasterxml.jackson.databind.node.ObjectNode>set("entryIds", mapper.createArrayNode().add(week.get(0).getId().toString()))))
                        .toString();
                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> edited = pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(put("/api/reports/" + report + "/content").header("X-Demo-User", ReportFixtures.NURSE)
                            .contentType(MediaType.APPLICATION_JSON).content(edit)).andReturn().getResponse().getStatus();
                });
                Future<Integer> approved = pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(post("/api/reports/" + report + "/approve").header("X-Demo-User", ReportFixtures.NURSE))
                            .andReturn().getResponse().getStatus();
                });
                go.countDown();
                int editStatus = edited.get(20, TimeUnit.SECONDS);
                int approveStatus = approved.get(20, TimeUnit.SECONDS);

                assertThat(approveStatus).as("la aprobacion siempre entra").isEqualTo(200);
                assertThat(editStatus).as("la edicion entra antes (200) o recibe 409").isIn(200, 409);
                int last = jdbc.queryForObject("SELECT max(version_no) FROM report_versions WHERE report_id = ?", Integer.class, report);
                int approvedVersion = jdbc.queryForObject("SELECT approved_version_no FROM reports WHERE id = ?", Integer.class, report);
                assertThat(approvedVersion).as("ronda %d", round).isEqualTo(last);
                assertThat(last).isEqualTo(editStatus == 200 ? 2 : 1);
                if (editStatus == 200) {
                    editedFirst++;
                } else {
                    approvedFirst++;
                }
                fx.delete(patient);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("Rondas con la edicion primero: " + editedFirst + "; con la aprobacion primero: " + approvedFirst);
    }
}
