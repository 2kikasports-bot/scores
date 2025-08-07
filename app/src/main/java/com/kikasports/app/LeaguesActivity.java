package com.kikasports.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
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

public class LeaguesActivity extends AppCompatActivity {
    private static final String TAG = "LeaguesActivity";
    private static final String API_KEY = "3e4e229987mshc9d7c332a212122p1cb46bjsn7813e8b7875a";
    private static final String API_HOST = "livescore-real-time.p.rapidapi.com";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView leaguesRecyclerView;
    private ImageView backButton;
    
    private LeaguesAdapter leaguesAdapter;
    private List<League> leagues = new ArrayList<>();
    
    private OkHttpClient client = new OkHttpClient();
    private ExecutorService executor = Executors.newFixedThreadPool(2);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leagues);

        initViews();
        setupRecyclerView();
        loadLeagues();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        leaguesRecyclerView = findViewById(R.id.leaguesRecyclerView);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());
        swipeRefresh.setOnRefreshListener(this::loadLeagues);
    }

    private void setupRecyclerView() {
        leaguesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        leaguesAdapter = new LeaguesAdapter(this, leagues);
        leaguesRecyclerView.setAdapter(leaguesAdapter);
    }

    private void loadLeagues() {
        swipeRefresh.setRefreshing(true);

        executor.execute(() -> {
            try {
                String url = "https://livescore-real-time.p.rapidapi.com/leagues/list-popular";
                Log.d(TAG, "Loading leagues from: " + url);

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
                        Toast.makeText(LeaguesActivity.this, "Failed to load leagues", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String jsonResponse = response.body().string();
                Log.d(TAG, "Response: " + jsonResponse);

                parseLeagues(jsonResponse);

                mainHandler.post(() -> {
                    leaguesAdapter.updateLeagues(leagues);
                    swipeRefresh.setRefreshing(false);
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading leagues", e);
                mainHandler.post(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(LeaguesActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void parseLeagues(String jsonResponse) {
        try {
            leagues.clear();
            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(jsonResponse, JsonArray.class);

            for (JsonElement element : jsonArray) {
                JsonObject leagueObj = element.getAsJsonObject();
                
                League league = new League();
                league.setId(getJsonString(leagueObj, "Cid"));
                league.setName(getJsonString(leagueObj, "Cnm"));
                league.setCountry(getJsonString(leagueObj, "Ccn"));
                league.setCountryCode(getJsonString(leagueObj, "Ccd"));
                league.setSeasonCode(getJsonString(leagueObj, "Scd"));
                
                // Set logo URL if available
                String logoPath = getJsonString(leagueObj, "Img");
                if (logoPath != null) {
                    league.setLogoUrl("https://lsm-static-prod.livescore.com/medium/" + logoPath);
                }

                leagues.add(league);
            }

            Log.d(TAG, "Parsed " + leagues.size() + " leagues");

        } catch (Exception e) {
            Log.e(TAG, "Error parsing leagues", e);
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}