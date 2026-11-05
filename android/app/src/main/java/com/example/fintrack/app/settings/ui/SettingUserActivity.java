package com.example.fintrack.app.settings.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

public class SettingUserActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPhone;
    private TextView tvDisplayName, tvDisplayEmail, tvAvatarInitialsHero;
    private TextView btnEdit;
    private Button btnSaveProfile, btnCancelEdit;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_user);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();
        loadUserData();
        setupObservers();
        setupListeners();

        // Fetch latest profile from server / ViewModel
        userViewModel.fetchProfile();
    }

    private void initViews() {
        btnEdit = findViewById(R.id.btnEdit);
        tvDisplayName = findViewById(R.id.tvDisplayName);
        tvDisplayEmail = findViewById(R.id.tvDisplayEmail);
        tvAvatarInitialsHero = findViewById(R.id.tvAvatarInitialsHero);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
    }

    private void loadUserData() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();

        if (name != null) {
            etFullName.setText(name);
            if (tvDisplayName != null) {
                tvDisplayName.setText(name);
            }
            updateInitials(name);
        }
        if (email != null) {
            etEmail.setText(email);
            if (tvDisplayEmail != null) {
                tvDisplayEmail.setText(email);
            }
        }
        etEmail.setEnabled(false);
    }

    private void updateInitials(String name) {
        if (tvAvatarInitialsHero != null && !TextUtils.isEmpty(name)) {
            String[] parts = name.trim().split("\\s+");
            String initials;
            if (parts.length >= 2) {
                initials = (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
            } else {
                initials = name.substring(0, Math.min(2, name.length())).toUpperCase();
            }
            tvAvatarInitialsHero.setText(initials);
        }
    }

    private void setupObservers() {
        // Observer for fetching profile
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getName();
                String fetchedEmail = resource.getData().getEmail();
                String fetchedPhone = resource.getData().getPhone();

                if (!TextUtils.isEmpty(fetchedName)) {
                    etFullName.setText(fetchedName);
                    if (tvDisplayName != null) tvDisplayName.setText(fetchedName);
                    updateInitials(fetchedName);
                    sessionManager.saveUserProfile(
                            sessionManager.getUserId(),
                            sessionManager.getUserEmail(),
                            fetchedName
                    );
                }
                if (!TextUtils.isEmpty(fetchedEmail)) {
                    etEmail.setText(fetchedEmail);
                    if (tvDisplayEmail != null) tvDisplayEmail.setText(fetchedEmail);
                }
                if (!TextUtils.isEmpty(fetchedPhone)) {
                    etPhone.setText(fetchedPhone);
                }
            }
        });

        // Observer for updating profile
        userViewModel.getUpdateProfileResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setText(R.string.btn_save_changes);
                    Toast.makeText(this, R.string.profile_updated_success, Toast.LENGTH_SHORT).show();

                    if (resource.getData() != null) {
                        String updatedName = resource.getData().getName();
                        if (!TextUtils.isEmpty(updatedName)) {
                            sessionManager.saveUserProfile(
                                    sessionManager.getUserId(),
                                    sessionManager.getUserEmail(),
                                    updatedName
                            );
                            if (tvDisplayName != null) tvDisplayName.setText(updatedName);
                            updateInitials(updatedName);
                        }
                    }
                    toggleEditMode(false);
                    break;

                case ERROR:
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setText(R.string.btn_save_changes);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.error_server);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnSaveProfile.setEnabled(false);
                    btnSaveProfile.setText(getString(R.string.loading_processing));
                    break;
            }
        });
    }

    private void setupListeners() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        btnEdit.setOnClickListener(v -> toggleEditMode(true));

        btnCancelEdit.setOnClickListener(v -> {
            loadUserData();
            toggleEditMode(false);
        });

        btnSaveProfile.setOnClickListener(v -> {
            String newName = etFullName.getText().toString().trim();
            String newPhone = etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(newName)) {
                etFullName.setError(getString(R.string.error_required_field));
                return;
            }

            userViewModel.updateProfile(newName, newPhone);
        });
    }

    private void toggleEditMode(boolean enable) {
        etFullName.setEnabled(enable);
        etPhone.setEnabled(enable);

        if (enable) {
            btnSaveProfile.setVisibility(View.VISIBLE);
            btnCancelEdit.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.GONE);
        } else {
            btnSaveProfile.setVisibility(View.GONE);
            btnCancelEdit.setVisibility(View.GONE);
            btnEdit.setVisibility(View.VISIBLE);
        }
    }
}
