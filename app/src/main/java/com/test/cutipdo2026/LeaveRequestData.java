package com.test.cutipdo2026;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LeaveRequestData implements Serializable {
    @SerializedName("rowNumber")
    public int rowNumber;

    @SerializedName("employeeName")
    public String employeeName;

    @SerializedName("targetDate")
    public Object targetDate; // Using Object to handle both String and Long (from GSON)

    @SerializedName("totalDays")
    public int totalDays;

    @SerializedName("leaveType")
    public String leaveType;

    @SerializedName("description")
    public String description;

    @SerializedName("status")
    public String status;    // Col G: Pending, Approved, System, Cancelled

    @SerializedName("actionType")
    public String actionType; // Col B: Submit, Add, Direct

    @SerializedName("calendarEventId")
    public String calendarEventId; // Col I: Calendar IDs or Approval Reference

    // UI States
    public boolean isMarked = false;

    /**
     * Helper to safely get the date as a formatted String.
     * Some server responses might send dates as a 'long' timestamp.
     */
    public String getFormattedDate() {
        if (targetDate == null) return "N/A";
        
        // 1. If it's already a clean string (like "2026-06-10")
        if (targetDate instanceof String) {
            String dateStr = (String) targetDate;
            
            // Sometimes numbers come in as scientific notation like "1.718E12"
            if (dateStr.contains("E") || dateStr.contains("e")) {
                try {
                    return formatFromTimestamp(new java.math.BigDecimal(dateStr).longValue());
                } catch (Exception ignored) {}
            }
            
            // If it's a numeric string (Unix Timestamp or Excel Date)
            if (dateStr.matches("-?\\d+(\\.\\d+)?")) {
                try {
                    return formatFromTimestamp(new java.math.BigDecimal(dateStr).longValue());
                } catch (Exception ignored) {}
            }
            return dateStr;
        }
        
        // 2. If GSON parsed it as a Number (Double or Long)
        if (targetDate instanceof Number) {
            return formatFromTimestamp(((Number) targetDate).longValue());
        }
        
        return String.valueOf(targetDate);
    }

    private String formatFromTimestamp(long timestamp) {
        if (timestamp <= 0) return "1970-01-01 (Error)";
        
        try {
            // A: Check if it's an Excel/Google Sheets serial date (e.g., 45456)
            // Excel dates are usually between 1 and 100,000
            if (timestamp > 0 && timestamp < 200000) {
                java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                // Google Sheets base date is Dec 30, 1899
                c.set(1899, 11, 30, 0, 0, 0);
                c.set(java.util.Calendar.MILLISECOND, 0);
                c.add(java.util.Calendar.DATE, (int) timestamp);
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return sdf.format(c.getTime());
            }

            // B: Handle Unix Timestamps (Seconds vs Milliseconds)
            long ms = timestamp;
            if (timestamp < 10000000000L) { // Likely seconds
                ms = timestamp * 1000;
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return sdf.format(new java.util.Date(ms));
            
        } catch (Exception e) {
            return "Format Err: " + timestamp;
        }
    }

    // A clean text generator to format how rows read inside our simple list view
    @Override
    public String toString() {
        return employeeName + " (" + leaveType + ")\n" +
                "Dates: " + getFormattedDate() + "\n" +
                "Duration: " + totalDays + " Day(s)";
    }
}