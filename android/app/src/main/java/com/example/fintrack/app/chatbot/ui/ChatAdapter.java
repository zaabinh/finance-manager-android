package com.example.fintrack.app.chatbot.ui;

import android.os.Build;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.chatbot.domain.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách tin nhắn chat giữa Người dùng và Trợ lý Đồng Bot.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnChatActionListener {
        void onActionClick(ChatMessage.ChatAction action);
        void onPromptClick(String prompt);
    }

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private final List<ChatMessage> messages = new ArrayList<>();
    private final OnChatActionListener listener;

    public ChatAdapter(OnChatActionListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<ChatMessage> list) {
        this.messages.clear();
        if (list != null) {
            this.messages.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        if (message == null) return;
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.sender == ChatMessage.Sender.USER) {
            return VIEW_TYPE_USER;
        }
        return VIEW_TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_bot, parent, false);
            return new BotViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg);
        } else if (holder instanceof BotViewHolder) {
            ((BotViewHolder) holder).bind(msg, listener);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvContent;
        private final TextView tvTimestamp;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvUserContent);
            tvTimestamp = itemView.findViewById(R.id.tvUserTimestamp);
        }

        void bind(ChatMessage message) {
            tvContent.setText(message.content);
            tvTimestamp.setText(TIME_FORMAT.format(new Date(message.timestamp)));
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvContent;
        private final TextView tvTimestamp;
        private final View cardAction;
        private final TextView tvActionTitle;
        private final ImageView ivActionIcon;

        BotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvBotContent);
            tvTimestamp = itemView.findViewById(R.id.tvBotTimestamp);
            cardAction = itemView.findViewById(R.id.cardAction);
            tvActionTitle = itemView.findViewById(R.id.tvActionTitle);
            ivActionIcon = itemView.findViewById(R.id.ivActionIcon);
        }

        void bind(ChatMessage message, OnChatActionListener listener) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tvContent.setText(Html.fromHtml(message.content, Html.FROM_HTML_MODE_COMPACT));
            } else {
                tvContent.setText(Html.fromHtml(message.content));
            }
            tvTimestamp.setText(TIME_FORMAT.format(new Date(message.timestamp)));

            if (message.action != null) {
                cardAction.setVisibility(View.VISIBLE);
                tvActionTitle.setText(message.action.label);

                if (message.action.type == ChatMessage.ActionType.CREATE_TRANSACTION) {
                    ivActionIcon.setImageResource(R.drawable.ic_plus);
                } else if (message.action.type == ChatMessage.ActionType.VIEW_BUDGET) {
                    ivActionIcon.setImageResource(R.drawable.ic_nav_layers);
                } else {
                    ivActionIcon.setImageResource(R.drawable.ic_action_report);
                }

                cardAction.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onActionClick(message.action);
                    }
                });
            } else {
                cardAction.setVisibility(View.GONE);
            }
        }
    }
}
