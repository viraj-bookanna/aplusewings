package lk.live.aplusewings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import lk.live.security.tools;
import org.json.JSONObject;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity implements AsyncResponse {
    SharedPreferences.Editor editor;
    private int configFetchAttempt = 0;
    private static final int CONFIG_MAX_ATTEMPTS = 4;

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_splash);
        this.editor = getSharedPreferences("aplusewings", 0).edit();
        SharedPreferences sharedPreferences = getSharedPreferences("aplusewings", 0);
        try {
            String string = Settings.Secure.getString(getContentResolver(), "android_id");
            this.editor.putString("device_id", string);
            this.editor.apply();
        } catch (Exception e) {
        }
        // Compute APK SHA-256 OFF the main thread (it forks `pm path` + copies
        // base.apk to external storage + streams a hash - easily 1-3s on big
        // APKs / slow IO). Cache the result keyed by the installed package's
        // lastUpdateTime so we only recompute after an actual update.
        computeApkSignatureAsync(sharedPreferences);
        ((TextView) findViewById(R.id.splash_live)).setText("LIvE");

        // Did we get launched by a "<brand>-player://..." deep link?
        DeepLinkRouter.Result dl = DeepLinkRouter.parse(getIntent());
        long startDelay = 2500;
        if (dl != null) {
            // Make the brand the active one and stash the pending work for MainActivity.
            this.editor.putString("default_app", dl.brand);
            if (DeepLinkRouter.KIND_LESSON.equals(dl.kind)) {
                if (dl.code != null)     this.editor.putString("deeplink_code", dl.code);
                if (dl.lessonId != null) this.editor.putString("deeplink_lesson_id", dl.lessonId);
            } else if (DeepLinkRouter.KIND_CALLBACK.equals(dl.kind)) {
                // Treat the website-issued accessToken as our brand auth. student_id
                // will be backfilled by MainActivity via a `me { _id }` GQL call.
                // Store the raw token to match the existing merged-app convention
                // (LoginActivity also stores it without a "Bearer " prefix).
                this.editor.putString(dl.brand + "_auth", dl.token);
                this.editor.putBoolean(dl.brand + "_logged_in", true);
                this.editor.putString(dl.brand + "_student_id", "");
            }
            this.editor.apply();
            vars.intent = new Intent(getApplicationContext(), MainActivity.class);
            // Keep the full splash wait so the pastebin config fetch has time
            // to land before MainActivity opens. Jumping ahead at 300ms left
            // MainActivity staring at an empty app_config in the deep-link flow.
            // startDelay unchanged (2500 ms default).
        } else if (sharedPreferences.getBoolean(new StringBuffer().append(sharedPreferences.getString("default_app", "Aplus")).append("_logged_in").toString(), false)) {
            vars.intent = new Intent(getApplicationContext(), MainActivity.class);
        } else {
            vars.intent = new Intent(getApplicationContext(), LoginActivity.class);
        }
		final long delay = startDelay;
		Handler h = new Handler(Looper.getMainLooper());
		h.postDelayed(new Runnable() {
				@Override
				public void run(){
					startActivity(vars.intent);
				}
			}, delay);
        fetchConfig();
    }

    private void fetchConfig() {
        configFetchAttempt++;
        request requestVar = new request();
        requestVar.delegate = this;
        requestVar.context = this;
        requestVar.showProgress = false;
        requestVar.url = vars.config_url;
        requestVar.id = "config";
        // Browser-ish UA to dodge Cloudflare interstitials that sometimes hit
        // the default okhttp UA. Pastebin accepts both but the browser UA
        // never gets challenged.
        try {
            JSONObject h = new JSONObject();
            h.put("User-Agent",
                "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
            h.put("Accept", "text/plain,application/json,*/*");
            requestVar.headers = h;
        } catch (Exception ignore) {}
        requestVar.execute();
    }

    public void onProcessFinish(String str, String str2) {
        // Validate the body is actually the JSON blob we expect - not a Cloudflare
        // interstitial, not an "HTTP Error: 403" string from request.java, not
        // a null. Only persist it if parse succeeds; otherwise retry with backoff.
        boolean ok = false;
        if (str != null && str.length() > 0 && str.trim().startsWith("{")) {
            try { new JSONObject(str); ok = true; } catch (Exception e) { ok = false; }
        }
        if (ok) {
            this.editor.putString("app_config", str);
            this.editor.putBoolean("first_start", false);
            this.editor.apply();
        } else if (configFetchAttempt < CONFIG_MAX_ATTEMPTS) {
            // Retry: 1s, 2s, 4s backoff.
            long delay = 1000L << (configFetchAttempt - 1);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run(){ fetchConfig(); }
            }, delay);
        } else {
            // Permanent failure. Keep whatever (possibly stale) config is already
            // in prefs rather than overwriting with garbage, and let the user know.
            try {
                Toast.makeText(getApplicationContext(),
                    "config fetch failed - using cached", Toast.LENGTH_LONG).show();
            } catch (Exception ignore) {}
        }

        Handler finisher = new Handler(Looper.getMainLooper());
        finisher.postDelayed(new Runnable() {
            @Override
            public void run(){
                finish();
            }
        }, 5000);
    }

    private String hexsign() {
        try {
            return tools.getApkChecksum(getApplicationContext(), "lk.live.aplusewings");
        } catch (Exception e) {
            return "";
        }
    }

    private void computeApkSignatureAsync(final SharedPreferences sp) {
        long lastUpdate;
        try {
            lastUpdate = getPackageManager()
                .getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            lastUpdate = 0L;
        }
        final long lastUpdateMs = lastUpdate;
        if (sp.getLong("app_signature_at", -1L) == lastUpdateMs
                && sp.getString("app_signature", "").length() > 0) {
            // Cache hit - no recompute needed.
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                String sig = hexsign();
                if (sig != null && sig.length() > 0) {
                    sp.edit()
                        .putString("app_signature", sig)
                        .putLong("app_signature_at", lastUpdateMs)
                        .apply();
                }
            }
        }, "apk-sha256").start();
    }
}


