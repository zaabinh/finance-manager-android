package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class UserPreferencesResponse {
    @SerializedName("displayName") private String displayName;
    @SerializedName("currencyCode") private String currencyCode;
    @SerializedName("timeZone") private String timeZone;

    public String getDisplayName() { return displayName; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTimeZone() { return timeZone; }
}
