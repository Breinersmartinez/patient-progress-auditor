package com.patientprogressauditor.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// La matriz esta escrita a mano desde docs/DICCIONARIO_DATOS_M2.md 3.4: si la base y el documento se desvian, falla.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class PermissionMatrixTest {

    static final String MARCELA = "20000000-0000-0000-0000-000000000001";
    static final String CAMILO = "20000000-0000-0000-0000-000000000002";
    static final String ANDRES = "20000000-0000-0000-0000-000000000003";
    static final String JULIAN = "20000000-0000-0000-0000-000000000004";

    private static final List<String> ALL_PERMISSIONS = List.of(
            "PATIENTS_READ", "PATIENTS_CREATE", "PATIENTS_UPDATE",
            "ENTRIES_READ", "ENTRIES_CREATE", "ENTRIES_UPDATE",
            "REPORTS_READ", "REPORTS_REQUEST", "REPORTS_CREATE_DRAFT", "REPORTS_UPDATE", "REPORTS_APPROVE");

    private static final Set<String> NURSING = Set.of(
            "PATIENTS_READ", "PATIENTS_CREATE", "PATIENTS_UPDATE",
            "ENTRIES_READ", "ENTRIES_CREATE", "ENTRIES_UPDATE",
            "REPORTS_READ", "REPORTS_REQUEST", "REPORTS_UPDATE", "REPORTS_APPROVE");

    private static final Set<String> DOCTOR = Set.of("PATIENTS_READ", "ENTRIES_READ", "ENTRIES_CREATE", "REPORTS_READ");

    @Autowired
    private MockMvc mockMvc;

    private Map<String, Boolean> allowedFor(String userId) throws Exception {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String permission : ALL_PERMISSIONS) {
            int status = mockMvc.perform(get("/api/test-probe/" + permission).header("X-Demo-User", userId))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("%s con %s", permission, userId).isIn(200, 403);
            result.put(permission, status == 200);
        }
        return result;
    }

    private static Map<String, Boolean> expected(Set<String> granted) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        ALL_PERMISSIONS.forEach(permission -> result.put(permission, granted.contains(permission)));
        return result;
    }

    @Test
    void nursesCanDoExactlyWhatTheMatrixSays() throws Exception {
        assertThat(allowedFor(MARCELA)).isEqualTo(expected(NURSING));
        assertThat(allowedFor(CAMILO)).isEqualTo(expected(NURSING));
    }

    @Test
    void doctorsCanDoExactlyWhatTheMatrixSays() throws Exception {
        assertThat(allowedFor(ANDRES)).isEqualTo(expected(DOCTOR));
        assertThat(allowedFor(JULIAN)).isEqualTo(expected(DOCTOR));
    }

    @Test
    void onlyNursingCanApproveAndTheAgentPermissionBelongsToNobodyWhoLogsIn() throws Exception {
        assertThat(allowedFor(MARCELA)).containsEntry("REPORTS_APPROVE", true).containsEntry("REPORTS_CREATE_DRAFT", false);
        assertThat(allowedFor(ANDRES)).containsEntry("REPORTS_APPROVE", false).containsEntry("REPORTS_CREATE_DRAFT", false);
    }
}
