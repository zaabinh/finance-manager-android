package com.example.fintrack.app.testing;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.rules.ExternalResource;

/** Lets LiveData.setValue run on the JVM test thread (there is no main Looper in unit tests). */
public final class InstantLiveData extends ExternalResource {
    @Override
    protected void before() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @Override
    protected void after() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }
}
