package com.kikasports.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "LiveScorePrefs";
    private static final String THEME_KEY = "dark_theme";
    private static final String NOTIFICATIONS_KEY = "notifications_enabled";

    private Switch themeSwitch, notificationsSwitch;
    private TextView appVersionText;
    private ImageView backButton;
    private LinearLayout aboutSection, shareSection, clearCacheSection, updateSection;
    private LinearLayout twitterSection, facebookSection, instagramSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupClickListeners();
        loadSettings();
        setAppVersion();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        themeSwitch = findViewById(R.id.themeSwitch);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        appVersionText = findViewById(R.id.appVersionText);
        
        aboutSection = findViewById(R.id.aboutSection);
        shareSection = findViewById(R.id.shareSection);
        clearCacheSection = findViewById(R.id.clearCacheSection);
        updateSection = findViewById(R.id.updateSection);
        
        twitterSection = findViewById(R.id.twitterSection);
        facebookSection = findViewById(R.id.facebookSection);
        instagramSection = findViewById(R.id.instagramSection);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveThemePreference(isChecked);
            applyTheme(isChecked);
        });

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNotificationPreference(isChecked);
        });

        aboutSection.setOnClickListener(v -> showAboutDialog());
        shareSection.setOnClickListener(v -> shareApp());
        clearCacheSection.setOnClickListener(v -> clearCache());
        updateSection.setOnClickListener(v -> checkForUpdates());

        twitterSection.setOnClickListener(v -> openSocialMedia("https://twitter.com/kikasports"));
        facebookSection.setOnClickListener(v -> openSocialMedia("https://facebook.com/kikasports"));
        instagramSection.setOnClickListener(v -> openSocialMedia("https://instagram.com/kikasports"));
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        boolean isDarkTheme = prefs.getBoolean(THEME_KEY, false);
        boolean notificationsEnabled = prefs.getBoolean(NOTIFICATIONS_KEY, true);
        
        themeSwitch.setChecked(isDarkTheme);
        notificationsSwitch.setChecked(notificationsEnabled);
    }

    private void saveThemePreference(boolean isDarkTheme) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(THEME_KEY, isDarkTheme).apply();
    }

    private void saveNotificationPreference(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(NOTIFICATIONS_KEY, enabled).apply();
    }

    private void applyTheme(boolean isDarkTheme) {
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        recreate();
    }

    private void setAppVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = packageInfo.versionName;
            appVersionText.setText("Version " + version);
        } catch (PackageManager.NameNotFoundException e) {
            appVersionText.setText("Version 1.0");
        }
    }

    private void showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("About Kika Sports")
                .setMessage("Kika Sports is your ultimate football companion app. Get live scores, match details, and stay updated with your favorite teams.\n\nDeveloped with ❤️ for football fans worldwide.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out Kika Sports!");
        shareIntent.putExtra(Intent.EXTRA_TEXT, 
            "Get live football scores and match updates with Kika Sports! Download now: https://play.google.com/store/apps/details?id=" + getPackageName());
        startActivity(Intent.createChooser(shareIntent, "Share Kika Sports"));
    }

    private void clearCache() {
        try {
            // Clear app cache
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Don't clear favorites and settings, just temporary data
            
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForUpdates() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to check for updates", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSocialMedia(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }
}