package com.test.cutipdo2026;

import java.io.Serializable;

public class QueuedRequest implements Serializable {
    private String employeeName;
    private String targetDate;
    private int totalDays;
    private String leaveType;

    public QueuedRequest(String employeeName, String targetDate, int totalDays, String leaveType) {
        this.employeeName = employeeName;
        this.targetDate = targetDate;
        this.totalDays = totalDays;
        this.leaveType = leaveType;
    }

    // Getters and Setters so we can read and edit these fields later on our review screen
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    @Override
    public String toString() {
        return employeeName + " (" + leaveType + ")\n" + targetDate + " | " + totalDays + " Day(s)";
    }
}