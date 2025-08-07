package com.kikasports.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements MatchAdapter.OnFavoriteClickListener {
    private static final String TAG = "LiveScore";
    private static final String PREFS_NAME = "LiveScorePrefs";
    private static final String FAVORITES_KEY = "favorites";

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout favoritesSection, liveSection, scheduledSection, noMatchesLayout;
    private LinearLayout dateTabsContainer, liveButton;
    private RecyclerView favoritesRecyclerView, liveRecyclerView, scheduledRecyclerView;

    private MatchAdapter favoritesAdapter, liveAdapter, scheduledAdapter;
    private List<Match> allMatches = new ArrayList<>();
    private Set<String> favoriteMatchIds = new HashSet<>();

    private OkHttpClient client = new OkHttpClient();
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Date selection variables
    private List<DateTab> dateTabs = new ArrayList<>();
    private String selectedDate = "";
    private boolean isLiveMode = false;
    private TimezoneConverter timezoneConverter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerViews();
        setupDateSelector();
        loadFavorites();
        loadMatches();
        startNotificationService();


        timezoneConverter = new TimezoneConverter(this);

        // Test timezone conversion (optional - for debugging)
        timezoneConverter.testTimezoneConversion();
        Log.d(TAG, timezoneConverter.getTimezoneInfo());

        requestNotificationPermission();

        swipeRefresh.setOnRefreshListener(this::loadMatches);
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        favoritesSection = findViewById(R.id.favoritesSection);
        liveSection = findViewById(R.id.liveSection);
        scheduledSection = findViewById(R.id.scheduledSection);
        noMatchesLayout = findViewById(R.id.noMatchesLayout);

        favoritesRecyclerView = findViewById(R.id.favoritesRecyclerView);
        liveRecyclerView = findViewById(R.id.liveRecyclerView);
        scheduledRecyclerView = findViewById(R.id.scheduledRecyclerView);

        dateTabsContainer = findViewById(R.id.dateTabsContainer);
        liveButton = findViewById(R.id.liveButton);
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                startNotificationService();
            }
        } else {
            startNotificationService();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNotificationService();
            } else {
                Toast.makeText(this, "Notification permission denied. You won't receive match updates.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }



    private void setupRecyclerViews() {
        // Favorites matches
        favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        favoritesAdapter = new MatchAdapter(this, new ArrayList<>());
        favoritesAdapter.setOnFavoriteClickListener(this);
        favoritesRecyclerView.setAdapter(favoritesAdapter);

        // Live matches
        liveRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        liveAdapter = new MatchAdapter(this, new ArrayList<>());
        liveAdapter.setOnFavoriteClickListener(this);
        liveRecyclerView.setAdapter(liveAdapter);

        // Scheduled matches
        scheduledRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        scheduledAdapter = new MatchAdapter(this, new ArrayList<>());
        scheduledAdapter.setOnFavoriteClickListener(this);
        scheduledRecyclerView.setAdapter(scheduledAdapter);
    }

    private void setupDateSelector() {
        // Setup live button
        liveButton.setOnClickListener(v -> {
            isLiveMode = !isLiveMode;
            updateLiveButtonState();
            if (isLiveMode) {
                // Clear date selection when live mode is active
                selectedDate = "";
                updateDateTabsState();
            }
            loadMatches();
        });

        // Create date tabs for the next 7 days
        createDateTabs();

        // Select today by default
        selectedDate = getCurrentDate();
        updateDateTabsState();
    }

    private void createDateTabs() {
        dateTabs.clear();

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
        SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // Create tabs for next 14 days (2 weeks)
        for (int i = 0; i < 14; i++) {
            DateTab dateTab = new DateTab();
            dateTab.dayName = i == 0 ? "TODAY" : dayFormat.format(calendar.getTime()).toUpperCase();
            dateTab.dateDisplay = dateFormat.format(calendar.getTime()).toUpperCase();
            dateTab.dateValue = apiFormat.format(calendar.getTime());
            dateTabs.add(dateTab);

            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Add date tabs to container
        addDateTabsToContainer();
    }

    private void addDateTabsToContainer() {
        // Remove existing date tabs (keep live button)
        while (dateTabsContainer.getChildCount() > 1) {
            dateTabsContainer.removeViewAt(dateTabsContainer.getChildCount() - 1);
        }

        for (int i = 0; i < dateTabs.size(); i++) {
            final DateTab dateTab = dateTabs.get(i);
            final int index = i;

            // Create date tab view
            LinearLayout tabView = new LinearLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 32, 0);
            tabView.setLayoutParams(params);
            tabView.setOrientation(LinearLayout.VERTICAL);
            tabView.setGravity(android.view.Gravity.CENTER);
            tabView.setPadding(24, 16, 24, 16);
            tabView.setClickable(true);
            tabView.setFocusable(true);
            tabView.setBackground(getDrawable(R.drawable.date_tab_selector));

            // Day name
            TextView dayText = new TextView(this);
            dayText.setText(dateTab.dayName);
            dayText.setTextColor(getColor(android.R.color.white));
            dayText.setTextSize(12);
            dayText.setGravity(android.view.Gravity.CENTER);

            // Date
            TextView dateText = new TextView(this);
            dateText.setText(dateTab.dateDisplay);
            dateText.setTextColor(getColor(android.R.color.white));
            dateText.setTextSize(10);
            dateText.setGravity(android.view.Gravity.CENTER);

            tabView.addView(dayText);
            tabView.addView(dateText);

            // Set click listener
            tabView.setOnClickListener(v -> {
                isLiveMode = false;
                selectedDate = dateTab.dateValue;
                updateLiveButtonState();
                updateDateTabsState();
                loadMatches();
            });

            dateTabsContainer.addView(tabView);
        }
    }

    private void updateDateTabsState() {
        // Update date tabs appearance
        for (int i = 1; i < dateTabsContainer.getChildCount(); i++) {
            View tabView = dateTabsContainer.getChildAt(i);
            DateTab dateTab = dateTabs.get(i - 1);
            boolean isSelected = !isLiveMode && selectedDate.equals(dateTab.dateValue);
            tabView.setSelected(isSelected);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void updateLiveButtonState() {
        liveButton.setSelected(isLiveMode);
        liveButton.setBackground(getDrawable(R.drawable.live_bg));
    }

    private void loadMatches() {
        swipeRefresh.setRefreshing(true);

        executor.execute(() -> {
            try {
                List<Match> matches = new ArrayList<>();

                if (isLiveMode) {
                    // Load only live matches
                    matches = loadLiveMatches();
                } else {
                    // Load matches for selected date
                    matches = loadMatchesForDate(selectedDate);
                }

                allMatches.clear();
                allMatches.addAll(matches);

                Log.d(TAG, "Loaded " + matches.size() + " matches");

                // Update favorites status
                updateFavoritesStatus();

                mainHandler.post(() -> {
                    updateUI();
                    swipeRefresh.setRefreshing(false);
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading matches", e);
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Failed to load matches", Toast.LENGTH_SHORT).show();
                    swipeRefresh.setRefreshing(false);
                    showNoMatches();
                });
            }
        });
    }

    private List<Match> loadLiveMatches() throws IOException {
        Request liveRequest = new Request.Builder()
                .url("https://livescore-real-time.p.rapidapi.com/matches/list-live?category=soccer")
                .get()
                .addHeader("x-rapidapi-key", "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a")
                .addHeader("x-rapidapi-host", "livescore-real-time.p.rapidapi.com")
                .build();

        Response response = client.newCall(liveRequest).execute();
        return parseMatches(response.body().string(), true);
    }

    private List<Match> loadMatchesForDate(String date) throws IOException {
        List<Match> allDateMatches = new ArrayList<>();

        // Load live matches (always include live matches regardless of date)
        List<Match> liveMatches = loadLiveMatches();
        allDateMatches.addAll(liveMatches);

        // Load scheduled matches for the selected date
        Request dateRequest = new Request.Builder()
                .url("https://livescore-real-time.p.rapidapi.com/matches/list-by-date?category=soccer&date=" + date)
                .get()
                .addHeader("x-rapidapi-key", "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a")
                .addHeader("x-rapidapi-host", "livescore-real-time.p.rapidapi.com")
                .build();

        Response response = client.newCall(dateRequest).execute();
        List<Match> scheduledMatches = parseMatches(response.body().string(), false);

        // Filter scheduled matches based on the selected date
        List<Match> filteredScheduledMatches = filterMatchesForDate(scheduledMatches, date);
        allDateMatches.addAll(filteredScheduledMatches);

        return allDateMatches;
    }

    private List<Match> filterMatchesForDate(List<Match> matches, String targetDate) {
        List<Match> filteredMatches = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Match match : matches) {
            // Skip live matches as they're already included
            if (match.isLive()) {
                continue;
            }

            // Check if match is for the target date
            if (match.getMatchTimestamp() > 0) {
                String matchDate = dateFormat.format(new Date(match.getMatchTimestamp()));
                if (matchDate.equals(targetDate)) {
                    filteredMatches.add(match);
                }
            } else {
                // Include matches without timestamp for the selected date
                filteredMatches.add(match);
            }
        }

        return filteredMatches;
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private List<Match> parseMatches(String jsonResponse, boolean isLive) {
        List<Match> matches = new ArrayList<>();

        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            if (jsonObject.has("Stages") && jsonObject.get("Stages").isJsonArray()) {
                JsonArray stagesArray = jsonObject.getAsJsonArray("Stages");

                for (JsonElement stageElement : stagesArray) {
                    JsonObject stageObj = stageElement.getAsJsonObject();

                    // Get competition name from stage
                    String competition = getJsonString(stageObj, "Snm");

                    if (stageObj.has("Events") && stageObj.get("Events").isJsonArray()) {
                        JsonArray eventsArray = stageObj.getAsJsonArray("Events");

                        for (JsonElement eventElement : eventsArray) {
                            JsonObject eventObj = eventElement.getAsJsonObject();

                            Match match = new Match();
                            match.setId(getJsonString(eventObj, "Eid"));
                            match.setLive(isLive);
                            match.setCompetition(competition);

                            // Parse teams
                            if (eventObj.has("T1") && eventObj.get("T1").isJsonArray()) {
                                JsonArray t1Array = eventObj.getAsJsonArray("T1");
                                if (t1Array.size() > 0) {
                                    JsonObject homeTeam = t1Array.get(0).getAsJsonObject();
                                    match.setHomeTeam(getJsonString(homeTeam, "Nm"));

                                    // Construct logo URL if available
                                    String imgPath = getJsonString(homeTeam, "Img");
                                    if (imgPath != null) {
                                        match.setHomeTeamLogo("https://lsm-static-prod.livescore.com/medium/" + imgPath);
                                    }
                                }
                            }

                            if (eventObj.has("T2") && eventObj.get("T2").isJsonArray()) {
                                JsonArray t2Array = eventObj.getAsJsonArray("T2");
                                if (t2Array.size() > 0) {
                                    JsonObject awayTeam = t2Array.get(0).getAsJsonObject();
                                    match.setAwayTeam(getJsonString(awayTeam, "Nm"));

                                    // Construct logo URL if available
                                    String imgPath = getJsonString(awayTeam, "Img");
                                    if (imgPath != null) {
                                        match.setAwayTeamLogo("https://lsm-static-prod.livescore.com/medium/" + imgPath);
                                    }
                                }
                            }

                            // Parse match time and status
                            if (isLive) {
                                // For live matches, show "LIVE" as the time and parse scores
                                match.setTime("LIVE");
                                match.setStatus("LIVE");

                                // Parse scores for live matches
                                match.setHomeScore(getJsonString(eventObj, "Tr1"));
                                match.setAwayScore(getJsonString(eventObj, "Tr2"));

                                // Get match minute/period if available
                                String eps = getJsonString(eventObj, "Eps");
                                if (eps != null && !eps.isEmpty()) {
                                    match.setTime(eps);
                                }
                            } else {
                                // For scheduled matches, parse the actual kickoff time
                                String kickoffTime = parseKickoffTime(eventObj);
                                match.setTime(kickoffTime);
                                match.setStatus("SCHEDULED");

                                // Parse and set timestamp for scheduled matches
                                long timestamp = parseMatchTimestamp(eventObj);
                                match.setMatchTimestamp(timestamp);

                                // Don't set scores for scheduled matches - they should be null
                                match.setHomeScore(null);
                                match.setAwayScore(null);
                            }

                            // Only add match if we have both teams
                            if (match.getHomeTeam() != null && match.getAwayTeam() != null) {
                                matches.add(match);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing matches", e);
            Log.e(TAG, "JSON Response: " + jsonResponse);
        }

        Log.d(TAG, "Parsed " + matches.size() + " matches from response");
        return matches;
    }


    private String parseKickoffTime(JsonObject eventObj) {
        try {
            String esd = getJsonString(eventObj, "Esd");
            if (esd != null && esd.length() == 14) {

                String year = esd.substring(0, 4);
                String month = esd.substring(4, 6);
                String day = esd.substring(6, 8);
                String hour = esd.substring(8, 10);
                String minute = esd.substring(10, 12);
                String second = esd.substring(12, 14);

                // API timezone is UTC-7 (based on your analysis: 12:00 API = 19:00 GMT)
                TimeZone apiTimeZone = TimeZone.getTimeZone("GMT-7");

                // Get user's device timezone
                TimeZone userTimeZone = TimeZone.getDefault();

                // Create date in API timezone (UTC-7)
                SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                apiFormat.setTimeZone(apiTimeZone);

                String apiTimeString = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
                Date apiDate = apiFormat.parse(apiTimeString);

                // Format for user's timezone
                SimpleDateFormat userFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                userFormat.setTimeZone(userTimeZone);

                String userTime = userFormat.format(apiDate);

                // Log the conversion for debugging
                Log.d(TAG, String.format(
                        "Kickoff Time Conversion - API: %s:%s (UTC-7) -> User: %s (%s)",
                        hour, minute, userTime, userTimeZone.getID()
                ));

                return userTime;
            }
            return "TBD";
        } catch (Exception e) {
            Log.e(TAG, "Error parsing kickoff time", e);
            return "TBD";
        }
    }

    // Replace your existing parseMatchTimestamp method with this enhanced version
    private long parseMatchTimestamp(JsonObject eventObj) {
        try {
            String esd = getJsonString(eventObj, "Esd");
            if (esd != null && esd.length() == 14) {
                String year = esd.substring(0, 4);
                String month = esd.substring(4, 6);
                String day = esd.substring(6, 8);
                String hour = esd.substring(8, 10);
                String minute = esd.substring(10, 12);
                String second = esd.substring(12, 14);

                // API timezone is UTC-7
                TimeZone apiTimeZone = TimeZone.getTimeZone("GMT-7");

                // Create date in API timezone
                SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                apiFormat.setTimeZone(apiTimeZone);

                String apiTimeString = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
                Date apiDate = apiFormat.parse(apiTimeString);

                Log.d(TAG, String.format(
                        "Match Timestamp - API: %s (UTC-7) -> UTC: %d",
                        apiTimeString, apiDate.getTime()
                ));

                return apiDate.getTime();
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing match timestamp", e);
            return 0;
        }
    }

    // Add this helper method to get timezone information (optional - for debugging)
    private void logTimezoneInfo() {
        TimeZone userTz = TimeZone.getDefault();
        TimeZone apiTz = TimeZone.getTimeZone("GMT-7");

        int userOffset = userTz.getRawOffset() / (1000 * 60 * 60);
        int apiOffset = apiTz.getRawOffset() / (1000 * 60 * 60);
        int timeDifference = userOffset - apiOffset;

        Log.d(TAG, String.format(
                "Timezone Info - API: UTC%+d | User: %s (UTC%+d) | Difference: %+d hours",
                apiOffset, userTz.getID(), userOffset, timeDifference
        ));
    }

    // Add this method to test specific time conversions (optional - for debugging)
    private void testTimeConversion() {
        Log.d(TAG, "=== Testing Time Conversion ===");
        logTimezoneInfo();

        try {
            // Test case: 12:00 in API timezone should be 19:00 in GMT
            SimpleDateFormat apiFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            apiFormat.setTimeZone(TimeZone.getTimeZone("GMT-7"));

            SimpleDateFormat userFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            userFormat.setTimeZone(TimeZone.getDefault());

            // Create test date at 12:00 in API timezone
            Calendar testCal = Calendar.getInstance();
            testCal.set(Calendar.HOUR_OF_DAY, 12);
            testCal.set(Calendar.MINUTE, 0);
            testCal.setTimeZone(TimeZone.getTimeZone("GMT-7"));

            String convertedTime = userFormat.format(testCal.getTime());

            Log.d(TAG, String.format("Test: 12:00 API time -> %s user time", convertedTime));

        } catch (Exception e) {
            Log.e(TAG, "Error in time conversion test", e);
        }
    }

    // Call this in onCreate if you want to test conversions (optional)
    private void initializeTimezoneHandling() {
        // Log timezone information
        logTimezoneInfo();

        // Test time conversion
        testTimeConversion();
    }


    private String getJsonString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }

    private void updateFavoritesStatus() {
        for (Match match : allMatches) {
            match.setFavorite(favoriteMatchIds.contains(match.getId()));
        }
    }

    private void updateUI() {
        if (allMatches.isEmpty()) {
            showNoMatches();
            return;
        }

        hideNoMatches();

        // Update favorites (only show if not in live mode)
        List<Match> favoriteMatches = new ArrayList<>();
        if (!isLiveMode) {
            for (Match match : allMatches) {
                if (match.isFavorite()) {
                    favoriteMatches.add(match);
                }
            }
        }
        updateFavoritesSection(favoriteMatches);

        // Update live matches
        List<Match> liveMatches = new ArrayList<>();
        for (Match match : allMatches) {
            if (match.isLive()) {
                liveMatches.add(match);
            }
        }
        updateSection(liveSection, liveAdapter, liveMatches);

        // Update scheduled matches (hide in live mode)
        List<Match> scheduledMatches = new ArrayList<>();
        if (!isLiveMode) {
            for (Match match : allMatches) {
                if (!match.isLive()) {
                    scheduledMatches.add(match);
                }
            }
        }
        updateSection(scheduledSection, scheduledAdapter, scheduledMatches);
    }

    private void updateSection(LinearLayout section, MatchAdapter adapter, List<Match> matches) {
        if (matches.isEmpty()) {
            section.setVisibility(View.GONE);
        } else {
            section.setVisibility(View.VISIBLE);
            adapter.updateMatches(matches);
        }
    }

    private void updateFavoritesSection(List<Match> favoriteMatches) {
        if (favoriteMatches.isEmpty()) {
            favoritesSection.setVisibility(View.GONE);
        } else {
            favoritesSection.setVisibility(View.VISIBLE);
            favoritesAdapter.updateMatches(favoriteMatches);
        }
    }

    private void showNoMatches() {
        noMatchesLayout.setVisibility(View.VISIBLE);
        favoritesSection.setVisibility(View.GONE);
        liveSection.setVisibility(View.GONE);
        scheduledSection.setVisibility(View.GONE);
    }

    private void hideNoMatches() {
        noMatchesLayout.setVisibility(View.GONE);
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String favoritesJson = prefs.getString(FAVORITES_KEY, "[]");

        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Set<String>>(){}.getType();
            Set<String> loadedFavorites = gson.fromJson(favoritesJson, type);
            if (loadedFavorites != null) {
                favoriteMatchIds.clear();
                favoriteMatchIds.addAll(loadedFavorites);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading favorites", e);
        }
    }

    private void saveFavorites() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String favoritesJson = gson.toJson(favoriteMatchIds);
        editor.putString(FAVORITES_KEY, favoritesJson);
        editor.apply();
    }

    @Override
    public void onFavoriteClick(Match match, int position) {
        if (match.isFavorite()) {
            // Remove from favorites
            favoriteMatchIds.remove(match.getId());
            match.setFavorite(false);
            Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show();
        } else {
            // Add to favorites
            favoriteMatchIds.add(match.getId());
            match.setFavorite(true);
            Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show();
        }

        saveFavorites();

        // Update all adapters
        favoritesAdapter.notifyDataSetChanged();
        liveAdapter.notifyDataSetChanged();
        scheduledAdapter.notifyDataSetChanged();

        // Update UI to show/hide favorites section
        updateUI();
    }


    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, MatchNotificationService.class);
        startService(serviceIntent);
    }

    private void stopNotificationService() {
        Intent serviceIntent = new Intent(this, MatchNotificationService.class);
        stopService(serviceIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNotificationService();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // DateTab inner class
    private static class DateTab {
        String dayName;
        String dateDisplay;
        String dateValue;
    }
}