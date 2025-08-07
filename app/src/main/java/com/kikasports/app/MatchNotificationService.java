package com.kikasports.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MatchNotificationService extends Service {
    private static final String TAG = "MatchNotificationService";
    private static final String PREFS_NAME = "LiveScorePrefs";
    private static final String FAVORITES_KEY = "favorites";
    private static final String MATCH_STATES_KEY = "match_states";

    private OkHttpClient client = new OkHttpClient();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Store previous match states to detect changes
    private Map<String, MatchState> previousMatchStates = new HashMap<>();

    private static final long CHECK_INTERVAL = 30000; // 30 seconds
    private Runnable checkRunnable;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startMonitoring();
        return START_STICKY; // Restart if killed
    }

    private void startMonitoring() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkFavoriteMatches();
                mainHandler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        mainHandler.post(checkRunnable);
    }

    private void checkFavoriteMatches() {
        executor.execute(() -> {
            try {
                Set<String> favoriteIds = loadFavoriteIds();
                if (favoriteIds.isEmpty()) return;

                // Check live matches
                checkLiveMatches(favoriteIds);

                // Check upcoming matches for kickoff reminders
                checkUpcomingMatches(favoriteIds);

            } catch (Exception e) {
                Log.e(TAG, "Error checking favorite matches", e);
            }
        });
    }

    private void checkLiveMatches(Set<String> favoriteIds) throws IOException {
        Request request = new Request.Builder()
                .url("https://livescore-real-time.p.rapidapi.com/matches/list-live?category=soccer")
                .addHeader("x-rapidapi-key", "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a")
                .addHeader("x-rapidapi-host", "livescore-real-time.p.rapidapi.com")
                .build();

        Response response = client.newCall(request).execute();
        String jsonResponse = response.body().string();

        parseLiveMatchesForNotifications(jsonResponse, favoriteIds);
    }

    private void checkUpcomingMatches(Set<String> favoriteIds) throws IOException {
        // Get today's date
        Calendar cal = Calendar.getInstance();
        String today = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));

        Request request = new Request.Builder()
                .url("https://livescore-real-time.p.rapidapi.com/matches/list-by-date?category=soccer&date=" + today)
                .addHeader("x-rapidapi-key", "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a")
                .addHeader("x-rapidapi-host", "livescore-real-time.p.rapidapi.com")
                .build();

        Response response = client.newCall(request).execute();
        String jsonResponse = response.body().string();

        parseUpcomingMatchesForNotifications(jsonResponse, favoriteIds);
    }

    private void parseLiveMatchesForNotifications(String jsonResponse, Set<String> favoriteIds) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            if (jsonObject.has("Stages") && jsonObject.get("Stages").isJsonArray()) {
                JsonArray stagesArray = jsonObject.getAsJsonArray("Stages");

                for (JsonElement stageElement : stagesArray) {
                    JsonObject stageObj = stageElement.getAsJsonObject();
                    String competition = getJsonString(stageObj, "Snm");

                    if (stageObj.has("Events") && stageObj.get("Events").isJsonArray()) {
                        JsonArray eventsArray = stageObj.getAsJsonArray("Events");

                        for (JsonElement eventElement : eventsArray) {
                            JsonObject eventObj = eventElement.getAsJsonObject();
                            String matchId = getJsonString(eventObj, "Eid");

                            if (favoriteIds.contains(matchId)) {
                                checkMatchForNotifications(eventObj, competition, matchId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing live matches for notifications", e);
        }
    }

    private void parseUpcomingMatchesForNotifications(String jsonResponse, Set<String> favoriteIds) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            if (jsonObject.has("Stages") && jsonObject.get("Stages").isJsonArray()) {
                JsonArray stagesArray = jsonObject.getAsJsonArray("Stages");

                for (JsonElement stageElement : stagesArray) {
                    JsonObject stageObj = stageElement.getAsJsonObject();

                    if (stageObj.has("Events") && stageObj.get("Events").isJsonArray()) {
                        JsonArray eventsArray = stageObj.getAsJsonArray("Events");

                        for (JsonElement eventElement : eventsArray) {
                            JsonObject eventObj = eventElement.getAsJsonObject();
                            String matchId = getJsonString(eventObj, "Eid");

                            if (favoriteIds.contains(matchId)) {
                                checkForKickoffReminder(eventObj, matchId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing upcoming matches for notifications", e);
        }
    }

    private void checkMatchForNotifications(JsonObject eventObj, String competition, String matchId) {
        try {
            // Get team names
            String homeTeam = "";
            String awayTeam = "";

            if (eventObj.has("T1") && eventObj.get("T1").isJsonArray()) {
                JsonArray t1Array = eventObj.getAsJsonArray("T1");
                if (t1Array.size() > 0) {
                    homeTeam = getJsonString(t1Array.get(0).getAsJsonObject(), "Nm");
                }
            }

            if (eventObj.has("T2") && eventObj.get("T2").isJsonArray()) {
                JsonArray t2Array = eventObj.getAsJsonArray("T2");
                if (t2Array.size() > 0) {
                    awayTeam = getJsonString(t2Array.get(0).getAsJsonObject(), "Nm");
                }
            }

            // Get current match state
            String homeScore = getJsonString(eventObj, "Tr1");
            String awayScore = getJsonString(eventObj, "Tr2");
            String period = getJsonString(eventObj, "Eps");

            MatchState currentState = new MatchState(homeScore, awayScore, period);
            MatchState previousState = previousMatchStates.get(matchId);

            if (previousState != null) {
                // Check for goals
                if (hasNewGoal(previousState, currentState)) {
                    String scorer = determineScorer(previousState, currentState, homeTeam, awayTeam);
                    sendNotification(
                            NotificationReceiver.TYPE_GOAL,
                            "⚽ GOAL!",
                            scorer + " scores! " + homeTeam + " " + homeScore + "-" + awayScore + " " + awayTeam,
                            matchId
                    );
                }

                // Check for half-time
                if (isHalfTime(previousState, currentState)) {
                    sendNotification(
                            NotificationReceiver.TYPE_HALF_TIME,
                            "⏰ Half Time",
                            homeTeam + " " + homeScore + "-" + awayScore + " " + awayTeam,
                            matchId
                    );
                }

                // Check for full-time
                if (isFullTime(previousState, currentState)) {
                    sendNotification(
                            NotificationReceiver.TYPE_FULL_TIME,
                            "🏁 Full Time",
                            "Final: " + homeTeam + " " + homeScore + "-" + awayScore + " " + awayTeam,
                            matchId
                    );
                }
            } else {
                // First time seeing this match - check if it just kicked off
                if (period != null && (period.contains("'") || period.equals("1"))) {
                    sendNotification(
                            NotificationReceiver.TYPE_KICKOFF,
                            "🏟 Kick Off!",
                            homeTeam + " vs " + awayTeam + " has started!",
                            matchId
                    );
                }
            }

            // Update stored state
            previousMatchStates.put(matchId, currentState);

        } catch (Exception e) {
            Log.e(TAG, "Error checking match for notifications", e);
        }
    }

    private void checkForKickoffReminder(JsonObject eventObj, String matchId) {
        try {
            String esd = getJsonString(eventObj, "Esd");
            if (esd != null && esd.length() == 14) {
                // Parse match time
                long matchTime = parseMatchTimestamp(esd);
                long currentTime = System.currentTimeMillis();
                long timeDiff = matchTime - currentTime;

                // Check if match starts in 15 minutes (±2 minutes tolerance)
                if (timeDiff > 13 * 60 * 1000 && timeDiff < 17 * 60 * 1000) {
                    // Check if we haven't already sent this notification
                    if (!hasNotificationBeenSent(matchId, "15min_reminder")) {
                        String homeTeam = "";
                        String awayTeam = "";

                        if (eventObj.has("T1") && eventObj.get("T1").isJsonArray()) {
                            JsonArray t1Array = eventObj.getAsJsonArray("T1");
                            if (t1Array.size() > 0) {

                                homeTeam = getJsonString(t1Array.get(0).getAsJsonObject(), "Nm");

                            }
                        }

                        if (eventObj.has("T2") && eventObj.get("T2").isJsonArray()) {
                            JsonArray t2Array = eventObj.getAsJsonArray("T2");
                            if (t2Array.size() > 0) {
                                awayTeam = getJsonString(t2Array.get(0).getAsJsonObject(), "Nm");
                            }
                        }

                        sendNotification(
                                NotificationReceiver.TYPE_KICKOFF_REMINDER,
                                "⏰ Match Starting Soon",
                                homeTeam + " vs " + awayTeam + " starts in 15 minutes!",
                                matchId
                        );

                        markNotificationAsSent(matchId, "15min_reminder");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for kickoff reminder", e);
        }
    }

    private boolean hasNewGoal(MatchState previous, MatchState current) {
        try {
            int prevHome = Integer.parseInt(previous.homeScore != null ? previous.homeScore : "0");
            int prevAway = Integer.parseInt(previous.awayScore != null ? previous.awayScore : "0");
            int currHome = Integer.parseInt(current.homeScore != null ? current.homeScore : "0");
            int currAway = Integer.parseInt(current.awayScore != null ? current.awayScore : "0");

            return (currHome > prevHome) || (currAway > prevAway);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String determineScorer(MatchState previous, MatchState current, String homeTeam, String awayTeam) {
        try {
            int prevHome = Integer.parseInt(previous.homeScore != null ? previous.homeScore : "0");
            int currHome = Integer.parseInt(current.homeScore != null ? current.homeScore : "0");

            if (currHome > prevHome) {
                return homeTeam;
            } else {
                return awayTeam;
            }
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }

    private boolean isHalfTime(MatchState previous, MatchState current) {
        return current.period != null && current.period.equals("HT") &&
                (previous.period == null || !previous.period.equals("HT"));
    }

    private boolean isFullTime(MatchState previous, MatchState current) {
        return current.period != null && current.period.equals("FT") &&
                (previous.period == null || !previous.period.equals("FT"));
    }

    private void sendNotification(String type, String title, String content, String matchId) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("type", type);
        intent.putExtra("title", title);
        intent.putExtra("match_info", content);
        intent.putExtra("match_id", matchId);

        sendBroadcast(intent);
    }

    private Set<String> loadFavoriteIds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String favoritesJson = prefs.getString(FAVORITES_KEY, "[]");

        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Set<String>>(){}.getType();
            Set<String> favorites = gson.fromJson(favoritesJson, type);
            return favorites != null ? favorites : new HashSet<>();
        } catch (Exception e) {
            Log.e(TAG, "Error loading favorites", e);
            return new HashSet<>();
        }
    }

    private boolean hasNotificationBeenSent(String matchId, String type) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(matchId + "_" + type, false);
    }

    private void markNotificationAsSent(String matchId, String type) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(matchId + "_" + type, true).apply();
    }

    private long parseMatchTimestamp(String esd) {
        // Same implementation as in MainActivity
        try {
            String year = esd.substring(0, 4);
            String month = esd.substring(4, 6);
            String day = esd.substring(6, 8);
            String hour = esd.substring(8, 10);
            String minute = esd.substring(10, 12);
            String second = esd.substring(12, 14);

            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day),
                    Integer.parseInt(hour), Integer.parseInt(minute), Integer.parseInt(second));

            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (checkRunnable != null) {
            mainHandler.removeCallbacks(checkRunnable);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // Inner class to track match state
    private static class MatchState {
        String homeScore;
        String awayScore;
        String period;

        MatchState(String homeScore, String awayScore, String period) {
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.period = period;
        }
    }
}