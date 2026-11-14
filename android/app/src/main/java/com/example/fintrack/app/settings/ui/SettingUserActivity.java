package com.example.fintrack.app.settings.ui;

import com.example.fintrack.app.core.network.ErrorMapper;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySettingUserBinding;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

public class SettingUserActivity extends AppCompatActivity {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    public static final String[] TIMEZONE_IDS = {
            "Asia/Ho_Chi_Minh", "Asia/Tokyo", "Europe/London", "America/New_York"
    };

    private ActivitySettingUserBinding binding;

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
    private UserViewModel userViewModel;
    private String selectedTimeZone = DEFAULT_TIMEZONE;
    private String savedTimeZone = DEFAULT_TIMEZONE;
    private String savedDisplayName;
    private static final String STATE_EDITING = "state_editing";
    private static final String STATE_TIME_ZONE = "state_time_zone";
    private static final String STATE_SAVED_TIME_ZONE = "state_saved_time_zone";
    private static final String STATE_SAVED_NAME = "state_saved_name";
    // Rotated mid-edit: the replayed profile result must not overwrite the name/time zone being edited.
    private boolean keepDraft = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);

        boolean editing = false;
        if (savedInstanceState != null) {
            editing = savedInstanceState.getBoolean(STATE_EDITING);
            keepDraft = editing;
            selectedTimeZone = savedInstanceState.getString(STATE_TIME_ZONE, selectedTimeZone);
            savedTimeZone = savedInstanceState.getString(STATE_SAVED_TIME_ZONE, savedTimeZone);
            savedDisplayName = savedInstanceState.getString(STATE_SAVED_NAME);
        }

        initViews();
        toggleEditMode(editing);
        loadUserData();
        setupObservers();
        setupListeners();
        userViewModel.fetchProfile();
    }

    private void initViews() {
        btnEdit = binding.btnEdit;
        tvDisplayName = binding.tvDisplayName;
        tvDisplayEmail = binding.tvDisplayEmail;
        tvAvatarInitialsHero = binding.tvAvatarInitialsHero;
        etDisplayName = binding.etDisplayName;
        etEmail = binding.etEmail;
        tvCurrencyValue = binding.tvCurrencyValue;
        tvTimezoneValue = binding.tvTimezoneValue;
        rowTimezone = binding.rowTimezone;
        btnSaveProfile = binding.btnSaveProfile;
        btnCancelEdit = binding.btnCancelEdit;
    }

    private void loadUserData() {
        SessionSnapshot session = FintrackApplication.container(this).sessionStore().current();
        String name = session.name;
        String email = session.email;
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
            boolean draft = keepDraft;
            keepDraft = false;
            if (!TextUtils.isEmpty(name) && !draft) {
                showName(name);
                savedDisplayName = name; // UserViewModel đã lưu hồ sơ này vào Session
            }
            if (!TextUtils.isEmpty(email)) {
                etEmail.setText(email);
                tvDisplayEmail.setText(email);
            }
            String currency = profile.getCurrencyCode();
            tvCurrencyValue.setText(TextUtils.isEmpty(currency) ? getString(R.string.currency_vnd_read_only) : currency);
            savedTimeZone = TextUtils.isEmpty(profile.getTimeZone()) ? DEFAULT_TIMEZONE : profile.getTimeZone();
            if (!draft) selectedTimeZone = savedTimeZone;
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
                        showName(updatedName); // UserViewModel đã lưu tên mới vào Session
                    }
                    etDisplayName.setEnabled(false);
                    break;
                case ERROR:
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setVisibility(View.VISIBLE);
                    String message = ErrorMapper.text(this, resource.getError()) != null ? ErrorMapper.text(this, resource.getError()) : getString(R.string.error_server);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    break;
                case LOADING:
                    btnSaveProfile.setEnabled(false);
                    break;
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
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

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING, etDisplayName.isEnabled());
        outState.putString(STATE_TIME_ZONE, selectedTimeZone);
        outState.putString(STATE_SAVED_TIME_ZONE, savedTimeZone);
        outState.putString(STATE_SAVED_NAME, savedDisplayName);
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
                .setSingleChoiceItems(R.array.timezone_labels, selectedTimezoneIndex(), (dialog, which) -> {
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
                tvTimezoneValue.setText(getResources().getStringArray(R.array.timezone_labels)[i]);
                return;
            }
        }
        tvTimezoneValue.setText(timezoneId);
    }

}
