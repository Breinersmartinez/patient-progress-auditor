package com.patientprogressauditor.run;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;

// El ejecutor vive en el proceso: si el backend se reinicia, lo que corria muere sin avisar. Al arrancar, toda ejecucion que
// quedo en cola o en curso se marca como interrumpida, para que la pantalla no espere para siempre (D24).
@Component
public class OrphanRunRecovery {

    static final String MESSAGE = "Interrumpida por un reinicio del servidor. Pida el informe de nuevo.";

    private static final Logger log = LoggerFactory.getLogger(OrphanRunRecovery.class);

    private final AgentRunRepository runs;
    private final RunTrace trace;

    public OrphanRunRecovery(AgentRunRepository runs, RunTrace trace) {
        this.runs = runs;
        this.trace = trace;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        recover();
    }

    public int recover() {
        List<AgentRun> orphans = runs.findByStatusIn(RunTrace.OPEN);
        int closed = 0;
        for (AgentRun run : orphans) {
            if (trace.failIfOpen(run.getId(), MESSAGE)) {
                closed++;
            }
        }
        if (closed > 0) {
            log.warn("{} ejecución(es) quedaron abiertas tras un reinicio y se marcaron como interrumpidas", closed);
        }
        return closed;
    }
}
