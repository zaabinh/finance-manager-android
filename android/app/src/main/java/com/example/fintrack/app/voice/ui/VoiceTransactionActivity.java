package com.example.fintrack.app.voice.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.fintrack.R;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.core.ui.HapticHelper;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.voice.domain.VoiceCategoryPredictor;
import com.example.fintrack.app.voice.domain.VoiceTransactionParser;
import com.example.fintrack.databinding.ActivityVoiceTransactionBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Màn hình Nhập giao dịch bằng giọng nói (Voice Transaction Input).
 * Tối ưu rảnh tay (Hands-Free): Đếm ngược tự lưu 3s, rung phản hồi xúc giác,
 * nhận diện ví và mốc thời gian tự nhiên, cùng các chip điều chỉnh siêu tốc 1-chạm.
 */
public class VoiceTransactionActivity extends FinanceActivity {

    private static final String STATE_TYPE = "voice_selected_type";
    private static final String STATE_WALLET = "voice_selected_wallet";
    private static final String STATE_DESTINATION = "voice_selected_destination";
    private static final String STATE_CATEGORY = "voice_selected_category";
    private static final String STATE_AMOUNT = "voice_parsed_amount";
    private static final String STATE_NOTE = "voice_parsed_note";
    private static final String STATE_TRANSCRIPT = "voice_transcript";
    private static final String STATE_RESULT_SHOWING = "voice_result_showing";
    private static final String STATE_OCCURRED_AT = "voice_occurred_at";

    private ActivityVoiceTransactionBinding binding;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isResultShowing = false;

    private TransactionType selectedType = TransactionType.EXPENSE;
    private String selectedWalletId;
    private String selectedDestinationWalletId;
    private String selectedCategoryId;

    private long parsedAmount = 0;
    private String parsedNote = "";
    private String recognizedTranscript = "";
    private long occurredAt = System.currentTimeMillis();
    private int consecutiveErrorCount = 0;

    // Bộ đếm ngược tự động lưu rảnh tay (Auto-save Countdown)
    private CountDownTimer autoSaveTimer;
    private boolean isAutoSaveActive = false;

    // Bộ yêu cầu quyền truy cập Micro
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    binding.layoutPermissionRationale.setVisibility(View.GONE);
                    startListening();
                } else {
                    binding.layoutPermissionRationale.setVisibility(View.VISIBLE);
                    binding.tvVoiceStatus.setText(R.string.voice_state_error);
                    showErrorUi(getString(R.string.voice_permission_rationale));
                }
            }
    );

    // Bộ khởi chạy nhận diện giọng nói hệ thống (fallback khi SpeechRecognizer service không khả dụng)
    private final ActivityResultLauncher<Intent> systemSpeechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                isListening = false;
                stopPulseAnimation();
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);

                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        consecutiveErrorCount = 0;
                        binding.btnSystemSpeechFromError.setVisibility(View.GONE);
                        handleRecognitionSuccess(matches);
                    } else {
                        showErrorUi(getString(R.string.voice_error_no_match));
                    }
                } else {
                    showErrorUi(getString(R.string.voice_error_no_match));
                }
            }
    );

    @Override
    protected View contentView() {
        binding = ActivityVoiceTransactionBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void bind(Bundle saved) {
        binding.toolbarVoice.setNavigationOnClickListener(v -> finish());

        if (saved != null) {
            String typeName = saved.getString(STATE_TYPE);
            if (typeName != null) {
                try { selectedType = TransactionType.valueOf(typeName); } catch (Exception ignored) {}
            }
            selectedWalletId = saved.getString(STATE_WALLET);
            selectedDestinationWalletId = saved.getString(STATE_DESTINATION);
            selectedCategoryId = saved.getString(STATE_CATEGORY);
            parsedAmount = saved.getLong(STATE_AMOUNT, 0);
            parsedNote = saved.getString(STATE_NOTE, "");
            recognizedTranscript = saved.getString(STATE_TRANSCRIPT, "");
            isResultShowing = saved.getBoolean(STATE_RESULT_SHOWING, false);
            occurredAt = saved.getLong(STATE_OCCURRED_AT, System.currentTimeMillis());
        }

        setupListeners();
        setupMicroAdjustmentChips();
        setupPresetAmountChips();

        if (isResultShowing) {
            binding.cardRecognitionResult.setVisibility(View.VISIBLE);
            binding.tvVoiceTranscript.setVisibility(View.VISIBLE);
            binding.tvVoiceTranscript.setText(String.format("\"%s\"", recognizedTranscript));
            binding.etResultAmount.setText(parsedAmount > 0 ? String.valueOf(parsedAmount) : "");
            binding.etResultNote.setText(parsedNote);
            binding.tvVoiceStatus.setText(R.string.voice_result_title);
        } else {
            checkPermissionAndStart();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        binding.btnMicTrigger.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            if (isListening) {
                stopListening();
            } else {
                checkPermissionAndStart();
            }
        });

        binding.btnGrantPermission.setOnClickListener(v ->
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO));

        binding.btnRetryOnError.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            binding.layoutVoiceError.setVisibility(View.GONE);
            checkPermissionAndStart();
        });

        binding.btnManualFromVoice.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            openManualForm(null, 0);
        });

        binding.btnTypeExpense.setOnClickListener(v -> selectType(TransactionType.EXPENSE));
        binding.btnTypeIncome.setOnClickListener(v -> selectType(TransactionType.INCOME));
        binding.btnTypeTransfer.setOnClickListener(v -> selectType(TransactionType.TRANSFER));

        binding.fieldResultCategory.setOnClickListener(v -> {
            cancelAutoSaveCountdown(true);
            if (model.saveLoading()) return;
            FinanceUi.category(this, selectedType, catId -> {
                selectedCategoryId = catId;
                renderCategoryUi();
            });
        });

        binding.fieldResultWallet.setOnClickListener(v -> {
            cancelAutoSaveCountdown(true);
            if (model.saveLoading()) return;
            FinanceUi.wallet(this, walletId -> {
                selectedWalletId = walletId;
                renderWalletUi();
            });
        });

        binding.fieldResultDestinationWallet.setOnClickListener(v -> {
            cancelAutoSaveCountdown(true);
            if (model.saveLoading()) return;
            chooseDestinationWallet();
        });

        // Chạm vào ô nhập số tiền hoặc ghi chú sẽ tự hủy đếm ngược tự lưu để người dùng tùy ý sửa
        binding.etResultAmount.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                cancelAutoSaveCountdown(true);
            }
            return false;
        });

        binding.etResultNote.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                cancelAutoSaveCountdown(true);
            }
            return false;
        });

        binding.btnConfirmTransaction.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            saveTransactionDirectly();
        });

        binding.btnEditMore.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            openEditInForm();
        });

        binding.btnRetryVoice.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            isResultShowing = false;
            binding.cardRecognitionResult.setVisibility(View.GONE);
            binding.tvVoiceTranscript.setVisibility(View.GONE);
            binding.layoutVoiceError.setVisibility(View.GONE);
            checkPermissionAndStart();
        });

        binding.btnSystemSpeechFromError.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            launchSystemSpeechRecognizer();
        });
    }

    private void setupPresetAmountChips() {
        binding.chipPreset10k.setOnClickListener(v -> applyPresetAmount(10_000));
        binding.chipPreset20k.setOnClickListener(v -> applyPresetAmount(20_000));
        binding.chipPreset30k.setOnClickListener(v -> applyPresetAmount(30_000));
        binding.chipPreset50k.setOnClickListener(v -> applyPresetAmount(50_000));
        binding.chipPreset100k.setOnClickListener(v -> applyPresetAmount(100_000));
        binding.chipPreset200k.setOnClickListener(v -> applyPresetAmount(200_000));
        binding.chipPreset500k.setOnClickListener(v -> applyPresetAmount(500_000));
    }

    private void applyPresetAmount(long amount) {
        cancelAutoSaveCountdown(false);
        updateAmountUi(amount);
        binding.layoutAmountPresets.setVisibility(View.GONE);
        binding.tvAmountMissingNotice.setVisibility(View.GONE);
        binding.layoutAmountAdjustment.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etResultAmount.getWindowToken(), 0);
        }
        startAutoSaveCountdown();
    }

    private void setupMicroAdjustmentChips() {
        // Chip cộng thêm tiền
        binding.chipAdd10k.setOnClickListener(v -> adjustAmount(10_000));
        binding.chipAdd20k.setOnClickListener(v -> adjustAmount(20_000));
        binding.chipAdd50k.setOnClickListener(v -> adjustAmount(50_000));
        binding.chipMinus10k.setOnClickListener(v -> adjustAmount(-10_000));

        // Chip nhân nghìn (+000) khi người dùng nói tắt (vd: "45" -> 45.000)
        binding.chipAdd000.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            long cur = readCurrentAmount();
            if (cur > 0 && cur <= FinanceBook.MAX_AMOUNT / 1_000L) {
                updateAmountUi(cur * 1_000L);
            }
        });

        // Chip xóa 1 số 0 ở cuối
        binding.chipRemove0.setOnClickListener(v -> {
            cancelAutoSaveCountdown(false);
            long cur = readCurrentAmount();
            if (cur >= 10) {
                updateAmountUi(cur / 10L);
            }
        });
    }

    private void adjustAmount(long delta) {
        cancelAutoSaveCountdown(false);
        long cur = readCurrentAmount();
        long updated = Math.max(0, cur + delta);
        if (updated <= FinanceBook.MAX_AMOUNT) {
            updateAmountUi(updated);
        }
    }

    private long readCurrentAmount() {
        String str = binding.etResultAmount.getText().toString().trim();
        try {
            return !str.isEmpty() ? Long.parseLong(str) : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void updateAmountUi(long val) {
        parsedAmount = val;
        binding.etResultAmount.setText(val > 0 ? String.valueOf(val) : "");
        binding.etResultAmount.setSelection(binding.etResultAmount.getText().length());
    }

    private void selectType(TransactionType type) {
        cancelAutoSaveCountdown(true);
        if (selectedType == type) return;
        selectedType = type;
        if (selectedType != TransactionType.TRANSFER && book != null) {
            Category predicted = VoiceCategoryPredictor.predictCategory(
                    book.categories(), selectedType, binding.etResultNote.getText().toString(), recognizedTranscript);
            selectedCategoryId = predicted != null ? predicted.id : null;
        } else {
            selectedCategoryId = null;
        }
        render();
    }

    private void chooseDestinationWallet() {
        if (book == null || model.saveLoading()) return;
        List<Wallet> available = new ArrayList<>();
        for (Wallet w : book.wallets()) {
            if (w.archivedAt == null && !w.id.equals(selectedWalletId)) {
                available.add(w);
            }
        }
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.finance_wallet_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] names = new CharSequence[available.size()];
        for (int i = 0; i < available.size(); i++) names[i] = available.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.transfer_destination_wallet_label)
                .setItems(names, (dialog, which) -> {
                    selectedDestinationWalletId = available.get(which).id;
                    renderWalletUi();
                })
                .show();
    }

    @Override
    protected void render() {
        updateTypeSegmentUi();

        if (book == null) return;

        // Tự động chọn ví thanh toán mặc định nếu chưa chọn
        if (selectedWalletId == null && !book.wallets().isEmpty()) {
            for (Wallet w : book.wallets()) {
                if (w.archivedAt == null) {
                    selectedWalletId = w.id;
                    break;
                }
            }
        }

        // Tự động chọn ví nhận (chuyển khoản) nếu chưa chọn
        if (selectedDestinationWalletId == null && book.wallets().size() > 1) {
            for (Wallet w : book.wallets()) {
                if (w.archivedAt == null && !w.id.equals(selectedWalletId)) {
                    selectedDestinationWalletId = w.id;
                    break;
                }
            }
        }

        // Tự động gợi ý danh mục nếu chưa có
        if (selectedCategoryId == null && selectedType != TransactionType.TRANSFER) {
            Category predicted = VoiceCategoryPredictor.predictCategory(
                    book.categories(), selectedType, parsedNote, recognizedTranscript);
            if (predicted != null) {
                selectedCategoryId = predicted.id;
            }
        }

        renderCategoryUi();
        renderWalletUi();

        boolean isTransfer = selectedType == TransactionType.TRANSFER;
        binding.fieldResultCategory.setVisibility(isTransfer ? View.GONE : View.VISIBLE);
        binding.scrollCategoryChips.setVisibility(isTransfer ? View.GONE : View.VISIBLE);
        binding.fieldResultDestinationWallet.setVisibility(isTransfer ? View.VISIBLE : View.GONE);
        binding.tvResultWalletLabel.setText(isTransfer ? R.string.voice_source_wallet_label : R.string.voice_wallet_label);
    }

    private void updateTypeSegmentUi() {
        binding.btnTypeExpense.setChecked(selectedType == TransactionType.EXPENSE);
        binding.btnTypeIncome.setChecked(selectedType == TransactionType.INCOME);
        binding.btnTypeTransfer.setChecked(selectedType == TransactionType.TRANSFER);
    }

    private void renderCategoryUi() {
        if (book == null || selectedCategoryId == null) {
            binding.tvResultCategoryValue.setText("");
            binding.ivResultCategoryIcon.setImageResource(R.drawable.ic_category);
            binding.scrollCategoryChips.setVisibility(View.GONE);
            return;
        }
        Category cat = book.category(selectedCategoryId);
        if (cat != null) {
            binding.tvResultCategoryValue.setText(cat.name);
            CategoryIcons.paint(binding.ivResultCategoryIcon, cat);
        } else {
            binding.tvResultCategoryValue.setText("");
            binding.ivResultCategoryIcon.setImageResource(R.drawable.ic_category);
        }

        // Hiển thị 3 chip danh mục thay thế gợi ý 1-chạm
        renderCategoryAlternativeChips();
    }

    private void renderCategoryAlternativeChips() {
        if (book == null || selectedType == TransactionType.TRANSFER) {
            binding.scrollCategoryChips.setVisibility(View.GONE);
            return;
        }

        List<Category> alts = new ArrayList<>();
        for (Category c : book.categories()) {
            if (c.type == selectedType && c.archivedAt == null && !c.id.equals(selectedCategoryId)) {
                alts.add(c);
                if (alts.size() >= 3) break;
            }
        }

        if (alts.isEmpty()) {
            binding.scrollCategoryChips.setVisibility(View.GONE);
            return;
        }

        binding.scrollCategoryChips.setVisibility(View.VISIBLE);
        setupCategoryChip(binding.chipCategory1, alts.size() > 0 ? alts.get(0) : null);
        setupCategoryChip(binding.chipCategory2, alts.size() > 1 ? alts.get(1) : null);
        setupCategoryChip(binding.chipCategory3, alts.size() > 2 ? alts.get(2) : null);
    }

    private void setupCategoryChip(TextView chipView, Category category) {
        if (category == null) {
            chipView.setVisibility(View.GONE);
            return;
        }
        chipView.setVisibility(View.VISIBLE);
        chipView.setText(category.name);
        chipView.setOnClickListener(v -> {
            cancelAutoSaveCountdown(true);
            selectedCategoryId = category.id;
            renderCategoryUi();
        });
    }

    private void renderWalletUi() {
        if (book == null) return;
        Wallet source = selectedWalletId != null ? book.wallet(selectedWalletId) : null;
        binding.tvResultWalletValue.setText(source != null ? source.name : "");

        Wallet dest = selectedDestinationWalletId != null ? book.wallet(selectedDestinationWalletId) : null;
        binding.tvResultDestinationWalletValue.setText(dest != null ? dest.name : "");
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
        binding.layoutVoiceError.setVisibility(View.GONE);

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            launchSystemSpeechRecognizer();
            return;
        }

        stopListening();

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception e) {
            launchSystemSpeechRecognizer();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                binding.tvVoiceStatus.setText(R.string.voice_state_listening);
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);
                startPulseAnimation();
                HapticHelper.tick(VoiceTransactionActivity.this);
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
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
                HapticHelper.doubleTick(VoiceTransactionActivity.this);
            }

            @Override
            public void onError(int error) {
                isListening = false;
                stopPulseAnimation();
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);

                consecutiveErrorCount++;
                if (consecutiveErrorCount >= 2) {
                    binding.btnSystemSpeechFromError.setVisibility(View.VISIBLE);
                } else {
                    binding.btnSystemSpeechFromError.setVisibility(View.GONE);
                }

                String errorMsg = getErrorMessage(error);
                showErrorUi(errorMsg);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                stopPulseAnimation();
                binding.pbVoiceLoading.setVisibility(View.GONE);
                binding.ivMicIcon.setVisibility(View.VISIBLE);

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    consecutiveErrorCount = 0;
                    binding.btnSystemSpeechFromError.setVisibility(View.GONE);
                    handleRecognitionSuccess(matches);
                } else {
                    consecutiveErrorCount++;
                    if (consecutiveErrorCount >= 2) {
                        binding.btnSystemSpeechFromError.setVisibility(View.VISIBLE);
                    }
                    showErrorUi(getString(R.string.voice_error_no_match));
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

    private void launchSystemSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_input_subtitle));
        try {
            systemSpeechLauncher.launch(intent);
        } catch (Exception e) {
            showErrorUi(getString(R.string.voice_error_not_available));
        }
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
        handleRecognitionSuccess(Collections.singletonList(speechText));
    }

    private void handleRecognitionSuccess(List<String> candidates) {
        VoiceTransactionParser.ParsedResult parsed = VoiceTransactionParser.parseBest(candidates);
        selectedType = parsed.type;
        parsedAmount = parsed.amount;
        parsedNote = parsed.note;
        recognizedTranscript = parsed.rawTranscript;
        occurredAt = parsed.occurredAt;
        isResultShowing = true;

        binding.layoutVoiceError.setVisibility(View.GONE);
        binding.tvVoiceTranscript.setVisibility(View.VISIBLE);
        binding.tvVoiceTranscript.setText(String.format("\"%s\"", recognizedTranscript));

        binding.etResultAmount.setText(parsedAmount > 0 ? String.valueOf(parsedAmount) : "");
        binding.etResultNote.setText(parsedNote);

        // Khớp ví nguồn từ phương thức thanh toán trong câu nói (vd: quẹt thẻ, momo, tiền mặt, banking)
        if (book != null && !parsed.sourceWalletHint.isEmpty()) {
            matchSourceWallet(parsed.sourceWalletHint);
        }

        // Khớp ví đích nếu có gợi ý từ câu nói khi chuyển khoản
        if (selectedType == TransactionType.TRANSFER && !parsed.destinationWalletHint.isEmpty() && book != null) {
            String hintNorm = parsed.destinationWalletHint.toLowerCase(Locale.ROOT);
            for (Wallet w : book.wallets()) {
                if (w.archivedAt == null && w.name != null && w.name.toLowerCase(Locale.ROOT).contains(hintNorm)) {
                    selectedDestinationWalletId = w.id;
                    break;
                }
            }
        }

        // Tự động dự đoán danh mục nếu là Chi tiêu hoặc Thu nhập
        if (selectedType != TransactionType.TRANSFER && book != null) {
            Category predicted = VoiceCategoryPredictor.predictCategory(
                    book.categories(), selectedType, parsedNote, recognizedTranscript);
            selectedCategoryId = predicted != null ? predicted.id : null;
        } else {
            selectedCategoryId = null;
        }

        binding.cardRecognitionResult.setVisibility(View.VISIBLE);
        binding.tvVoiceStatus.setText(R.string.voice_result_title);
        render();

        // Kích hoạt đếm ngược tự động lưu rảnh tay 3 giây nếu có đủ số tiền
        if (parsedAmount > 0) {
            binding.layoutAmountPresets.setVisibility(View.GONE);
            binding.tvAmountMissingNotice.setVisibility(View.GONE);
            binding.layoutAmountAdjustment.setVisibility(View.VISIBLE);
            startAutoSaveCountdown();
        } else {
            // Trường hợp người dùng nói thiếu số tiền: hiển thị dãy chip gợi ý số tiền nhanh
            binding.layoutAmountAdjustment.setVisibility(View.GONE);
            binding.layoutAmountPresets.setVisibility(View.VISIBLE);
            binding.tvAmountMissingNotice.setVisibility(View.VISIBLE);
            binding.etResultAmount.requestFocus();
            binding.etResultAmount.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(binding.etResultAmount, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        }
    }

    private void matchSourceWallet(String hint) {
        if (book == null || hint == null || hint.isEmpty()) return;
        String hintNorm = hint.toLowerCase(Locale.ROOT);

        for (Wallet w : book.wallets()) {
            if (w.archivedAt != null) continue;
            String nameNorm = w.name != null ? w.name.toLowerCase(Locale.ROOT) : "";

            if (hintNorm.equals("thẻ") && (nameNorm.contains("thẻ") || nameNorm.contains("card") || nameNorm.contains("visa") || nameNorm.contains("tín dụng"))) {
                selectedWalletId = w.id;
                return;
            }
            if (hintNorm.equals("momo") && nameNorm.contains("momo")) {
                selectedWalletId = w.id;
                return;
            }
            if (hintNorm.equals("tiền mặt") && (nameNorm.contains("tiền mặt") || w.walletType == WalletType.CASH)) {
                selectedWalletId = w.id;
                return;
            }
            if (hintNorm.equals("ngân hàng") && (w.walletType == WalletType.BANK || nameNorm.contains("vcb") || nameNorm.contains("vietcombank") || nameNorm.contains("bank"))) {
                selectedWalletId = w.id;
                return;
            }
            if (nameNorm.contains(hintNorm)) {
                selectedWalletId = w.id;
                return;
            }
        }
    }

    private void startAutoSaveCountdown() {
        cancelAutoSaveCountdown(false);
        isAutoSaveActive = true;
        binding.tvAutoSaveNotice.setVisibility(View.VISIBLE);

        autoSaveTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) Math.ceil(millisUntilFinished / 1000.0);
                binding.btnConfirmTransaction.setText(getString(R.string.voice_btn_confirm_countdown, sec));
            }

            @Override
            public void onFinish() {
                isAutoSaveActive = false;
                binding.tvAutoSaveNotice.setVisibility(View.GONE);
                binding.btnConfirmTransaction.setText(R.string.voice_btn_confirm);
                saveTransactionDirectly();
            }
        }.start();
    }

    private void cancelAutoSaveCountdown(boolean showNotice) {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
        }
        if (isAutoSaveActive) {
            isAutoSaveActive = false;
            binding.tvAutoSaveNotice.setVisibility(View.GONE);
            binding.btnConfirmTransaction.setText(R.string.voice_btn_confirm);
            if (showNotice) {
                Toast.makeText(this, R.string.voice_autosave_paused, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showErrorUi(String message) {
        cancelAutoSaveCountdown(false);
        binding.tvVoiceStatus.setText(R.string.voice_state_error);
        binding.layoutVoiceError.setVisibility(View.VISIBLE);
        binding.tvVoiceErrorMessage.setText(message);
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return getString(R.string.voice_error_network);
            case SpeechRecognizer.ERROR_AUDIO:
                return getString(R.string.voice_error_audio);
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            default:
                return getString(R.string.voice_error_no_match);
        }
    }

    private void saveTransactionDirectly() {
        if (model.saveLoading()) return;

        long amount = readCurrentAmount();
        if (amount <= 0) {
            Toast.makeText(this, R.string.voice_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedWalletId == null) {
            Toast.makeText(this, R.string.voice_wallet_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedType == TransactionType.TRANSFER) {
            if (selectedDestinationWalletId == null) {
                Toast.makeText(this, R.string.voice_destination_wallet_required, Toast.LENGTH_SHORT).show();
                chooseDestinationWallet();
                return;
            }
            if (selectedDestinationWalletId.equals(selectedWalletId)) {
                Toast.makeText(this, R.string.voice_destination_same_error, Toast.LENGTH_SHORT).show();
                chooseDestinationWallet();
                return;
            }
        }

        String note = binding.etResultNote.getText().toString().trim();
        if (note.isEmpty()) {
            note = selectedType == TransactionType.INCOME ? getString(R.string.category_income)
                    : selectedType == TransactionType.TRANSFER ? getString(R.string.transaction_type_transfer)
                    : getString(R.string.category_expense);
        }

        String catId = selectedType == TransactionType.TRANSFER ? null : selectedCategoryId;

        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(),
                selectedType,
                amount,
                catId,
                selectedWalletId,
                selectedType == TransactionType.TRANSFER ? selectedDestinationWalletId : null,
                occurredAt,
                note,
                note
        );

        model.change(FinanceAction.transaction(transaction, false), true);
    }

    private void openEditInForm() {
        long amount = readCurrentAmount();
        String note = binding.etResultNote.getText().toString().trim();
        openManualForm(note, amount);
    }

    private void openManualForm(String note, long amount) {
        Intent intent = new Intent(this, TransactionFormActivity.class);
        if (amount > 0) {
            intent.putExtra(TransactionFormActivity.EXTRA_AMOUNT, amount);
            intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_AMOUNT_MINOR, amount);
        }
        intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_TYPE, selectedType.name());
        if (note != null && !note.isEmpty()) {
            intent.putExtra(TransactionFormActivity.EXTRA_PAYEE, note);
            intent.putExtra(TransactionFormActivity.EXTRA_NOTE, note);
        }
        if (selectedWalletId != null) {
            intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_WALLET_ID, selectedWalletId);
        }
        if (selectedType == TransactionType.TRANSFER && selectedDestinationWalletId != null) {
            intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_DESTINATION_WALLET_ID, selectedDestinationWalletId);
        }
        if (selectedType != TransactionType.TRANSFER && selectedCategoryId != null) {
            intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_CATEGORY_ID, selectedCategoryId);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void loading(boolean loading) {
        FinanceUi.progress(binding.btnConfirmTransaction, loading);
        binding.btnConfirmTransaction.setEnabled(!loading);
        binding.btnEditMore.setEnabled(!loading);
        binding.btnRetryVoice.setEnabled(!loading);
    }

    @Override
    protected void completed() {
        HapticHelper.success(this);
        Toast.makeText(this, R.string.finance_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        cancelAutoSaveCountdown(false);
        super.onSaveInstanceState(out);
        out.putString(STATE_TYPE, selectedType.name());
        out.putString(STATE_WALLET, selectedWalletId);
        out.putString(STATE_DESTINATION, selectedDestinationWalletId);
        out.putString(STATE_CATEGORY, selectedCategoryId);
        out.putLong(STATE_AMOUNT, parsedAmount);
        out.putString(STATE_NOTE, parsedNote);
        out.putString(STATE_TRANSCRIPT, recognizedTranscript);
        out.putBoolean(STATE_RESULT_SHOWING, isResultShowing);
        out.putLong(STATE_OCCURRED_AT, occurredAt);
    }

    @Override
    protected void onDestroy() {
        cancelAutoSaveCountdown(false);
        stopListening();
        super.onDestroy();
    }
}
