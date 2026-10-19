package lk.live.aplusewings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import android.os.Looper;
import org.json.JSONObject;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import org.json.JSONArray;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;

public class MainActivity extends AppCompatActivity implements AsyncResponse {
    SharedPreferences.Editor editor;
    TextView output;
    SharedPreferences prefs;
    Spinner select_app;
    Spinner select_quality;
	Spinner select_proxy;
    String tmp_selected_app;
	EditText video_code;
	EditText proxy_url;
	boolean proxy_on = false;
	Button btn_proxy_toggle;
	// Bounded-retry counters for the deep-link / student-id backfill loops
	// (previously unbounded postDelayed self-chains -> battery drain if config
	// never arrives).
	private int deepLinkWait = 0;
	private int studentIdWait = 0;
	private static final int MAX_WAIT_TICKS = 20; // 20 * 500ms = ~10s hard cap

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        vars.c = this;
        // If we got launched directly from a brand website's custom-scheme URL
        // (e.g. aplus-player://lessonId=XX&url=YY), SplashActivity will have
        // stashed it in prefs. We also accept a direct VIEW intent as a fallback.
        handleDeepLinkIntent(getIntent());
        editor = getSharedPreferences("aplusewings", 0).edit();
        select_app = findViewById(R.id.main_select_app);
        select_quality = findViewById(R.id.main_select_quality);
        output = findViewById(R.id.main_output);
        video_code = findViewById(R.id.main_vid_code);
		select_proxy = findViewById(R.id.main_select_proxy);
		proxy_url = findViewById(R.id.main_proxy_url);
        Button btn_paste = findViewById(R.id.main_vid_code_paste);
        Button btn_generate = findViewById(R.id.main_btn_generate);
        Button btn_logout = findViewById(R.id.main_btn_logout);
        Button btn_copy = findViewById(R.id.main_btn_output_copy);
		btn_proxy_toggle = findViewById(R.id.main_proxy_toggle);
        util.setSpinner(this, this.select_app, util.getAppList(this));
        util.setSpinner(this, this.select_quality, vars.quality_list());
		// Match the proxy-mode set from the original (v2.0) release:
		//   No Proxy  -> direct connection; no toggle, no URL field.
		//   Local     -> run the in-app NanoHTTPD proxy service; show start/stop toggle.
		//   Custom    -> user-supplied proxy URL; show the URL input.
		//   Remote    -> use proxy_url from app_config; no UI configuration needed.
		JSONArray proxy_list = new JSONArray();
		proxy_list.put("No Proxy");
		proxy_list.put("Local");
		proxy_list.put("Custom");
		proxy_list.put("Remote");
		util.setSpinner(this, select_proxy, proxy_list);
        SharedPreferences sharedPreferences = getSharedPreferences("aplusewings", 0);
        prefs = sharedPreferences;
        String string = sharedPreferences.getString("default_app", "Aplus");
        String string2 = this.prefs.getString("default_quality", "240p");
        util.setSpinnerSelectedItem(this.select_app, string);
        util.setSpinnerSelectedItem(this.select_quality, string2);

		// Persist the user's quality choice so it's remembered across launches.
		// (The merged client was already reading `default_quality` from prefs
		// above - it just never wrote it back on change.)
		select_quality.setOnItemSelectedListener(new OnItemSelectedListener(){
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
					try {
						String q = parent.getItemAtPosition(pos).toString();
						editor.putString("default_quality", q).apply();
					} catch (Exception ignore) {}
				}
				@Override public void onNothingSelected(AdapterView<?> p){}
			});
		// Same for the app brand selector.
		select_app.setOnItemSelectedListener(new OnItemSelectedListener(){
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
					try {
						String a = parent.getItemAtPosition(pos).toString();
						editor.putString("default_app", a).apply();
					} catch (Exception ignore) {}
				}
				@Override public void onNothingSelected(AdapterView<?> p){}
			});
		// Remember the proxy mode across launches. One-time pref-key migration
		// from the v2.0 original's `default_proxy_typ` to our `default_proxy_mode`.
		String savedProxyMode = sharedPreferences.getString("default_proxy_mode",
			sharedPreferences.getString("default_proxy_typ", "No Proxy"));
		util.setSpinnerSelectedItem(select_proxy, savedProxyMode);
		// Pre-fill the Custom URL field with whatever the user last saved.
		String savedCustomProxy = sharedPreferences.getString("custom_proxy", "");
		if (proxy_url != null && savedCustomProxy.length() > 0) {
			proxy_url.setText(savedCustomProxy);
		}

		btn_proxy_toggle.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					proxy_toggle();
				}
			});
		// Collapsible proxy card
		final View proxyHeader = findViewById(R.id.main_proxy_header);
		final View proxyBody   = findViewById(R.id.main_proxy_body);
		final TextView proxyChevron = (TextView) findViewById(R.id.main_proxy_chevron);
		if (proxyHeader != null && proxyBody != null) {
			proxyHeader.setOnClickListener(new OnClickListener() {
				@Override public void onClick(View v) {
					boolean expanded = proxyBody.getVisibility() == View.VISIBLE;
					proxyBody.setVisibility(expanded ? View.GONE : View.VISIBLE);
					if (proxyChevron != null) proxyChevron.setText(expanded ? "[ + ]" : "[ - ]");
				}
			});
		}

		// Proxy mode -> toggle which sub-controls are visible.
		//   No Proxy : neither toggle nor URL field (direct connection)
		//   Local    : start/stop toggle only (controls the in-app NanoHTTPD service)
		//   Custom   : custom-URL input only (user-supplied proxy)
		//   Remote   : neither - the proxy URL is read from app_config.proxy_url
		final View proxyUrlWrap = findViewById(R.id.main_proxy_url_wrap);
		select_proxy.setOnItemSelectedListener(new OnItemSelectedListener(){
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
					String proxy_typ = parent.getItemAtPosition(pos).toString();
					boolean showToggle = "Local".equals(proxy_typ);
					boolean showUrl    = "Custom".equals(proxy_typ);
					btn_proxy_toggle.setVisibility(showToggle ? View.VISIBLE : View.GONE);
					if (proxyUrlWrap != null) proxyUrlWrap.setVisibility(showUrl ? View.VISIBLE : View.GONE);
					// Persist the mode so the next session starts in the same state.
					try { editor.putString("default_proxy_mode", proxy_typ).apply(); } catch (Exception ignore) {}
				}
				@Override
				public void onNothingSelected(AdapterView<?> p){}
			});
        btn_paste.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					paste_code();
				}
			});
        btn_generate.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					gen_link();
				}
			});
        btn_logout.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					logout();
				}
			});
        btn_copy.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					copy_link();
				}
			});
        output.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					open_url();
				}
			});

        // Deep-link work may have been stashed either by SplashActivity (fresh
        // launch) or directly by us (singleTask onNewIntent while visible).
        // Try to drain it now that all views are wired up. If config hasn't
        // loaded yet we poll until it has, so the first lesson from a cold
        // start still works.
        maybeProcessPendingDeepLink();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLinkIntent(intent);
        maybeProcessPendingDeepLink();
    }

    /**
     * Promote an incoming VIEW intent (as a direct scheme match on this
     * activity, bypassing the splash) into the same prefs the splash would
     * have written. Idempotent: if the intent is not a deep link, no-op.
     */
    private void handleDeepLinkIntent(Intent intent) {
        DeepLinkRouter.Result dl = DeepLinkRouter.parse(intent);
        if (dl == null) return;
        SharedPreferences sp = getSharedPreferences("aplusewings", 0);
        SharedPreferences.Editor e = sp.edit();
        e.putString("default_app", dl.brand);
        if (DeepLinkRouter.KIND_LESSON.equals(dl.kind)) {
            if (dl.code != null)     e.putString("deeplink_code", dl.code);
            if (dl.lessonId != null) e.putString("deeplink_lesson_id", dl.lessonId);
        } else if (DeepLinkRouter.KIND_CALLBACK.equals(dl.kind)) {
            // Idempotent on redelivery: only overwrite auth + reset student_id
            // when the token actually changed. Otherwise we'd clobber a valid
            // _auth / _student_id each time Android redelivers the VIEW intent.
            String existing = sp.getString(dl.brand + "_auth", "");
            if (!existing.equals(dl.token)) {
                e.putString(dl.brand + "_auth", dl.token);
                e.putBoolean(dl.brand + "_logged_in", true);
                e.putString(dl.brand + "_student_id", "");
            }
        }
        e.apply();
    }

    /**
     * If there is a pending passcode from a deep link, pre-select the brand,
     * fill the video-code box and auto-fire gen_link(). If config hasn't
     * downloaded yet, retry on a short timer.
     */
    private void maybeProcessPendingDeepLink() {
        SharedPreferences sp = getSharedPreferences("aplusewings", 0);
        final String code  = sp.getString("deeplink_code", "");
        final String brand = sp.getString("default_app", "Aplus");
        if (code == null || code.length() == 0) {
            // Even without a lesson deep link, we may have just taken a token
            // via a callback deep link and still owe a student_id lookup.
            maybeBackfillStudentId(brand);
            return;
        }
        if (sp.getString("app_config", "").length() == 0) {
            if (++deepLinkWait > MAX_WAIT_TICKS) {
                try { Toast.makeText(getApplicationContext(),
                    "config unavailable - try again later", Toast.LENGTH_LONG).show(); }
                catch (Exception ignore) {}
                deepLinkWait = 0;
                return;
            }
            new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable(){
                @Override public void run(){ maybeProcessPendingDeepLink(); }
            }, 500);
            return;
        }
        deepLinkWait = 0;

        // If the spinner was populated with an empty app list earlier (cold
        // start before config landed), repopulate it now so the brand lookup
        // actually resolves.
        if (select_app.getAdapter() == null || select_app.getAdapter().getCount() == 0) {
            util.setSpinner(this, select_app, util.getAppList(this));
        }

        // Point the UI at the right brand and consume the pending code.
        util.setSpinnerSelectedItem(select_app, brand);
        tmp_selected_app = brand;
        video_code.setText(code);
        sp.edit().remove("deeplink_code").remove("deeplink_lesson_id").apply();

        // Highlight the Generate CTA so the user sees we handed off correctly,
        // but DO NOT auto-fire gen_link - the user wants a chance to pick
        // quality / adjust the proxy before we burn through to the backend.
        try {
            Toast.makeText(getApplicationContext(),
                "ready -> pick quality then tap Generate",
                Toast.LENGTH_LONG).show();
        } catch (Exception ignore) {}

        // Only still-pending work: if we took a token via a callback deep link
        // but have no student_id yet, backfill it quietly in the background so
        // the user's eventual Generate click doesn't stall.
        if (sp.getString(brand + "_student_id", "").length() == 0
                && sp.getString(brand + "_auth", "").length() > 0) {
            maybeBackfillStudentId(brand);
        }
    }

    /**
     * Fire a `me { _id }` GQL request against the brand's apiURL. Only called
     * when we hold an auth token but no student_id (deep-link callback flow).
     */
    private void maybeBackfillStudentId(String brand) {
        SharedPreferences sp = getSharedPreferences("aplusewings", 0);
        if (sp.getString(brand + "_student_id", "").length() > 0) return;
        if (sp.getString(brand + "_auth", "").length() == 0) return;
        if (sp.getString("app_config", "").length() == 0) {
            if (++studentIdWait > MAX_WAIT_TICKS) {
                studentIdWait = 0;
                return; // give up silently; next Generate will try again
            }
            new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable(){
                @Override public void run(){
                    maybeBackfillStudentId(getSharedPreferences("aplusewings", 0)
                        .getString("default_app", "Aplus"));
                }
            }, 500);
            return;
        }
        studentIdWait = 0;
        tmp_selected_app = brand;
        request r = new request();
        r.context = vars.c;
        r.delegate = this;
        r.method = "POST";
        r.message = "fetching profile";
        r.url = util.getConfig(vars.c, brand, "apiURL");
        r.headers = util.authorizeHeaders(vars.c, brand, util.getConfigArr(vars.c, brand, "headers"));
        r.id = "me_lookup";
        r.postData = vars.meQuery;
        r.execute(new String[0]);
    }

	private void gen_link() {
		tamper_check();
		// Config must be loaded before any brand lookup makes sense.
		if (prefs.getString("app_config", "").length() == 0) {
			Toast.makeText(getApplicationContext(),
				"config still loading, please wait a moment...", Toast.LENGTH_SHORT).show();
			return;
		}
		// Make sure the spinner has the current app list in case onCreate
		// populated it before app_config was fetched.
		if (select_app.getAdapter() == null || select_app.getAdapter().getCount() == 0) {
			util.setSpinner(this, select_app, util.getAppList(this));
		}
		// Prefer whatever the spinner currently shows, but fall back to the
		// stored default_app if the spinner has no selection yet (eg. config
		// loaded after onCreate ran).
		Object sel = select_app.getSelectedItem();
		String app = (sel != null) ? sel.toString() : null;
		if (app == null || app.length() == 0) {
			app = prefs.getString("default_app", "Aplus");
		}
		tmp_selected_app = app;
		// Fixed longstanding bug: original source used `== ""` which is reference
		// equality (always false for prefs-sourced strings) so gen_link would
		// barrel into getLessonContent even when the user wasn't logged in.
		if (prefs.getString(tmp_selected_app + "_auth", "").length() == 0) {
			editor.putString("default_app", tmp_selected_app);
			editor.apply();
			startActivity(new Intent(getApplicationContext(), LoginActivity.class));
			finish();
			return;
		}
		request requestVar = new request();
		requestVar.context = vars.c;
		requestVar.delegate = this;
		requestVar.message = "fetching lesson details";
		requestVar.method = "POST";
		requestVar.url = util.getConfig(vars.c, tmp_selected_app, "apiURL");
		requestVar.headers = util.authorizeHeaders(vars.c, tmp_selected_app, util.getConfigArr(vars.c, tmp_selected_app, "headers"));
		requestVar.id = "code";
		requestVar.postData = String.format(util.getConfig(vars.c, tmp_selected_app, "getLessonContent"), video_code.getText().toString());
		requestVar.execute(new String[0]);
	}
	private void paste_code() {
		try {
			video_code.setText(((ClipboardManager) getSystemService("clipboard")).getPrimaryClip().getItemAt(0).getText());
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "can't find text in clipboard", Toast.LENGTH_LONG).show();
		}
	}
	private void open_url() {
		try{
			startActivity(new Intent("android.intent.action.VIEW", Uri.parse(output.getText().toString())));
		}
		catch(Exception e){}
	}
	private void copy_link() {
		try {
			((ClipboardManager) getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText("link", output.getText().toString()));
			Toast.makeText(getApplicationContext(), "COPIED !", Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "something went wrong", Toast.LENGTH_LONG).show();
		}
	}
	private void logout() {
		// tmp_selected_app may be null on a fresh launch where the user taps
		// Logout before ever hitting Generate. Prefer the spinner, fall back to
		// stored default_app, so we never scribble into prefs keys like
		// "null_logged_in".
		Object sel = select_app != null ? select_app.getSelectedItem() : null;
		String brand = (sel != null) ? sel.toString() : prefs.getString("default_app", "Aplus");
		editor.putBoolean(brand + "_logged_in", false);
		editor.putString (brand + "_auth", "");
		editor.apply();
		startActivity(new Intent(getApplicationContext(), LoginActivity.class));
		finish();
	}
	private static final int RC_POST_NOTIFICATIONS = 42;

	private void proxy_toggle(){
		if (proxy_on) {
			try {
				lk.live.corsproxy.ProxyManager.stopProxyServer(getApplicationContext());
			} catch (Exception e) {
				Toast.makeText(this, "stop failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
			}
			proxy_on = false;
			btn_proxy_toggle.setText("start");
			return;
		}

		// Android 13+: POST_NOTIFICATIONS is a runtime grant. Request it, then
		// start the service once the user responds. Starting a foreground
		// service without the grant on API 33+ accepts the service but hides
		// the notification; on some OEM skins it also throws.
		if (android.os.Build.VERSION.SDK_INT >= 33) {
			if (androidx.core.content.ContextCompat.checkSelfPermission(this,
					"android.permission.POST_NOTIFICATIONS")
					!= android.content.pm.PackageManager.PERMISSION_GRANTED) {
				androidx.core.app.ActivityCompat.requestPermissions(this,
					new String[] { "android.permission.POST_NOTIFICATIONS" },
					RC_POST_NOTIFICATIONS);
				return;
			}
		}
		startProxyServiceSafe();
	}

	private void startProxyServiceSafe() {
		try {
			Intent main = new Intent(getApplicationContext(), MainActivity.class);
			lk.live.corsproxy.ProxyManager.startProxyServer(getApplicationContext(), main, 12522);
			proxy_on = true;
			btn_proxy_toggle.setText("stop");
		} catch (SecurityException se) {
			// API 34 enforces per-type FGS permissions. If the manifest is
			// missing one, startForegroundService throws SecurityException.
			Toast.makeText(this,
				"proxy start blocked: " + se.getMessage(), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			Toast.makeText(this,
				"proxy start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == RC_POST_NOTIFICATIONS) {
			// Grant or deny - try starting anyway. If denied, the service still
			// runs but the foreground notification is suppressed by the OS.
			startProxyServiceSafe();
		}
	}

	
    public void onProcessFinish(String str, String str2) {
        if (str2.equals("me_lookup")) {
            try {
                String sid = new JSONObject(str)
                    .getJSONObject("data")
                    .getJSONObject("me")
                    .getString("_id");
                SharedPreferences sp = getSharedPreferences("aplusewings", 0);
                sp.edit().putString(tmp_selected_app + "_student_id", sid).apply();
                // Silently done - do not auto-fire gen_link. The user will tap
                // Generate manually when they're ready.
            } catch (Exception e) {
                try {
                    output.setText(new JSONObject(str).getJSONArray("errors").getJSONObject(0).getString("message"));
                } catch (Exception e2) {
                    output.setText("profile lookup failed: " + e.toString());
                }
                output.setTextColor(Color.parseColor("#ff0000"));
            }
            return;
        }
        if (str2.equals("code")) {
            try {
                JSONObject jSONObject = new JSONObject(str).getJSONObject("data");
                String string = jSONObject.getJSONObject("getLessonContent").getString("key");
                String string2 = jSONObject.getJSONObject("getLessonContent").getString("hash");
                String string3 = jSONObject.getJSONObject("getLessonContent").getJSONObject("lesson").getString("_id");
                request requestVar = new request();
                requestVar.context = vars.c;
                requestVar.delegate = this;
                requestVar.message = "decrypting video key";
                requestVar.method = "POST";
                requestVar.url = util.getConfig(vars.c, this.tmp_selected_app, "keydecrypt");
                requestVar.headers = util.authorizeHeadersMy(vars.c, this.tmp_selected_app, util.getConfigArr(vars.c, this.tmp_selected_app, "headers"));
                requestVar.id = "key_decrypt";
                requestVar.postData = String.format("data=%s&iv=%s&lesson_id=%s&student_id=%s&custom_timestamp=%d",
                    string, string2, string3,
                    this.prefs.getString(this.tmp_selected_app + "_student_id", ""),
                    System.currentTimeMillis());
                requestVar.execute(new String[0]);
                return;
            } catch (Exception e) {
                try {
                    this.output.setText(new JSONObject(str).getJSONArray("errors").getJSONObject(0).getString("message"));
                } catch (Exception e2) {
                    this.output.setText(e.toString());
                }
                // Error path: show error text in red. (Original code had an
                // auto-launch of the error message as a URI here; removed.)
                this.output.setTextColor(Color.parseColor("#ff0000"));
                return;
            }
        }
        if (str2.equals("key_decrypt")) {
            try {
                JSONObject jSONObject2 = new JSONObject(str);
                if (!jSONObject2.getBoolean("ok")) {
                    this.output.setText(jSONObject2.getString("output"));
                    return;
                }
                vars.sav = jSONObject2.getJSONObject("output");
                String str3 = "v0";
                // Fix: original used `== "420p"` (reference equality, always false).
                if ("420p".equals(this.select_quality.getSelectedItem().toString())) {
                    str3 = "v1";
                }
                String replace = jSONObject2.getJSONObject("output").getString("playlist_url")
                    .replace("/playback", "/" + str3 + "/playback");
                vars.sav.put("playlist_url", replace);
                vars.sav.put("vod_auth", jSONObject2.getJSONObject("output").getString("pass"));
                request requestVar2 = new request();
                requestVar2.context = vars.c;
                requestVar2.delegate = this;
                requestVar2.message = "downloading playlist";
                requestVar2.url = replace;
                // The playlist origin server now requires `Authorization: Bearer <pass>`
                // when the backend returned a real pass (non-"null"). Reuse the
                // util.authorizeHeadersVOD helper which already exists for exactly
                // this purpose but was never wired up in the original client.
                JSONObject plHeaders = util.getConfigArr(vars.c, this.tmp_selected_app, "headers");
                String vodAuth = jSONObject2.getJSONObject("output").optString("pass", "null");
                if (plHeaders != null && vodAuth != null && !"null".equals(vodAuth) && vodAuth.length() > 0) {
                    try { plHeaders.put("Authorization", "Bearer " + vodAuth); } catch (Exception ignore) {}
                }
                requestVar2.headers = plHeaders;
                requestVar2.id = "download_playlist";
                requestVar2.execute(new String[0]);
                return;
            } catch (Exception e4) {
                try {
                    this.output.setText(new JSONObject(str).getString("output"));
                } catch (Exception e5) {
                    this.output.setText(e5.toString());
                }
                // Error path: show error text in red. (Original code had an
                // auto-launch of the error message as a URI here; removed.)
                this.output.setTextColor(Color.parseColor("#ff0000"));
                return;
            }
        }
        if (str2.equals("download_playlist")) {
            try {
                JSONObject jSONObject3 = vars.sav;
                String encode = URLEncoder.encode(jSONObject3.getString("raw_key"), StandardCharsets.UTF_8.name());
                String encode2 = URLEncoder.encode(jSONObject3.getString("playlist_url"), StandardCharsets.UTF_8.name());
                String string4 = jSONObject3.getString("playlist_decryption_hash");
                request requestVar3 = new request();
                requestVar3.context = vars.c;
                requestVar3.delegate = this;
                requestVar3.message = "decrypting playlist";
                requestVar3.method = "POST";
                requestVar3.url = util.getConfig(vars.c, this.tmp_selected_app, "playlistdecrypt");
                requestVar3.headers = util.authorizeHeadersMy(vars.c, this.tmp_selected_app, util.getConfigArr(vars.c, this.tmp_selected_app, "headers"));
                requestVar3.id = "decrypt_playlist";
                // Resolve the ACTUAL proxy URL based on what the user picked in
                // the UI. The old merged client always sent the Remote proxy no
                // matter which mode the spinner was on; that's been fixed.
                String proxyMode = (select_proxy != null && select_proxy.getSelectedItem() != null)
                    ? select_proxy.getSelectedItem().toString()
                    : prefs.getString("default_proxy_mode", "No Proxy");
                String customProxy = (proxy_url != null && proxy_url.getText() != null)
                    ? proxy_url.getText().toString() : "";
                // Persist the typed Custom URL for next-launch recall.
                if ("Custom".equals(proxyMode) && customProxy != null && customProxy.length() > 0) {
                    try { editor.putString("custom_proxy", customProxy).apply(); } catch (Exception ignore) {}
                }
                String effectiveProxy = util.getProxyUrlFor(vars.c, proxyMode, customProxy);
                // URL-encode every field value uniformly. The m3u8 body (str) and
                // the base64-ish iv (string4) commonly contain `&`, `=`, `+`, `#`,
                // `\n` which would otherwise corrupt the form boundary.
                String dataEnc = URLEncoder.encode(str,     StandardCharsets.UTF_8.name());
                String ivEnc   = URLEncoder.encode(string4, StandardCharsets.UTF_8.name());
                if (effectiveProxy == null || effectiveProxy.length() == 0) {
                    requestVar3.postData = String.format("url=%s&keyurl=%s&data=%s&iv=%s",
                        encode2, encode, dataEnc, ivEnc);
                } else {
                    requestVar3.postData = String.format("url=%s&keyurl=%s&data=%s&iv=%s&proxy=%s&req_options=%s",
                        encode2, encode, dataEnc, ivEnc,
                        URLEncoder.encode(effectiveProxy, StandardCharsets.UTF_8.name()),
                        URLEncoder.encode(util.get_req_options(vars.c, this.tmp_selected_app), StandardCharsets.UTF_8.name()));
                }
                requestVar3.execute(new String[0]);
                return;
            } catch (Exception e7) {
                this.output.setText(e7.toString());
                // Error path: show error text in red. (Original code had an
                // auto-launch of the error message as a URI here; removed.)
                this.output.setTextColor(Color.parseColor("#ff0000"));
                return;
            }
        }
        if (str2.equals("decrypt_playlist")) {
            try {
                JSONObject jSONObject4 = new JSONObject(str);
                String string5 = jSONObject4.getString("output");
                if (!jSONObject4.getBoolean("ok")) {
                    this.output.setText(string5);
                    return;
                }
                this.output.setText(string5);
                this.output.setTextColor(Color.parseColor("#00ff00"));
                // Only auto-launch the external video player if the tamper check
                // passed. On mismatch we keep the URL visible in the output box
                // so the user can inspect / copy it, but we don't hand it to
                // another app.
                if (vars.signature_ok) {
                    try {
                        startActivity(new Intent("android.intent.action.VIEW", Uri.parse(string5)));
                    } catch (Exception e9) {
                        this.output.setTextColor(Color.parseColor("#ff0000"));
                    }
                }
            } catch (Exception e10) {
                try {
                    this.output.setText(new JSONObject(str).getString("output"));
                } catch (Exception e11) {
                    this.output.setText(e11.toString());
                }
            }
        }
    }

    public void tamper_check() {
        // Re-evaluate on every call so the flag can flip back to true after a
        // pastebin update rotates the expected checksum, instead of being
        // sticky-false for the lifetime of the process.
        String computed = this.prefs.getString("app_signature", "");
        String expected = util.get_sign(vars.c);
        vars.signature_ok = (computed != null) && computed.length() > 0
                            && computed.equals(expected);
    }
}


