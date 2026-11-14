package com.example.fintrack.app.core.di;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public class AppExecutorsTest {
    private final List<String> log = new ArrayList<>();

    private Executor direct(String name) {
        return task -> { log.add(name); task.run(); };
    }

    @Test
    public void eachRoleRunsItsTaskOnTheExecutorThatWasSupplied() {
        AppExecutors executors = new AppExecutors(direct("io"), direct("serial"), direct("computation"), direct("main"));

        executors.io().execute(() -> log.add("read"));
        executors.serial().execute(() -> log.add("store"));
        executors.computation().execute(() -> log.add("parse"));
        executors.main().execute(() -> log.add("deliver"));

        assertEquals(Arrays.asList("io", "read", "serial", "store", "computation", "parse", "main", "deliver"), log);
    }

    @Test
    public void directExecutorsRunABackgroundTaskAndItsMainDeliveryBeforeReturning() {
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run);

        executors.serial().execute(() -> {
            log.add("work");
            executors.main().execute(() -> log.add("result"));
        });

        assertEquals(Arrays.asList("work", "result"), log);
    }
}
