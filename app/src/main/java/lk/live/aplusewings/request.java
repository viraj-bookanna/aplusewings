package lk.live.aplusewings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Replacement for the old AsyncTask-based `request`. Same public surface so
 * existing callers (MainActivity, LoginActivity, SplashActivity) work with
 * zero changes:
 *
 *     request r = new request();
 *     r.context = ...; r.url = ...; r.method = ...; r.postData = ...;
 *     r.headers = ...; r.delegate = ...; r.id = ...; r.showProgress = ...;
 *     r.execute(args);        // <- now non-blocking via shared ExecutorService
 *
 * Internally uses:
 *   - A single cached ExecutorService (background work)
 *   - android.os.Handler(Looper.getMainLooper()) (main-thread dispatch)
 *   - A Material-styled AlertDialog with a CircularProgressIndicator (progress UI)
 */
public class request {
    public Context context;
    public String message = "";
    public String url;
    public String method = "GET";
    public JSONObject headers = null;
    public String postData = "";
    public AsyncResponse delegate = null;
    public boolean showProgress = true;
    public String id = null;

    private Dialog progress;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Fire-and-forget: starts the HTTP call on a worker, returns immediately. */
    public void execute(String... params) {
        if (showProgress) {
            progress = buildProgressDialog(context, message);
            try { progress.show(); } catch (Exception ignore) {}
        }
        EXECUTOR.execute(new Runnable() {
            @Override public void run() { doRequest(); }
        });
    }

    private void doRequest() {
        String responseString;
        try {
            OkHttpClient client = new OkHttpClient();
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    builder.addHeader(key, headers.getString(key));
                }
            }
            if ("POST".equalsIgnoreCase(method)) {
                builder.post(RequestBody.create(null, postData));
            } else {
                builder.get();
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                if (response.isSuccessful()) {
                    responseString = response.body() != null ? response.body().string() : "";
                } else {
                    responseString = "HTTP Error: " + response.code();
                }
            }
        } catch (Exception e) {
            responseString = "Exception: " + e;
        }
        final String result = responseString;
        MAIN.post(new Runnable() {
            @Override public void run() {
                // Tamper-check behaviour (intentional):
                //   signature_ok == true  -> dismiss the progress dialog as normal
                //   signature_ok == false -> leave the dialog on screen
                // Combined with the auto-launch gate in MainActivity, a tamper
                // mismatch results in: no dialog dismissal + no auto-open of
                // the decrypted URL.
                if (vars.signature_ok && progress != null) {
                    try { progress.dismiss(); } catch (Exception ignore) {}
                }
                if (delegate != null) delegate.onProcessFinish(result, id);
            }
        });
    }

    /**
     * A minimal progress dialog built in code (no XML, no Material dependency).
     * Uses the stock {@link ProgressBar} indeterminate spinner so this works
     * under any theme - including AppCompat-only themes where Material widgets
     * would throw {@code IllegalArgumentException}.
     */
    private static Dialog buildProgressDialog(Context ctx, String msg) {
        // Wrap the context with an explicit AppCompat theme overlay so dialog
        // widgets inflate correctly even if `ctx` came in without a theme.
        ctx = new ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
        int dp16 = dp(ctx, 16);
        int dp24 = dp(ctx, 24);
        int dp36 = dp(ctx, 36);

        ProgressBar spinner = new ProgressBar(ctx);
        spinner.setIndeterminate(true);
        try {
            spinner.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF9C")));
        } catch (Throwable ignore) { /* API < 21 fallback: default color */ }
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp36, dp36);
        spinnerLp.gravity = Gravity.CENTER_VERTICAL;
        spinner.setLayoutParams(spinnerLp);

        TextView tv = new TextView(ctx);
        tv.setText(msg == null || msg.length() == 0 ? "Please wait..." : msg);
        tv.setTextColor(Color.parseColor("#F3F4F6"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setPadding(dp16, 0, 0, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.gravity = Gravity.CENTER_VERTICAL;
        tv.setLayoutParams(tvLp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp24, dp24, dp24, dp24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141821"));
        bg.setCornerRadius(dp(ctx, 16));
        bg.setStroke(dp(ctx, 1), Color.parseColor("#2A3042"));
        row.setBackground(bg);
        row.addView(spinner);
        row.addView(tv);

        AlertDialog d = new AlertDialog.Builder(ctx).setView(row).setCancelable(false).create();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return d;
    }

    private static int dp(Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }
}
