package com.patientprogressauditor.run;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.patientprogressauditor.agent.AgentProperties;

import jakarta.annotation.PreDestroy;

// El ejecutor acotado en el proceso (D24): sin broker ni cola externa. Pocos hilos (la cuota gratuita del proveedor no da
// para mas) y una cola corta: lleno, rechaza en lugar de acumular esperas que nadie ve.
// No es un bean `Executor`: Spring Boot dejaria de crear el suyo para la aplicacion.
@Component
public class RunExecutor {

    private final ThreadPoolExecutor pool;

    public RunExecutor(AgentProperties properties) {
        BlockingQueue<Runnable> queue = properties.getQueueCapacity() == 0
                ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(properties.getQueueCapacity());
        this.pool = new ThreadPoolExecutor(properties.getExecutorThreads(), properties.getExecutorThreads(), 0, TimeUnit.SECONDS,
                queue, task -> {
                    Thread thread = new Thread(task, "report-run");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    // RejectedExecutionException si los hilos y la cola estan llenos.
    public void submit(Runnable task) throws RejectedExecutionException {
        pool.execute(task);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
