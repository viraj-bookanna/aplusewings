package lk.live.corsproxy;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ProxyManager {
    
    public static Intent notificationIntent;
	
	public static void startProxyServer(Context ctx, Intent notifi, int PORT) {
		notificationIntent = notifi;
		Intent intent = new Intent(ctx, ProxyService.class);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			ctx.startForegroundService(intent);
		} else {
			ctx.startService(intent);
		}
		Toast.makeText(ctx, "Starting proxy server...", Toast.LENGTH_SHORT).show();
	}

	public static void stopProxyServer(Context ctx) {
		Intent intent = new Intent(ctx, ProxyService.class);
		ctx.stopService(intent);
		Toast.makeText(ctx, "Stopping proxy server...", Toast.LENGTH_SHORT).show();
	}
    
}
