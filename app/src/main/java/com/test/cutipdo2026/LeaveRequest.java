package com.test.cutipdo2026;

public class LeaveRequest {
    private String action;
    private String employeeName;
    private String targetDate;
    private int totalDays;
    private String leaveType;
    private int rowNumber; // GSON will convert this directly to "rowNumber" for the script

    // Constructor for Division Head submission
    public LeaveRequest(String action, String employeeName, String targetDate, int totalDays, String leaveType) {
        this.action = action;
        this.employeeName = employeeName;
        this.targetDate = targetDate;
        this.totalDays = totalDays;
        this.leaveType = leaveType;
    }

    // Constructor for Supervisor approval targeting a specific row
    public LeaveRequest(String action, int rowNumber) {
        this.action = action;
        this.rowNumber = rowNumber;
    }
}