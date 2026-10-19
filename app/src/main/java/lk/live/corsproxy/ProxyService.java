package lk.live.corsproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private static final int NOTIFICATION_ID = 12522;
    private static final String CHANNEL_ID = "proxy_server_channel";

    private ProxyServer server;
    private static final int PORT = 12522;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            startForegroundServiceWithNotification();
        } catch (Exception e) {
            // API 34+ throws SecurityException if the manifest-declared
            // foregroundServiceType doesn't have a matching permission.
            Log.e(TAG, "startForeground failed", e);
            Toast.makeText(this,
                "proxy service blocked: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        startServer();
    }

    private void startForegroundServiceWithNotification() {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Proxy Server Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for Proxy Server");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // Create intent to open MainActivity when notification is clicked.
        // Build it locally rather than relying on the static
        // ProxyManager.notificationIntent, which is null when the OS restarts
        // the service and would NPE PendingIntent.getActivity.
        PendingIntent pendingIntent = buildTapIntent();

        // Build notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proxy Server Running")
            .setContentText("Port: " + PORT + " * Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // Make it non-dismissable
            .build();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startServer() {
        try {
            server = new ProxyServer(PORT);
            Log.d(TAG, "Proxy server started on port " + PORT);

            // Update notification with server status
            updateNotification("Proxy Server Running on port " + PORT);

            Toast.makeText(this, "Proxy server started on port " + PORT, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
            Toast.makeText(this, "Failed to start server: " + e.getMessage(), 
						   Toast.LENGTH_SHORT).show();
            stopSelf(); // Stop service if server fails to start
        }
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        PendingIntent pendingIntent = buildTapIntent();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proxy Server")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();

        manager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            Log.d(TAG, "Proxy server stopped");
        }

        // Remove notification
        // Use the AndroidX compat shim; it maps to the new STOP_FOREGROUND_REMOVE
        // flag on API 24+ and the old boolean overload on older APIs.
        androidx.core.app.ServiceCompat.stopForeground(this,
            androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE);

        Toast.makeText(this, "Proxy server stopped", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Don't auto-restart on process death. The user explicitly starts/stops
        // this via the "Local" proxy toggle; a silent OS restart would surface
        // as a mystery notification.
        return START_NOT_STICKY;
    }

    /** Build a PendingIntent that opens MainActivity when the user taps the
     *  foreground-service notification. Never null; survives static-field loss
     *  if the OS restarts us. */
    private PendingIntent buildTapIntent() {
        Intent launch;
        try {
            launch = new Intent(this,
                Class.forName("lk.live.aplusewings.MainActivity"));
        } catch (ClassNotFoundException e) {
            launch = new Intent();
            launch.setPackage(getPackageName());
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(this, 0, launch, flags);
    }
}

