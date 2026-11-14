package com.example.fintrack.app.finance.ui;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceProblem;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.ui.SessionExpiry;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Lớp Activity cơ sở (Base Activity) dùng chung cho tất cả các màn hình quản lý tài chính.
 * Tự động xử lý xác thực phiên đăng nhập, vòng đời và quản lý lỗi mạng tập trung.
 */
public abstract class FinanceActivity extends AppCompatActivity {
    protected FinanceViewModel model;
    protected FinanceBook book;
    private SessionSnapshot origin; // phiên đăng nhập lúc mở màn hình

    /** Inflates the screen's ViewBinding (kept in a subclass field) and returns its root; called once from onCreate. */
    protected abstract View contentView();
    protected abstract void bind(Bundle saved); // Khởi tạo các view và sự kiện
    protected abstract void render(); // Cập nhật giao diện khi dữ liệu tài chính thay đổi
    protected void loading(boolean loading) { } // Xử lý hiệu ứng loading
    protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.REFERENCES;
    }
    protected String detailId() { return null; }

    @Override 
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        origin = FintrackApplication.container(this).sessionStore().current();
        if (!authenticated()) return; // Kiểm tra đăng nhập
        setContentView(contentView());
        FinanceUi.insets(findViewById(android.R.id.content));
        model = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory().forFinance(readScope(), detailId())).get(FinanceViewModel.class);
        bind(saved);

        // Quan sát dữ liệu tài chính (FinanceBook) từ LiveData
        model.state().observe(this, resource -> {
            loading(model.snapshotLoading() || model.saveLoading());
            if (resource.getData() != null) { book = resource.getData(); render(); }
            if (resource.getStatus() == Resource.Status.ERROR) {
                FinanceProblem problem = FinanceErrors.problem(resource.getError());
                if (problem == FinanceProblem.UNAUTHORIZED) { authenticated(); return; }
                
                // Hiển thị hộp thoại báo lỗi hoặc Snackbar thử lại
                if (resource.getData() == null) new MaterialAlertDialogBuilder(this)
                        .setMessage(FinanceUi.error(problem))
                        .setPositiveButton(R.string.action_retry, (dialog, which) -> model.refresh())
                        .setNegativeButton(R.string.action_back, (dialog, which) -> finish()).show();
                else com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), FinanceUi.error(problem),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setAction(R.string.action_retry, view -> model.refresh()).show();
            }
        });

        // Quan sát sự kiện hoàn tất lệnh (Command) thêm/sửa/xóa
        model.saveLoadingState().observe(this, value -> loading(model.snapshotLoading() || Boolean.TRUE.equals(value)));
        model.command().observe(this, event -> {
            FinanceViewModel.Command command = event.getContentIfNotHandled();
            if (command == null) return;
            loading(model.snapshotLoading() || model.saveLoading());
            onCommand(command);
            if (command.error != null) { CharSequence text = failure(command); if (text != null) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
            else if (command.complete) {
                Toast.makeText(this, R.string.finance_saved, Toast.LENGTH_SHORT).show();
                completed();
            }
        });
    }

    protected void onCommand(FinanceViewModel.Command command) { }

    protected void completed() { setResult(RESULT_OK); finish(); }

    /** Text for a failed command; screens may localise specific server codes. Null shows nothing. */
    protected CharSequence failure(FinanceViewModel.Command command) {
        // Server messages may be English or technical; always show the localized text for the problem.
        return getString(FinanceUi.error(command.error));
    }

    /** Kiểm tra phiên đăng nhập lúc mở màn hình có còn là phiên hiện tại hay không; nếu không thì chuyển về Login */
    protected final boolean authenticated() {
        if (origin.userId != null && !origin.userId.trim().isEmpty() && SessionExpiry.isCurrent(this, origin)) return true;
        SessionExpiry.redirectToLogin(this); return false;
    }

    @Override protected void onResume() { super.onResume(); if (authenticated() && model != null) model.refresh(); }

    /** Hiển thị hộp thoại xác nhận xóa dữ liệu */
    protected void confirmDelete(Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.finance_delete_title)
                .setMessage(R.string.finance_delete_body).setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> action.run()).show();
    }
}
