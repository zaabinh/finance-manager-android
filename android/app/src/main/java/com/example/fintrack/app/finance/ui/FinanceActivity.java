package com.example.fintrack.app.finance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Shared lifecycle/error handling. All disk work stays in the repository executor. */
public abstract class FinanceActivity extends AppCompatActivity {
    protected FinanceViewModel model;
    protected FinanceBook book;
    private String owner;
    protected abstract int layout();
    protected abstract void bind(Bundle saved);
    protected abstract void render();
    protected void loading(boolean loading) { }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        owner = SessionManager.getInstance(this).getUserId();
        if (!authenticated()) return;
        setContentView(layout());
        FinanceUi.insets(findViewById(android.R.id.content));
        model = new ViewModelProvider(this).get(FinanceViewModel.class);
        bind(saved);
        model.state().observe(this, resource -> {
            loading(resource.getStatus() == Resource.Status.LOADING);
            if (resource.getData() != null) { book = resource.getData(); render(); }
            if (resource.getStatus() == Resource.Status.ERROR) {
                FinanceBook.Problem problem = FinanceBook.Problem.valueOf(resource.getMessage());
                if (problem == FinanceBook.Problem.UNAUTHORIZED) { authenticated(); return; }
                // Mutation errors have a command event; load failures offer a retry.
                if (resource.getData() == null) new MaterialAlertDialogBuilder(this)
                        .setMessage(FinanceUi.error(problem))
                        .setPositiveButton(R.string.action_retry, (dialog, which) -> model.refresh())
                        .setNegativeButton(R.string.action_back, (dialog, which) -> finish()).show();
                else com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), FinanceUi.error(problem),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setAction(R.string.action_retry, view -> model.refresh()).show();
            }
        });
        model.command().observe(this, command -> {
            if (!command.consume()) return;
            if (command.error != null) Toast.makeText(this, FinanceUi.error(command.error), Toast.LENGTH_LONG).show();
            else if (command.complete) {
                Toast.makeText(this, R.string.finance_saved, Toast.LENGTH_SHORT).show();
                completed();
            }
        });
    }
    protected void completed() { setResult(RESULT_OK); finish(); }
    private boolean authenticated() {
        SessionManager session = SessionManager.getInstance(this);
        if (session.isLoggedIn() && session.getUserId() != null && !session.getUserId().trim().isEmpty()
                && java.util.Objects.equals(owner, session.getUserId())) return true;
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); return false;
    }
    @Override protected void onResume() { super.onResume(); if (authenticated() && model != null) model.refresh(); }
    protected void toolbar(int id) { ((MaterialToolbar) findViewById(id)).setNavigationOnClickListener(view -> finish()); }
    protected void text(int id, CharSequence value) { ((TextView) findViewById(id)).setText(value); }
    protected void visible(int id, boolean value) { findViewById(id).setVisibility(value ? View.VISIBLE : View.GONE); }
    protected void confirmDelete(Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.finance_delete_title)
                .setMessage(R.string.finance_delete_body).setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> action.run()).show();
    }
}
