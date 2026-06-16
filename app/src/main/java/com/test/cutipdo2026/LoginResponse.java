package com.test.cutipdo2026;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("message")
    private String message;

    @SerializedName("filterClass")
    private String filterClass;

    @SerializedName("isSpv")
    private boolean isSpv;

    @SerializedName("roleName")
    private String roleName;

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getFilterClass() { return filterClass; }
    public boolean isSpv() { return isSpv; }
    public String getRoleName() { return roleName; }
}