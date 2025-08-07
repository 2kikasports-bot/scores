package com.kikasports.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "LiveScore_Channel";
    private static final String CHANNEL_NAME = "Live Score Notifications";
    private static final int NOTIFICATION_ID = 1001;

    // Notification types
    public static final String TYPE_KICKOFF_REMINDER = "kickoff_reminder";
    public static final String TYPE_KICKOFF = "kickoff";
    public static final String TYPE_GOAL = "goal";
    public static final String TYPE_HALF_TIME = "half_time";
    public static final String TYPE_FULL_TIME = "full_time";
    public static final String TYPE_INCIDENT = "incident";

    @Override
    public void onReceive(Context context, Intent intent) {
        String matchInfo = intent.getStringExtra("match_info");
        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");
        String matchId = intent.getStringExtra("match_id");

        if (matchInfo != null && title != null) {
            showNotification(context, title, matchInfo, type, matchId);
        }
    }

    private void showNotification(Context context, String title, String content, String type, String matchId) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager);

        // Create intent to open app when notification is tapped
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.putExtra("match_id", matchId);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Get appropriate icon and priority based on notification type
        int icon = getNotificationIcon(type);
        int priority = getNotificationPriority(type);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content));

        // Use unique notification ID for each type
        int notificationId = NOTIFICATION_ID + matchId.hashCode() + type.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for favorite match updates");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            notificationManager.createNotificationChannel(channel);
        }
    }

    private int getNotificationIcon(String type) {
        switch (type) {
            case TYPE_GOAL:
                return android.R.drawable.star_on;
            case TYPE_KICKOFF:
            case TYPE_KICKOFF_REMINDER:
                return android.R.drawable.ic_media_play;
            case TYPE_HALF_TIME:
            case TYPE_FULL_TIME:
                return android.R.drawable.ic_media_pause;
            default:
                return android.R.drawable.ic_dialog_info;
        }
    }

    private int getNotificationPriority(String type) {
        switch (type) {
            case TYPE_GOAL:
                return NotificationCompat.PRIORITY_HIGH;
            case TYPE_KICKOFF:
                return NotificationCompat.PRIORITY_DEFAULT;
            case TYPE_KICKOFF_REMINDER:
                return NotificationCompat.PRIORITY_DEFAULT;
            default:
                return NotificationCompat.PRIORITY_LOW;
        }
    }
}
