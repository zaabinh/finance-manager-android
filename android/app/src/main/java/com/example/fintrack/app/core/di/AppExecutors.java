package com.example.fintrack.app.core.di;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** The app's threads. Tests pass direct (synchronous) executors through the constructor. */
public final class AppExecutors {
    private final Executor io;
    private final Executor serial;
    private final Executor computation;
    private final Executor main;

    public AppExecutors(Executor io, Executor serial, Executor computation, Executor main) {
        this.io = io;
        this.serial = serial;
        this.computation = computation;
        this.main = main;
    }

    /** Production executors. Threads are only started on first use. */
    public static AppExecutors create() {
        Handler handler = new Handler(Looper.getMainLooper());
        return new AppExecutors(Executors.newFixedThreadPool(4), Executors.newSingleThreadExecutor(),
                Executors.newSingleThreadExecutor(), handler::post);
    }

    /** Background reads and blocking network calls that may run concurrently. */
    public Executor io() { return io; }

    /** One thread, in submission order. Every MockFinanceStore read and write runs here, so its file is never read mid-write. */
    public Executor serial() { return serial; }

    /** CPU work such as receipt/OCR parsing. */
    public Executor computation() { return computation; }

    /** Main-thread delivery; always posts, even when called from the main thread. */
    public Executor main() { return main; }
}
