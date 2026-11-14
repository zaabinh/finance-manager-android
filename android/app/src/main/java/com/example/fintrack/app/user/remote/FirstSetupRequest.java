package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.wallet.remote.WalletRequest;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Body của POST api/v1/users/me/first-setup; server tạo các ví trong cùng transaction với việc hoàn tất thiết lập. */
public class FirstSetupRequest {
    @SerializedName("profile") private final UpdateProfileRequest profile;
    @SerializedName("notificationSettings") private final UpdateNotificationSettingsRequest notificationSettings;
    @SerializedName("wallets") private final List<WalletRequest> wallets;

    public FirstSetupRequest(UpdateProfileRequest profile, UpdateNotificationSettingsRequest notificationSettings,
                             List<WalletRequest> wallets) {
        this.profile = profile;
        this.notificationSettings = notificationSettings;
        this.wallets = wallets;
    }
}
