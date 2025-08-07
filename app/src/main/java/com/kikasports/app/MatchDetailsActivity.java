package com.kikasports.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MatchDetailsActivity extends AppCompatActivity {
    private static final String TAG = "MatchDetailsActivity";
    private static final String API_KEY = "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a";
    private static final String API_HOST = "livescore-real-time.p.rapidapi.com";

    // UI Components
    private TextView homeTeamName, awayTeamName, homeScore, awayScore, matchTime, matchStatus;
    private TextView competitionName, stadiumName, matchDate;
    private ImageView homeTeamLogo, awayTeamLogo, backButton;
    private ProgressBar loadingProgress;
    private LinearLayout lineupsLayout, statisticsLayout, incidentsLayout;

    ScrollView contentLayout;
    private LinearLayout homeLineupsContainer, awayLineupsContainer;
    private LinearLayout homeStatsContainer, awayStatsContainer;
    private LinearLayout incidentsContainer;

    // Tab buttons
    private TextView tabLineups, tabStats, tabIncidents;
    private View lineupsIndicator, statsIndicator, incidentsIndicator;

    // Data
    private String matchId;
    private Match currentMatch;
    private OkHttpClient client = new OkHttpClient();
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Current tab
    private int currentTab = 0; // 0: lineups, 1: stats, 2: incidents

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_details);

        // Get match data from intent
        Intent intent = getIntent();
        if (intent.hasExtra("match")) {
            currentMatch = (Match) intent.getSerializableExtra("match");
            matchId = currentMatch.getId();
            Log.d(TAG, "Match ID from intent: " + matchId);
        } else {
            matchId = intent.getStringExtra("match_id");
            Log.d(TAG, "Match ID from string: " + matchId);
            if (matchId == null) {
                Toast.makeText(this, "Match not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        initViews();
        setupToolbar();
        setupTabs();
        loadMatchDetails();
    }

    private void initViews() {
        Log.d(TAG, "Initializing views...");
        // Header views
        homeTeamName = findViewById(R.id.homeTeamName);
        awayTeamName = findViewById(R.id.awayTeamName);
        homeScore = findViewById(R.id.homeScore);
        awayScore = findViewById(R.id.awayScore);
        matchTime = findViewById(R.id.matchTime);
        matchStatus = findViewById(R.id.matchStatus);
        competitionName = findViewById(R.id.competitionName);
        stadiumName = findViewById(R.id.stadiumName);
        matchDate = findViewById(R.id.matchDate);
        homeTeamLogo = findViewById(R.id.homeTeamLogo);
        awayTeamLogo = findViewById(R.id.awayTeamLogo);
        backButton = findViewById(R.id.backButton);

        // Layout containers
        loadingProgress = findViewById(R.id.loadingProgress);
        contentLayout = findViewById(R.id.contentLayout);
        lineupsLayout = findViewById(R.id.lineupsLayout);
        statisticsLayout = findViewById(R.id.statisticsLayout);
        incidentsLayout = findViewById(R.id.incidentsLayout);

        // Content containers
        homeLineupsContainer = findViewById(R.id.homeLineupsContainer);
        awayLineupsContainer = findViewById(R.id.awayLineupsContainer);
        homeStatsContainer = findViewById(R.id.homeStatsContainer);
        awayStatsContainer = findViewById(R.id.awayStatsContainer);
        incidentsContainer = findViewById(R.id.incidentsContainer);

        // Tab views
        tabLineups = findViewById(R.id.tabLineups);
        tabStats = findViewById(R.id.tabStats);
        tabIncidents = findViewById(R.id.tabIncidents);
        lineupsIndicator = findViewById(R.id.lineupsIndicator);
        statsIndicator = findViewById(R.id.statsIndicator);
        incidentsIndicator = findViewById(R.id.incidentsIndicator);

        // Log null views
        if (homeLineupsContainer == null) Log.e(TAG, "homeLineupsContainer is null!");
        if (awayLineupsContainer == null) Log.e(TAG, "awayLineupsContainer is null!");
        if (homeStatsContainer == null) Log.e(TAG, "homeStatsContainer is null!");
        if (awayStatsContainer == null) Log.e(TAG, "awayStatsContainer is null!");
        if (incidentsContainer == null) Log.e(TAG, "incidentsContainer is null!");
    }

    private void setupToolbar() {
        backButton.setOnClickListener(v -> finish());
    }

    private void setupTabs() {
        tabLineups.setOnClickListener(v -> switchTab(0));
        tabStats.setOnClickListener(v -> switchTab(1));
        tabIncidents.setOnClickListener(v -> switchTab(2));

        // Set default tab
        switchTab(0);
    }

    private void switchTab(int tabIndex) {
        Log.d(TAG, "Switching to tab: " + tabIndex);
        currentTab = tabIndex;

        // Reset all tabs
        tabLineups.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        tabStats.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        tabIncidents.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        lineupsIndicator.setVisibility(View.INVISIBLE);
        statsIndicator.setVisibility(View.INVISIBLE);
        incidentsIndicator.setVisibility(View.INVISIBLE);

        // Hide all content
        lineupsLayout.setVisibility(View.GONE);
        statisticsLayout.setVisibility(View.GONE);
        incidentsLayout.setVisibility(View.GONE);

        // Show selected tab
        switch (tabIndex) {
            case 0:
                tabLineups.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                lineupsIndicator.setVisibility(View.VISIBLE);
                lineupsLayout.setVisibility(View.VISIBLE);
                Log.d(TAG, "Loading lineups...");
                loadLineups();
                break;
            case 1:
                tabStats.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                statsIndicator.setVisibility(View.VISIBLE);
                statisticsLayout.setVisibility(View.VISIBLE);
                Log.d(TAG, "Loading statistics...");
                loadStatistics();
                break;
            case 2:
                tabIncidents.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                incidentsIndicator.setVisibility(View.VISIBLE);
                incidentsLayout.setVisibility(View.VISIBLE);
                Log.d(TAG, "Loading incidents...");
                loadIncidents();
                break;
        }
    }

    private void loadMatchDetails() {
        Log.d(TAG, "Loading match details for ID: " + matchId);
        showLoading(true);

        // Set basic match info if available
        if (currentMatch != null) {
            updateMatchHeader(currentMatch);
        }

        // Load additional match info
        executor.execute(() -> {
            try {
                String url = "https://livescore-real-time.p.rapidapi.com/matches/get-info?eid=" + matchId;
                Log.d(TAG, "Match info URL: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", API_KEY)
                        .addHeader("x-rapidapi-host", API_HOST)
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Match info request failed: " + response.code() + " - " + response.message());
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(MatchDetailsActivity.this, "Failed to load match details: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Match info response: " + jsonResponse);

                parseMatchInfo(jsonResponse);

                mainHandler.post(() -> {
                    showLoading(false);
                    Log.d(TAG, "Match details loaded, loading first tab content");
                    loadLineups(); // Load first tab content
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading match info", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(MatchDetailsActivity.this, "Failed to load match details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateMatchHeader(Match match) {
        Log.d(TAG, "Updating match header");
        homeTeamName.setText(match.getHomeTeam());
        awayTeamName.setText(match.getAwayTeam());
        competitionName.setText(match.getCompetition());
        matchTime.setText(match.getTime());

        if (match.isLive()) {
            homeScore.setText(match.getHomeScore());
            awayScore.setText(match.getAwayScore());
            matchStatus.setText("LIVE");
            matchStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else {
            homeScore.setText("-");
            awayScore.setText("-");
            matchStatus.setText(match.getStatus());
        }

        // Load team logos
        if (match.getHomeTeamLogo() != null) {
            Glide.with(this)
                    .load(match.getHomeTeamLogo())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(homeTeamLogo);
        }

        if (match.getAwayTeamLogo() != null) {
            Glide.with(this)
                    .load(match.getAwayTeamLogo())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(awayTeamLogo);
        }
    }

    private void parseMatchInfo(String jsonResponse) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
            Log.d(TAG, "Parsed match info JSON successfully");

            // Stadium info
            String venue = getJsonString(jsonObject, "Vnm");
            String city = getJsonString(jsonObject, "Vcy");
            String country = getJsonString(jsonObject, "VCnm");

            mainHandler.post(() -> {
                if (venue != null) {
                    String stadiumText = venue;
                    if (city != null) stadiumText += ", " + city;
                    if (country != null) stadiumText += ", " + country;
                    stadiumName.setText(stadiumText);
                }

                // Format match date
                String dateStr = getJsonString(jsonObject, "Esd");
                if (dateStr != null && dateStr.length() >= 8) {
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                        SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        Date date = inputFormat.parse(dateStr);
                        matchDate.setText(outputFormat.format(date));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing date: " + dateStr, e);
                        matchDate.setText("");
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error parsing match info", e);
        }
    }

    private void loadLineups() {
        Log.d(TAG, "loadLineups called, container child count: " +
                (homeLineupsContainer != null ? homeLineupsContainer.getChildCount() : "container is null"));

        if (homeLineupsContainer != null && homeLineupsContainer.getChildCount() > 0) {
            Log.d(TAG, "Lineups already loaded, skipping");
            return; // Already loaded
        }

        executor.execute(() -> {
            try {
                String url = "https://livescore-real-time.p.rapidapi.com/matches/get-lineups?eid=" + matchId;
                Log.d(TAG, "Lineups URL: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", API_KEY)
                        .addHeader("x-rapidapi-host", API_HOST)
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Lineups request failed: " + response.code() + " - " + response.message());
                    mainHandler.post(() -> {
                        addNoDataView(homeLineupsContainer, "Failed to load lineups: " + response.code());
                        addNoDataView(awayLineupsContainer, "Failed to load lineups: " + response.code());
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Lineups response: " + jsonResponse);

                parseLineups(jsonResponse);

            } catch (IOException e) {
                Log.e(TAG, "Error loading lineups", e);
                mainHandler.post(() -> {
                    addNoDataView(homeLineupsContainer, "Network error: " + e.getMessage());
                    addNoDataView(awayLineupsContainer, "Network error: " + e.getMessage());
                });
            }
        });
    }

    private void parseLineups(String jsonResponse) {
        try {
            Log.d(TAG, "Parsing lineups JSON...");
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            List<Player> homePlayers = new ArrayList<>();
            List<Player> awayPlayers = new ArrayList<>();

            if (jsonObject.has("Lu") && jsonObject.get("Lu").isJsonArray()) {
                JsonArray luArray = jsonObject.getAsJsonArray("Lu");
                Log.d(TAG, "Found Lu array with " + luArray.size() + " elements");

                for (JsonElement luElement : luArray) {
                    JsonObject luObj = luElement.getAsJsonObject();
                    int teamNumber = getJsonInt(luObj, "Tnb", -1);
                    Log.d(TAG, "Processing team number: " + teamNumber);

                    if (luObj.has("Ps") && luObj.get("Ps").isJsonArray()) {
                        JsonArray psArray = luObj.getAsJsonArray("Ps");
                        Log.d(TAG, "Found " + psArray.size() + " players for team " + teamNumber);

                        for (JsonElement psElement : psArray) {
                            JsonObject playerObj = psElement.getAsJsonObject();

                            Player player = new Player();
                            player.firstName = getJsonString(playerObj, "Fn");
                            player.lastName = getJsonString(playerObj, "Ln");
                            player.position = getJsonString(playerObj, "Pon");
                            player.number = getJsonInt(playerObj, "Snu", 0);
                            player.rating = getJsonString(playerObj, "Rate");

                            Log.d(TAG, "Player: " + player.firstName + " " + player.lastName + " (#" + player.number + ")");

                            if (teamNumber == 1) {
                                homePlayers.add(player);
                            } else if (teamNumber == 2) {
                                awayPlayers.add(player);
                            }
                        }
                    } else {
                        Log.d(TAG, "No Ps array found for team " + teamNumber);
                    }
                }
            } else {
                Log.d(TAG, "No Lu array found in response");
            }

            Log.d(TAG, "Found " + homePlayers.size() + " home players, " + awayPlayers.size() + " away players");

            mainHandler.post(() -> {
                displayLineups(homePlayers, awayPlayers);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error parsing lineups", e);
            mainHandler.post(() -> {
                addNoDataView(homeLineupsContainer, "Error parsing lineups: " + e.getMessage());
                addNoDataView(awayLineupsContainer, "Error parsing lineups: " + e.getMessage());
            });
        }
    }

    private void displayLineups(List<Player> homePlayers, List<Player> awayPlayers) {
        Log.d(TAG, "Displaying lineups - Home: " + homePlayers.size() + ", Away: " + awayPlayers.size());

        if (homeLineupsContainer == null || awayLineupsContainer == null) {
            Log.e(TAG, "Lineup containers are null!");
            return;
        }

        // Clear existing views
        homeLineupsContainer.removeAllViews();
        awayLineupsContainer.removeAllViews();

        // Display home team lineup
        for (Player player : homePlayers) {
            View playerView = createPlayerView(player);
            homeLineupsContainer.addView(playerView);
        }

        // Display away team lineup
        for (Player player : awayPlayers) {
            View playerView = createPlayerView(player);
            awayLineupsContainer.addView(playerView);
        }

        if (homePlayers.isEmpty()) {
            addNoDataView(homeLineupsContainer, "No home team lineup data available");
        }
        if (awayPlayers.isEmpty()) {
            addNoDataView(awayLineupsContainer, "No away team lineup data available");
        }

        Log.d(TAG, "Lineups displayed successfully");
    }

    private View createPlayerView(Player player) {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        TextView name = view.findViewById(android.R.id.text1);
        TextView details = view.findViewById(android.R.id.text2);

        String fullName = (player.firstName != null ? player.firstName : "") + " " +
                (player.lastName != null ? player.lastName : "");
        name.setText("#" + player.number + " " + fullName.trim());

        String detailsText = "";
        if (player.position != null) detailsText += player.position;
        if (player.rating != null) detailsText += (detailsText.isEmpty() ? "" : " • ") + "Rating: " + player.rating;
        details.setText(detailsText);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        view.setLayoutParams(params);

        return view;
    }

    private void loadStatistics() {
        Log.d(TAG, "loadStatistics called, container child count: " +
                (homeStatsContainer != null ? homeStatsContainer.getChildCount() : "container is null"));

        if (homeStatsContainer != null && homeStatsContainer.getChildCount() > 0) {
            Log.d(TAG, "Statistics already loaded, skipping");
            return; // Already loaded
        }

        executor.execute(() -> {
            try {
                String url = "https://livescore-real-time.p.rapidapi.com/matches/get-statistics?eid=" + matchId;
                Log.d(TAG, "Statistics URL: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", API_KEY)
                        .addHeader("x-rapidapi-host", API_HOST)
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Statistics request failed: " + response.code() + " - " + response.message());
                    mainHandler.post(() -> {
                        addNoDataView(homeStatsContainer, "Failed to load statistics: " + response.code());
                        addNoDataView(awayStatsContainer, "Failed to load statistics: " + response.code());
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Statistics response: " + jsonResponse);

                parseStatistics(jsonResponse);

            } catch (IOException e) {
                Log.e(TAG, "Error loading statistics", e);
                mainHandler.post(() -> {
                    addNoDataView(homeStatsContainer, "Network error: " + e.getMessage());
                    addNoDataView(awayStatsContainer, "Network error: " + e.getMessage());
                });
            }
        });
    }

    private void parseStatistics(String jsonResponse) {
        try {
            Log.d(TAG, "Parsing statistics JSON...");
            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(jsonResponse, JsonArray.class);

            List<Statistic> homeStats = new ArrayList<>();
            List<Statistic> awayStats = new ArrayList<>();

            Log.d(TAG, "Statistics array size: " + jsonArray.size());

            for (JsonElement element : jsonArray) {
                JsonObject statObj = element.getAsJsonObject();
                int teamNumber = getJsonInt(statObj, "Tnb", -1);
                Log.d(TAG, "Processing statistics for team: " + teamNumber);

                Statistic stat = new Statistic();
                stat.fouls = getJsonInt(statObj, "Fls", 0);
                stat.offsides = getJsonInt(statObj, "Ofs", 0);
                stat.corners = getJsonInt(statObj, "Crs", 0);
                stat.yellowCards = getJsonInt(statObj, "Ycs", 0);
                stat.redCards = getJsonInt(statObj, "Rcs", 0);
                stat.shotsOff = getJsonInt(statObj, "Shof", 0);
                stat.shotsWide = getJsonInt(statObj, "Shwd", 0);
                stat.shotsBlocked = getJsonInt(statObj, "Shbl", 0);
                stat.shotsOn = getJsonInt(statObj, "Shon", 0);
                stat.possession = getJsonInt(statObj, "Pss", 0);

                if (teamNumber == 1) {
                    homeStats.add(stat);
                } else if (teamNumber == 2) {
                    awayStats.add(stat);
                }
            }

            Log.d(TAG, "Found " + homeStats.size() + " home stats, " + awayStats.size() + " away stats");

            mainHandler.post(() -> {
                displayStatistics(homeStats, awayStats);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error parsing statistics", e);
            mainHandler.post(() -> {
                addNoDataView(homeStatsContainer, "Error parsing statistics: " + e.getMessage());
                addNoDataView(awayStatsContainer, "Error parsing statistics: " + e.getMessage());
            });
        }
    }

    private void displayStatistics(List<Statistic> homeStats, List<Statistic> awayStats) {
        Log.d(TAG, "Displaying statistics - Home: " + homeStats.size() + ", Away: " + awayStats.size());

        if (statisticsLayout == null) {
            Log.e(TAG, "Statistics layout is null!");
            return;
        }

        // Clear existing views
        statisticsLayout.removeAllViews();

        if (homeStats.isEmpty() || awayStats.isEmpty()) {
            addNoDataView(statisticsLayout, "No statistics available");
            return;
        }

        Statistic homeStat = homeStats.get(0);
        Statistic awayStat = awayStats.get(0);

        // Create stat comparison views
        addStatComparisonView("Possession", homeStat.possession + "%", awayStat.possession + "%");
        addStatComparisonView("Shots on Target", String.valueOf(homeStat.shotsOn), String.valueOf(awayStat.shotsOn));
        addStatComparisonView("Shots off Target", String.valueOf(homeStat.shotsOff), String.valueOf(awayStat.shotsOff));
        addStatComparisonView("Corners", String.valueOf(homeStat.corners), String.valueOf(awayStat.corners));
        addStatComparisonView("Fouls", String.valueOf(homeStat.fouls), String.valueOf(awayStat.fouls));
        addStatComparisonView("Yellow Cards", String.valueOf(homeStat.yellowCards), String.valueOf(awayStat.yellowCards));
        addStatComparisonView("Red Cards", String.valueOf(homeStat.redCards), String.valueOf(awayStat.redCards));
        addStatComparisonView("Offsides", String.valueOf(homeStat.offsides), String.valueOf(awayStat.offsides));

        Log.d(TAG, "Statistics displayed successfully");
    }

    private void addStatComparisonView(String statName, String homeValue, String awayValue) {
        // Check if the layout exists
        if (getLayoutInflater() == null) {
            Log.e(TAG, "Layout inflater is null");
            return;
        }

        try {
            View view = getLayoutInflater().inflate(R.layout.stat_comparison_item, statisticsLayout, false);

            TextView statNameView = view.findViewById(R.id.statName);
            TextView homeStatValue = view.findViewById(R.id.homeStatValue);
            TextView awayStatValue = view.findViewById(R.id.awayStatValue);

            if (statNameView != null) statNameView.setText(statName);
            if (homeStatValue != null) homeStatValue.setText(homeValue);
            if (awayStatValue != null) awayStatValue.setText(awayValue);

            statisticsLayout.addView(view);
        } catch (Exception e) {
            Log.e(TAG, "Error creating stat comparison view for: " + statName, e);
            // Fallback to simple text view
            TextView fallbackView = new TextView(this);
            fallbackView.setText(statName + ": " + homeValue + " - " + awayValue);
            fallbackView.setPadding(16, 8, 16, 8);
            statisticsLayout.addView(fallbackView);
        }
    }

    private void loadIncidents() {
        Log.d(TAG, "loadIncidents called, container child count: " +
                (incidentsContainer != null ? incidentsContainer.getChildCount() : "container is null"));

        if (incidentsContainer != null && incidentsContainer.getChildCount() > 0) {
            Log.d(TAG, "Incidents already loaded, skipping");
            return; // Already loaded
        }

        executor.execute(() -> {
            try {
                String url = "https://livescore-real-time.p.rapidapi.com/matches/get-incidents?eid=" + matchId;
                Log.d(TAG, "Incidents URL: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", API_KEY)
                        .addHeader("x-rapidapi-host", API_HOST)
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Incidents request failed: " + response.code() + " - " + response.message());
                    mainHandler.post(() -> {
                        addNoDataView(incidentsContainer, "Failed to load incidents: " + response.code());
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Incidents response: " + jsonResponse);

                parseIncidents(jsonResponse);

            } catch (IOException e) {
                Log.e(TAG, "Error loading incidents", e);
                mainHandler.post(() -> {
                    addNoDataView(incidentsContainer, "Network error: " + e.getMessage());
                });
            }
        });
    }

    private void parseIncidents(String jsonResponse) {
        try {
            Log.d(TAG, "Parsing incidents JSON...");
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            List<Incident> incidents = new ArrayList<>();

            if (jsonObject.has("Incs") && jsonObject.get("Incs").isJsonArray()) {
                JsonArray incsArray = jsonObject.getAsJsonArray("Incs");
                Log.d(TAG, "Found " + incsArray.size() + " incidents");

                for (JsonElement incElement : incsArray) {
                    JsonObject incObj = incElement.getAsJsonObject();

                    Incident incident = new Incident();
                    incident.minute = getJsonInt(incObj, "Min", 0);
                    incident.type = getJsonInt(incObj, "IT", 0);
                    incident.playerName = getJsonString(incObj, "Pn");

                    Log.d(TAG, "Incident: " + incident.minute + "' " + getIncidentTypeName(incident.type) +
                            " - " + incident.playerName);

                    incidents.add(incident);
                }
            } else {
                Log.d(TAG, "No Incs array found in response");
            }

            Log.d(TAG, "Found " + incidents.size() + " total incidents");

            mainHandler.post(() -> {
                displayIncidents(incidents);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error parsing incidents", e);
            mainHandler.post(() -> {
                addNoDataView(incidentsContainer, "Error parsing incidents: " + e.getMessage());
            });
        }
    }

    private void displayIncidents(List<Incident> incidents) {
        Log.d(TAG, "Displaying " + incidents.size() + " incidents");

        if (incidentsContainer == null) {
            Log.e(TAG, "Incidents container is null!");
            return;
        }

        // Clear existing views
        incidentsContainer.removeAllViews();

        if (incidents.isEmpty()) {
            addNoDataView(incidentsContainer, "No incidents available");
            return;
        }

        for (Incident incident : incidents) {
            View incidentView = createIncidentView(incident);
            incidentsContainer.addView(incidentView);
        }

        Log.d(TAG, "Incidents displayed successfully");
    }

    private View createIncidentView(Incident incident) {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        TextView title = view.findViewById(android.R.id.text1);
        TextView subtitle = view.findViewById(android.R.id.text2);

        String incidentType = getIncidentTypeName(incident.type);
        title.setText(incident.minute + "' " + incidentType);

        if (incident.playerName != null) {
            subtitle.setText(incident.playerName);
        } else {
            subtitle.setVisibility(View.GONE);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        view.setLayoutParams(params);

        return view;
    }

    private String getIncidentTypeName(int type) {
        switch (type) {
            case 1: return "⚽ Goal";
            case 2: return "🟨 Yellow Card";
            case 3: return "🟥 Red Card";
            case 4: return "↔️ Substitution";
            case 36: return "⚽ Goal";
            default: return "Event (Type: " + type + ")";
        }
    }

    private void addNoDataView(LinearLayout container, String message) {
        if (container == null) {
            Log.e(TAG, "Cannot add no data view - container is null");
            return;
        }

        Log.d(TAG, "Adding no data view: " + message);
        TextView noDataText = new TextView(this);
        noDataText.setText(message);
        noDataText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        noDataText.setPadding(32, 32, 32, 32);
        noDataText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        container.addView(noDataText);
    }

    private void showLoading(boolean show) {
        Log.d(TAG, "Show loading: " + show);
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (contentLayout != null) {
            contentLayout.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private int getJsonInt(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                Log.w(TAG, "Error parsing int for key: " + key, e);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed, shutting down executor");
        if (executor != null) {
            executor.shutdown();
        }
    }

    // Data classes
    private static class Player {
        String firstName;
        String lastName;
        String position;
        int number;
        String rating;
    }

    private static class Statistic {
        int fouls;
        int offsides;
        int corners;
        int yellowCards;
        int redCards;
        int shotsOff;
        int shotsWide;
        int shotsBlocked;
        int shotsOn;
        int possession;
    }

    private static class Incident {
        int minute;
        int type;
        String playerName;
    }
}