package com.test.cutipdo2026;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class to handle standardized date-based sorting and validation across all list activities.
 */
public class ListSorter {

    /**
     * Sorts the provided list by date, newest first (latest dates at the top).
     */
    public static void sortNewestFirst(List<LeaveRequestData> list) {
        if (list == null || list.isEmpty()) return;
        
        Collections.sort(list, (o1, o2) -> {
            Date d1 = getStartDate(o1.getFormattedDate());
            Date d2 = getStartDate(o2.getFormattedDate());
            
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            
            // Newest to Oldest
            return d2.compareTo(d1);
        });
    }

    /**
     * Checks if a request is "Cancellable" (only if it hasn't started yet).
     * Returns true if the start date is after today.
     */
    public static boolean isCancellable(String dateRangeStr) {
        try {
            Date startDate = getStartDate(dateRangeStr);
            if (startDate == null) return false;

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            // True if startDate is strictly AFTER today
            return startDate.after(today.getTime());
        } catch (Exception e) {
            return false;
        }
    }

    private static Date getStartDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            if (dateStr.contains(" to ")) {
                return sdf.parse(dateStr.split(" to ")[0]);
            } else if (!dateStr.equals("-") && !dateStr.isEmpty()) {
                return sdf.parse(dateStr);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}