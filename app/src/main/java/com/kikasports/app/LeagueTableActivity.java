package com.kikasports.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LeagueTableActivity extends AppCompatActivity {
    private static final String TAG = "LeagueTableActivity";
    private static final String API_KEY = "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a";
    private static final String API_HOST = "livescore-real-time.p.rapidapi.com";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView tableRecyclerView;
    private ImageView backButton;
    private TextView leagueTitle;
    
    private LeagueTableAdapter tableAdapter;
    private List<TeamStanding> standings = new ArrayList<>();
    private League currentLeague;
    
    private OkHttpClient client = new OkHttpClient();
    private ExecutorService executor = Executors.newFixedThreadPool(2);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_league_table);

        // Get league from intent
        currentLeague = (League) getIntent().getSerializableExtra("league");
        if (currentLeague == null) {
            Toast.makeText(this, "League not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        loadLeagueTable();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tableRecyclerView = findViewById(R.id.tableRecyclerView);
        backButton = findViewById(R.id.backButton);
        leagueTitle = findViewById(R.id.leagueTitle);

        leagueTitle.setText(currentLeague.getName());
        backButton.setOnClickListener(v -> finish());
        swipeRefresh.setOnRefreshListener(this::loadLeagueTable);
    }

    private void setupRecyclerView() {
        tableRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tableAdapter = new LeagueTableAdapter(this, standings);
        tableRecyclerView.setAdapter(tableAdapter);
    }

    private void loadLeagueTable() {
        swipeRefresh.setRefreshing(true);

        executor.execute(() -> {
            try {
                String url = String.format(
                    "https://livescore-real-time.p.rapidapi.com/leagues/get-table?ccd=%s&scd=%s",
                    currentLeague.getCountryCode(),
                    currentLeague.getSeasonCode()
                );
                Log.d(TAG, "Loading table from: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", API_KEY)
                        .addHeader("x-rapidapi-host", API_HOST)
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Request failed: " + response.code());
                    mainHandler.post(() -> {
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(LeagueTableActivity.this, "Failed to load table", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Response: " + jsonResponse);

                parseLeagueTable(jsonResponse);

                mainHandler.post(() -> {
                    tableAdapter.updateStandings(standings);
                    swipeRefresh.setRefreshing(false);
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading table", e);
                mainHandler.post(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(LeagueTableActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void parseLeagueTable(String jsonResponse) {
        try {
            standings.clear();
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            if (jsonObject.has("Stages") && jsonObject.get("Stages").isJsonArray()) {
                JsonArray stagesArray = jsonObject.getAsJsonArray("Stages");
                
                for (JsonElement stageElement : stagesArray) {
                    JsonObject stageObj = stageElement.getAsJsonObject();
                    
                    if (stageObj.has("LeagueTable") && stageObj.get("LeagueTable").isJsonObject()) {
                        JsonObject leagueTable = stageObj.getAsJsonObject("LeagueTable");
                        
                        if (leagueTable.has("L") && leagueTable.get("L").isJsonArray()) {
                            JsonArray teamsArray = leagueTable.getAsJsonArray("L");
                            
                            for (JsonElement teamElement : teamsArray) {
                                JsonObject teamObj = teamElement.getAsJsonObject();
                                
                                TeamStanding standing = new TeamStanding();
                                standing.setPosition(getJsonInt(teamObj, "Pos", 0));
                                standing.setTeamName(getJsonString(teamObj, "Tnm"));
                                standing.setPlayed(getJsonInt(teamObj, "Pld", 0));
                                standing.setWins(getJsonInt(teamObj, "W", 0));
                                standing.setDraws(getJsonInt(teamObj, "D", 0));
                                standing.setLosses(getJsonInt(teamObj, "L", 0));
                                standing.setGoalsFor(getJsonInt(teamObj, "F", 0));
                                standing.setGoalsAgainst(getJsonInt(teamObj, "A", 0));
                                standing.setGoalDifference(getJsonInt(teamObj, "GD", 0));
                                standing.setPoints(getJsonInt(teamObj, "Pts", 0));
                                
                                // Set team logo if available
                                String logoPath = getJsonString(teamObj, "Img");
                                if (logoPath != null) {
                                    standing.setTeamLogo("https://lsm-static-prod.livescore.com/medium/" + logoPath);
                                }
                                
                                standings.add(standing);
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Parsed " + standings.size() + " team standings");

        } catch (Exception e) {
            Log.e(TAG, "Error parsing league table", e);
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
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}