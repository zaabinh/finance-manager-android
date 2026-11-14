package com.example.fintrack.app.chatbot.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.ui.AnalyticsActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.chatbot.data.ChatbotRepository;
import com.example.fintrack.app.chatbot.domain.ChatMessage;
import com.example.fintrack.app.chatbot.domain.FinancialChatbotEngine;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.databinding.ActivityChatbotBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Trợ lý Chatbot tài chính thông minh (Đồng Bot).
 * Hỗ trợ trò chuyện, nhận diện giao dịch tự nhiên, truy vấn ngân sách, và tư vấn tài chính.
 */
public class ChatbotActivity extends FinanceActivity implements ChatAdapter.OnChatActionListener {

    private ActivityChatbotBinding binding;
    private ChatAdapter adapter;
    private ChatbotRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> voiceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        if (spokenText != null && !spokenText.trim().isEmpty()) {
                            sendMessage(spokenText.trim());
                        }
                    }
                }
            }
    );

    @Override
    protected View contentView() {
        binding = ActivityChatbotBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected FinanceRepository.Scope readScope() {
        return FinanceRepository.Scope.ALL;
    }

    @Override
    protected void bind(Bundle saved) {
        repository = new ChatbotRepository(this);

        initViews();
        setupListeners();
        loadHistory();
    }

    @Override
    protected void render() {
        // Cập nhật khi dữ liệu FinanceBook hoàn tất nạp (nếu có hành động cần sync)
    }

    private void initViews() {
        adapter = new ChatAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvChatMessages.setLayoutManager(layoutManager);
        binding.rvChatMessages.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.toolbarChatbot.setNavigationOnClickListener(v -> finish());

        binding.btnClearHistory.setOnClickListener(v -> showClearHistoryDialog());

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etMessage.getText().toString();
            if (!text.trim().isEmpty()) {
                sendMessage(text.trim());
                binding.etMessage.setText("");
            }
        });

        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                String text = binding.etMessage.getText().toString();
                if (!text.trim().isEmpty()) {
                    sendMessage(text.trim());
                    binding.etMessage.setText("");
                }
                return true;
            }
            return false;
        });

        binding.btnVoiceInput.setOnClickListener(v -> startVoiceRecognition());

        // Quick prompts click listeners
        binding.chipPromptBudget.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_budget)));
        binding.chipPromptBalance.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_balance)));
        binding.chipPromptSampleTx.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_sample_tx)));
        binding.chipPrompt503020.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_50_30_20)));
        binding.chipPromptEmergency.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_emergency)));
        binding.chipPromptSaving.setOnClickListener(v -> sendMessage(getString(R.string.chatbot_prompt_saving)));
    }

    private void loadHistory() {
        List<ChatMessage> list = repository.getMessages();
        adapter.setMessages(list);
        scrollToBottom();
    }

    private void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;

        ChatMessage userMsg = ChatMessage.userMessage(text);
        repository.addMessage(userMsg);
        adapter.addMessage(userMsg);
        scrollToBottom();

        // Hiển thị trạng thái bot đang suy nghĩ / soạn câu trả lời
        binding.layoutTypingIndicator.setVisibility(View.VISIBLE);

        mainHandler.postDelayed(() -> {
            ChatMessage botReply = FinancialChatbotEngine.process(text, book);
            repository.addMessage(botReply);
            adapter.addMessage(botReply);
            binding.layoutTypingIndicator.setVisibility(View.GONE);
            scrollToBottom();
        }, 450);
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_state_listening));

        try {
            voiceLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_state_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearHistoryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chatbot_clear_confirm_title)
                .setMessage(R.string.chatbot_clear_confirm_msg)
                .setPositiveButton(R.string.chatbot_clear_confirm_yes, (dialog, which) -> {
                    repository.clearMessages();
                    loadHistory();
                })
                .setNegativeButton(R.string.chatbot_clear_confirm_cancel, null)
                .show();
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            binding.rvChatMessages.smoothScrollToPosition(count - 1);
        }
    }

    @Override
    public void onActionClick(ChatMessage.ChatAction action) {
        if (action == null) return;

        if (action.type == ChatMessage.ActionType.CREATE_TRANSACTION) {
            Intent intent = new Intent(this, TransactionFormActivity.class);
            if (action.amount > 0) {
                intent.putExtra(TransactionFormActivity.EXTRA_AMOUNT, action.amount);
                intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_AMOUNT_MINOR, action.amount);
            }
            if (action.note != null && !action.note.isEmpty()) {
                intent.putExtra(TransactionFormActivity.EXTRA_NOTE, action.note);
            }
            if (action.transactionType != null) {
                intent.putExtra(TransactionFormActivity.EXTRA_PREFILL_TYPE, action.transactionType.name());
            }
            startActivity(intent);
        } else if (action.type == ChatMessage.ActionType.VIEW_BUDGET) {
            startActivity(new Intent(this, BudgetListActivity.class));
        } else if (action.type == ChatMessage.ActionType.VIEW_ANALYTICS) {
            startActivity(new Intent(this, AnalyticsActivity.class));
        }
    }

    @Override
    public void onPromptClick(String prompt) {
        if (prompt != null && !prompt.isEmpty()) {
            sendMessage(prompt);
        }
    }
}
