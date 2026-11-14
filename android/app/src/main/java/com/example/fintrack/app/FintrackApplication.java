package com.example.fintrack.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.example.fintrack.app.core.di.AppContainer;

/** Owns the single AppContainer of the process. */
public class FintrackApplication extends Application {
    private volatile AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer container() {
        return container;
    }

    public static AppContainer container(Context context) {
        return ((FintrackApplication) context.getApplicationContext()).container();
    }

    /** Instrumentation tests swap in a container with fake dependencies before launching a screen. */
    @VisibleForTesting
    public void replaceContainer(AppContainer container) {
        this.container = container;
    }
}
