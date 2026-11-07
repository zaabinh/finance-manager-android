package com.example.fintrack.app.settings.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

public class SettingUserActivity extends AppCompatActivity {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final String[] TIMEZONE_IDS = {
            "Asia/Ho_Chi_Minh", "Asia/Tokyo", "Europe/London", "America/New_York"
    };
    private static final String[] TIMEZONE_LABELS = {
            "Hà Nội · GMT+07:00", "Tokyo · GMT+09:00", "London", "New York"
    };

    private EditText etDisplayName;
    private EditText etEmail;
    private TextView tvDisplayName;
    private TextView tvDisplayEmail;
    private TextView tvAvatarInitialsHero;
    private TextView tvCurrencyValue;
    private TextView tvTimezoneValue;
    private TextView btnEdit;
    private View rowTimezone;
    private View btnSaveProfile;
    private View btnCancelEdit;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;
    private String selectedTimeZone = DEFAULT_TIMEZONE;
    private String savedTimeZone = DEFAULT_TIMEZONE;
    private String savedDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_user);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();
        toggleEditMode(false);
        loadUserData();
        setupObservers();
        setupListeners();
        userViewModel.fetchProfile();
    }

    private void initViews() {
        btnEdit = findViewById(R.id.btnEdit);
        tvDisplayName = findViewById(R.id.tvDisplayName);
        tvDisplayEmail = findViewById(R.id.tvDisplayEmail);
        tvAvatarInitialsHero = findViewById(R.id.tvAvatarInitialsHero);
        etDisplayName = findViewById(R.id.etDisplayName);
        etEmail = findViewById(R.id.etEmail);
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue);
        tvTimezoneValue = findViewById(R.id.tvTimezoneValue);
        rowTimezone = findViewById(R.id.rowTimezone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
    }

    private void loadUserData() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();
        if (!TextUtils.isEmpty(name)) {
            savedDisplayName = name;
            showName(name);
        }
        if (!TextUtils.isEmpty(email)) {
            etEmail.setText(email);
            tvDisplayEmail.setText(email);
        }
        etEmail.setEnabled(false);
        showTimezone(selectedTimeZone);
    }

    private void setupObservers() {
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource == null || resource.getStatus() != Resource.Status.SUCCESS || resource.getData() == null) return;
            UserProfileResponse profile = resource.getData();
            String name = profile.getDisplayName();
            String email = profile.getEmail();
            if (!TextUtils.isEmpty(name)) {
                showName(name);
                savedDisplayName = name;
                sessionManager.saveUserProfile(profile.getId(), email, name);
            }
            if (!TextUtils.isEmpty(email)) {
                etEmail.setText(email);
                tvDisplayEmail.setText(email);
            }
            String currency = profile.getCurrencyCode();
            tvCurrencyValue.setText(TextUtils.isEmpty(currency) ? getString(R.string.currency_vnd_read_only) : currency);
            selectedTimeZone = TextUtils.isEmpty(profile.getTimeZone()) ? DEFAULT_TIMEZONE : profile.getTimeZone();
            savedTimeZone = selectedTimeZone;
            showTimezone(selectedTimeZone);
        });

        userViewModel.getUpdateProfileResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setVisibility(View.GONE);
                    btnCancelEdit.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.profile_updated_success, Toast.LENGTH_SHORT).show();
                    if (resource.getData() != null && !TextUtils.isEmpty(resource.getData().getDisplayName())) {
                        String updatedName = resource.getData().getDisplayName();
                        savedDisplayName = updatedName;
                        selectedTimeZone = resource.getData().getTimeZone();
                        savedTimeZone = selectedTimeZone;
                        showName(updatedName);
                        sessionManager.saveUserProfile(sessionManager.getUserId(), sessionManager.getUserEmail(), updatedName);
                    }
                    etDisplayName.setEnabled(false);
                    break;
                case ERROR:
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setVisibility(View.VISIBLE);
                    String message = resource.getMessage() != null ? resource.getMessage() : getString(R.string.error_server);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    break;
                case LOADING:
                    btnSaveProfile.setEnabled(false);
                    break;
            }
        });
    }

    private void setupListeners() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        btnEdit.setOnClickListener(v -> toggleEditMode(true));
        btnCancelEdit.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(savedDisplayName)) showName(savedDisplayName);
            selectedTimeZone = savedTimeZone;
            showTimezone(selectedTimeZone);
            toggleEditMode(false);
        });
        btnSaveProfile.setOnClickListener(v -> {
            String displayName = etDisplayName.getText().toString().trim();
            if (TextUtils.isEmpty(displayName)) {
                etDisplayName.setError(getString(R.string.error_required_field));
                return;
            }
            userViewModel.updateProfile(displayName, selectedTimeZone);
        });
        rowTimezone.setOnClickListener(v -> {
            if (etDisplayName.isEnabled()) showTimezonePicker();
        });
    }

    private void toggleEditMode(boolean enable) {
        etDisplayName.setEnabled(enable);
        rowTimezone.setEnabled(enable);
        btnSaveProfile.setVisibility(enable ? View.VISIBLE : View.GONE);
        btnCancelEdit.setVisibility(enable ? View.VISIBLE : View.GONE);
        btnEdit.setVisibility(enable ? View.GONE : View.VISIBLE);
    }

    private void showName(String name) {
        etDisplayName.setText(name);
        tvDisplayName.setText(name);
        updateInitials(name);
    }

    private void updateInitials(String name) {
        if (TextUtils.isEmpty(name)) return;
        String[] parts = name.trim().split("\\s+");
        String initials = parts.length > 1
                ? (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                : name.substring(0, Math.min(2, name.length()));
        tvAvatarInitialsHero.setText(initials.toUpperCase());
    }

    private void showTimezonePicker() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.timezone_picker_title)
                .setSingleChoiceItems(TIMEZONE_LABELS, selectedTimezoneIndex(), (dialog, which) -> {
                    String timezoneId = TIMEZONE_IDS[which];
                    selectedTimeZone = timezoneId;
                    showTimezone(timezoneId);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private int selectedTimezoneIndex() {
        for (int i = 0; i < TIMEZONE_IDS.length; i++) {
            if (TIMEZONE_IDS[i].equals(selectedTimeZone)) return i;
        }
        return 0;
    }

    private void showTimezone(String timezoneId) {
        for (int i = 0; i < TIMEZONE_IDS.length; i++) {
            if (TIMEZONE_IDS[i].equals(timezoneId)) {
                tvTimezoneValue.setText(TIMEZONE_LABELS[i]);
                return;
            }
        }
        tvTimezoneValue.setText(timezoneId);
    }

}
