package com.kikasports.app;

import android.content.Context;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimezoneConverter {
    private static final String TAG = "TimezoneConverter";
    private Context context;

    public TimezoneConverter(Context context) {
        this.context = context;
    }

    /**
     * Get the timezone that the API uses for kickoff times
     * Based on analysis: 12:00 API time = 19:00 GMT (UTC-7)
     */
    public TimeZone getApiTimeZone() {
        return TimeZone.getTimeZone("GMT-7");
    }

    /**
     * Get user's timezone based on device settings
     */
    public TimeZone getUserTimeZone() {
        try {
            TimeZone deviceTimeZone = TimeZone.getDefault();

            Log.d(TAG, String.format(
                    "User Timezone: %s (Offset: %d hours from UTC)",
                    deviceTimeZone.getID(),
                    deviceTimeZone.getRawOffset() / (1000 * 60 * 60)
            ));

            return deviceTimeZone;

        } catch (Exception e) {
            Log.e(TAG, "Error getting user timezone, defaulting to GMT", e);
            return TimeZone.getTimeZone("GMT");
        }
    }

    /**
     * Convert time from API timezone to user timezone
     */
    public String convertKickoffTime(String year, String month, String day,
                                     String hour, String minute, String second) {
        try {
            // Create date in API timezone (UTC-7)
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            apiFormat.setTimeZone(getApiTimeZone());

            String apiTimeString = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
            Date apiDate = apiFormat.parse(apiTimeString);

            // Format for user's timezone
            SimpleDateFormat userFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            userFormat.setTimeZone(getUserTimeZone());

            String userTime = userFormat.format(apiDate);

            Log.d(TAG, String.format(
                    "Time Conversion - API: %s:%s (UTC-7) -> User: %s (%s)",
                    hour, minute, userTime, getUserTimeZone().getID()
            ));

            return userTime;

        } catch (Exception e) {
            Log.e(TAG, "Error converting kickoff time", e);
            return hour + ":" + minute; // Fallback to original time
        }
    }

    /**
     * Convert timestamp from API timezone to UTC
     */
    public long convertToTimestamp(String year, String month, String day,
                                   String hour, String minute, String second) {
        try {
            // Create date in API timezone
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            apiFormat.setTimeZone(getApiTimeZone());

            String apiTimeString = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
            Date apiDate = apiFormat.parse(apiTimeString);

            return apiDate.getTime();

        } catch (Exception e) {
            Log.e(TAG, "Error converting to timestamp", e);
            return 0;
        }
    }

    /**
     * Get timezone offset information for debugging
     */
    public String getTimezoneInfo() {
        TimeZone apiTz = getApiTimeZone();
        TimeZone userTz = getUserTimeZone();

        int apiOffset = apiTz.getRawOffset() / (1000 * 60 * 60);
        int userOffset = userTz.getRawOffset() / (1000 * 60 * 60);
        int timeDifference = userOffset - apiOffset;

        return String.format(
                "API Timezone: %s (UTC%+d) | User Timezone: %s (UTC%+d) | Difference: %+d hours",
                apiTz.getID(), apiOffset, userTz.getID(), userOffset, timeDifference
        );
    }

    /**
     * Test method to verify timezone conversion
     */
    public void testTimezoneConversion() {
        Log.d(TAG, "=== Timezone Conversion Test ===");
        Log.d(TAG, getTimezoneInfo());

        // Test case: 12:00 in API timezone should be 19:00 for GMT users
        try {
            String testTime = convertKickoffTime("2024", "12", "25", "12", "00", "00");
            Log.d(TAG, String.format("Test: 12:00 API time -> %s user time", testTime));

        } catch (Exception e) {
            Log.e(TAG, "Error in timezone conversion test", e);
        }
    }
}