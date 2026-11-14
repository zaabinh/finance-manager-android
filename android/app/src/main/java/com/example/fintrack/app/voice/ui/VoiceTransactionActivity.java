package com.example.fintrack.app.voice.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.voice.domain.VoiceTransactionParser;
import com.example.fintrack.databinding.ActivityVoiceTransactionBinding;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Màn hình Nhập giao dịch bằng giọng nói (Voice Transaction Input).
 * Sử dụng SpeechRecognizer nhận diện tiếng Việt và chuyển đổi sang giao dịch tài chính.
 */
public class VoiceTransactionActivity extends AppCompatActivity {

    private ActivityVoiceTransactionBinding binding;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private TransactionType selectedType = TransactionType.EXPENSE;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    binding.layoutPermissionRationale.setVisibility(View.GONE);
                    startListening();
                } else {
                    binding.layoutPermissionRationale.setVisibility(View.VISIBLE);
                    binding.tvVoiceStatus.setText(R.string.voice_state_error);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initViews();
        setupListeners();
        checkPermissionAndStart();
    }

    private void initViews() {
        binding.toolbarVoice.setNavigationOnClickListener(v -> finish());
        updateTypeSegmentUi();
    }

    private void setupListeners() {
        binding.btnMicTrigger.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                checkPermissionAndStart();
            }
        });

        binding.btnGrantPermission.setOnClickListener(v ->
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO));

        binding.btnTypeExpense.setOnClickListener(v -> {
            selectedType = TransactionType.EXPENSE;
            updateTypeSegmentUi();
        });

        binding.btnTypeIncome.setOnClickListener(v -> {
            selectedType = TransactionType.INCOME;
            updateTypeSegmentUi();
        });

        binding.btnRetryVoice.setOnClickListener(v -> {
            binding.cardRecognitionResult.setVisibility(View.GONE);
            binding.tvVoiceTranscript.setVisibility(View.GONE);
            checkPermissionAndStart();
        });

        binding.btnConfirmTransaction.setOnClickListener(v -> confirmAndProceed());
    }

    private void updateTypeSegmentUi() {
        binding.btnTypeExpense.setChecked(selectedType == TransactionType.EXPENSE);
        binding.btnTypeIncome.setChecked(selectedType == TransactionType.INCOME);
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            binding.layoutPermissionRationale.setVisibility(View.GONE);
            startListening();
        } else {
            binding.layoutPermissionRationale.setVisibility(View.VISIBLE);
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show();
            return;
        }

        stopListening();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                binding.tvVoiceStatus.setText(R.string.voice_state_listening);
                startPulseAnimation();
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
                // Điều chỉnh kích thước vòng xung theo âm lượng giọng nói
                float scale = 1.0f + Math.max(0f, rmsdB / 12f);
                binding.viewPulseRingInner.setScaleX(scale);
                binding.viewPulseRingInner.setScaleY(scale);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                stopPulseAnimation();
                binding.tvVoiceStatus.setText(R.string.voice_state_processing);
                binding.pbVoiceLoading.setVisibility(View.VISIBLE);
                binding.ivMicIcon.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onError(int error) {
                isListening = false;
                stopPulseAnimation();
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);
                binding.tvVoiceStatus.setText(R.string.voice_state_error);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                stopPulseAnimation();
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognized = matches.get(0);
                    handleRecognitionSuccess(recognized);
                } else {
                    binding.tvVoiceStatus.setText(R.string.voice_state_error);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    binding.tvVoiceTranscript.setVisibility(View.VISIBLE);
                    binding.tvVoiceTranscript.setText(String.format("\"%s\"", partial.get(0)));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        stopPulseAnimation();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private void startPulseAnimation() {
        binding.viewPulseRingOuter.setVisibility(View.VISIBLE);
        binding.viewPulseRingInner.setVisibility(View.VISIBLE);

        AlphaAnimation pulse = new AlphaAnimation(0.2f, 0.8f);
        pulse.setDuration(800);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        binding.viewPulseRingOuter.startAnimation(pulse);
    }

    private void stopPulseAnimation() {
        binding.viewPulseRingOuter.clearAnimation();
        binding.viewPulseRingOuter.setVisibility(View.INVISIBLE);
        binding.viewPulseRingInner.setVisibility(View.INVISIBLE);
    }

    private void handleRecognitionSuccess(String speechText) {
        VoiceTransactionParser.ParsedResult parsed = VoiceTransactionParser.parse(speechText);
        selectedType = parsed.type;
        updateTypeSegmentUi();

        binding.tvVoiceTranscript.setVisibility(View.VISIBLE);
        binding.tvVoiceTranscript.setText(String.format("\"%s\"", parsed.rawTranscript));

        binding.etResultAmount.setText(parsed.amount > 0 ? String.valueOf(parsed.amount) : "");
        binding.etResultNote.setText(parsed.note);

        binding.cardRecognitionResult.setVisibility(View.VISIBLE);
        binding.tvVoiceStatus.setText(R.string.voice_result_title);
    }

    private void confirmAndProceed() {
        String amountStr = binding.etResultAmount.getText().toString().trim();
        long amount = 0;
        try {
            if (!amountStr.isEmpty()) amount = Long.parseLong(amountStr);
        } catch (NumberFormatException ignored) {}

        String note = binding.etResultNote.getText().toString().trim();

        Intent intent = new Intent(this, TransactionFormActivity.class);
        intent.putExtra(TransactionFormActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_AMOUNT_MINOR, amount);
        intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_TYPE, selectedType.name());
        intent.putExtra(TransactionFormActivity.EXTRA_PAYEE, note);
        intent.putExtra("transaction_note", note);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
