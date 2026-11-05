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

/**
 * Lớp Activity cơ sở (Base Activity) dùng chung cho tất cả các màn hình quản lý tài chính.
 * Tự động xử lý xác thực phiên đăng nhập, vòng đời và quản lý lỗi mạng tập trung.
 */
public abstract class FinanceActivity extends AppCompatActivity {
    protected FinanceViewModel model;
    protected FinanceBook book;
    private String owner;

    protected abstract int layout(); // Khai báo layout XML của màn hình con
    protected abstract void bind(Bundle saved); // Khởi tạo các view và sự kiện
    protected abstract void render(); // Cập nhật giao diện khi dữ liệu tài chính thay đổi
    protected void loading(boolean loading) { } // Xử lý hiệu ứng loading

    @Override 
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        owner = SessionManager.getInstance(this).getUserId();
        if (!authenticated()) return; // Kiểm tra đăng nhập
        setContentView(layout());
        FinanceUi.insets(findViewById(android.R.id.content));
        model = new ViewModelProvider(this).get(FinanceViewModel.class);
        bind(saved);

        // Quan sát dữ liệu tài chính (FinanceBook) từ LiveData
        model.state().observe(this, resource -> {
            loading(resource.getStatus() == Resource.Status.LOADING);
            if (resource.getData() != null) { book = resource.getData(); render(); }
            if (resource.getStatus() == Resource.Status.ERROR) {
                FinanceBook.Problem problem = FinanceBook.Problem.valueOf(resource.getMessage());
                if (problem == FinanceBook.Problem.UNAUTHORIZED) { authenticated(); return; }
                
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

    /** Kiểm tra xem người dùng đã đăng nhập đúng tài khoản hay chưa */
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

    /** Hiển thị hộp thoại xác nhận xóa dữ liệu */
    protected void confirmDelete(Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.finance_delete_title)
                .setMessage(R.string.finance_delete_body).setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> action.run()).show();
    }
}
