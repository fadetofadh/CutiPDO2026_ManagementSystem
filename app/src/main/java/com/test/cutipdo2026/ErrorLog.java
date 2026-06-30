package com.test.cutipdo2026;

public class ErrorLog {
    public String action = "report_error";
    public String employeeName;
    public String activityName;
    public String errorMessage;
    public String errorType;

    public ErrorLog(String employeeName, String activityName, String errorMessage, String errorType) {
        this.employeeName = employeeName;
        this.activityName = activityName;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }
}
