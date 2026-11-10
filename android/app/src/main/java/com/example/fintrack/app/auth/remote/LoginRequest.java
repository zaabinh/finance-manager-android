package com.example.fintrack.app.auth.remote;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("usernameOrEmail")
    private String usernameOrEmail;

    @SerializedName("password")
    private String password;
    @SerializedName("rememberMe")
    private boolean rememberMe;

    public LoginRequest(String usernameOrEmail, String password, boolean rememberMe) {
        this.rememberMe = rememberMe;
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
