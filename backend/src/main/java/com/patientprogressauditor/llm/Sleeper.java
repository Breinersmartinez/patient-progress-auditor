package com.patientprogressauditor.llm;

import java.time.Duration;

// La espera entre intentos, aparte para que las pruebas no esperen de verdad.
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
