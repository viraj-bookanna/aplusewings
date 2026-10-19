package lk.live.aplusewings;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class util {
    public static void setSpinner(Context context, Spinner spinner, JSONArray jSONArray) {
		String[] strArr;
		try {
            strArr = new String[jSONArray.length()];
            for (int i = 0; i < jSONArray.length(); i++) {
                strArr[i] = jSONArray.getString(i);
            }
        } catch (Exception e) {
			strArr = new String[0];
        }
        ArrayAdapter arrayAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, strArr);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter((SpinnerAdapter) arrayAdapter);
    }

    @SuppressWarnings("unchecked")
    public static void setSpinnerSelectedItem(Spinner spinner, String str) {
        if (spinner == null || spinner.getAdapter() == null) return;
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        int pos = adapter.getPosition(str);
        if (pos >= 0) spinner.setSelection(pos);
    }

    /** Return null (NOT an exception string) on miss, so callers can short-circuit. */
    public static String getConfig(Context context, String brand, String key) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0)
                .getString("app_config", "")).getJSONObject(brand).getString(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject getConfigArr(Context context, String brand, String key) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0)
                .getString("app_config", "")).getJSONObject(brand).getJSONObject(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject authorizeHeaders(Context context, String brand, JSONObject base) {
        // Never return null: if base is null (config miss), start from an empty
        // JSONObject so the caller still gets a header map with Authorization,
        // instead of silently sending the request with no auth at all.
        JSONObject h = (base != null) ? base : new JSONObject();
        try {
            h.put("Authorization", context.getSharedPreferences("aplusewings", 0)
                .getString(brand + "_auth", ""));
        } catch (JSONException ignore) {}
        return h;
    }

    public static JSONObject authorizeHeadersMy(Context context, String brand, JSONObject base) {
        JSONObject h = (base != null) ? base : new JSONObject();
        SharedPreferences sp = context.getSharedPreferences("aplusewings", 0);
        try {
            h.put("Authorization", sp.getString(brand + "_auth", ""));
            h.put("Content-Type", "application/x-www-form-urlencoded");
            h.put("App", brand);
            h.put("Device", sp.getString("device_id", ""));
        } catch (JSONException ignore) {}
        return h;
    }

    public static String get_req_options(Context context, String str) {
        try {
            JSONObject jSONObject = new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", ""));
            JSONObject jSONObject2 = new JSONObject();
            JSONObject jSONObject3 = new JSONObject();
            jSONObject3.put("User-Agent", jSONObject.getJSONObject(str).getJSONObject("headers").get("User-Agent"));
            // Fix: original used `obj != "null"` (reference equality on String,
            // always true) so Bearer was always prefixed even when pass was
            // the literal string "null". Use proper .equals and a non-empty check.
            String obj = vars.sav.get("vod_auth").toString();
            if (obj != null && obj.length() > 0 && !"null".equals(obj)) {
                jSONObject3.put("Authorization", "Bearer " + obj);
            }
            jSONObject2.put("headers", jSONObject3);
            return jSONObject2.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONObject authorizeHeadersVOD(JSONObject jSONObject) {
        try {
            Object v = (vars.sav != null) ? vars.sav.opt("vod_auth") : null;
            String vodAuth = (v == null) ? null : v.toString();
            if (vodAuth != null && vodAuth.length() > 0 && !"null".equals(vodAuth)) {
                jSONObject.put("Authorization", "Bearer " + vodAuth);
            }
            return jSONObject;
        } catch (Exception e) {
            return null;
        }
    }

    public static String get_sign(Context context) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", "")).getString("checksum");
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONArray getAppList(Context context) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", "")).getJSONArray("apps");
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * Resolve the proxy URL that should actually be used for the backend's
     * `playlistdecrypt` request, based on the user's current proxy-mode choice.
     *
     *   "No Proxy" -> empty string      -> backend emits a bare playlist
     *   "Local"    -> http://127.0.0.1:12522/?unblock_cors=1&reqdata=
     *                                  -> routes through the in-app NanoHTTPD service
     *   "Custom"   -> whatever the user typed into the custom-URL EditText,
     *                suffixed with `?reqdata=` (preserves original semantics)
     *   "Remote"   -> app_config.proxy_url + "?reqdata="  (the Cloudflare worker)
     *
     * Anything else (unknown mode, missing config) -> empty string.
     */
    /**
     * Resolve the proxy URL to send as the `proxy=` form field on the backend's
     * `playlistdecrypt` request. The backend will append a URL-encoded JSON blob
     * of the request data (`urllib.parse.quote(json.dumps(reqdata))`) directly
     * to this string, so it must END IN `?reqdata=` (or `&reqdata=`).
     *
     *   "No Proxy" -> ""   (backend won't proxy at all)
     *   "Local"    -> http://127.0.0.1:12522/?reqdata=   (matches original app's literal)
     *   "Custom"   -> user-typed URL, with ?reqdata= appended if missing
     *   "Remote"   -> app_config.proxy_url (already ends in `?reqdata=` in the shipped config)
     */
    public static String getProxyUrlFor(Context ctx, String mode, String customUrl) {
        if (mode == null) mode = "No Proxy";
        if ("No Proxy".equals(mode)) return "";
        if ("Local".equals(mode)) {
            return "http://127.0.0.1:12522/?reqdata=";
        }
        if ("Custom".equals(mode)) {
            if (customUrl == null) return "";
            customUrl = customUrl.trim();
            if (customUrl.length() == 0) return "";
            if (customUrl.endsWith("reqdata=")) return customUrl;
            return customUrl + (customUrl.contains("?") ? "&" : "?") + "reqdata=";
        }
        if ("Remote".equals(mode)) {
            try {
                JSONObject cfg = new JSONObject(ctx.getSharedPreferences("aplusewings", 0)
                    .getString("app_config", "{}"));
                String base = cfg.optString("proxy_url", "");
                if (base.length() == 0) return "";
                // The shipped config already ends in `?reqdata=`; don't double-append.
                if (base.endsWith("reqdata=")) return base;
                return base + (base.contains("?") ? "&" : "?") + "reqdata=";
            } catch (Exception ignore) {
                return "";
            }
        }
        return "";
    }

    /** Legacy entry point - kept so any lingering caller still compiles. Behaves as
     *  "Remote" mode. New code should call {@link #getProxyUrlFor(Context, String, String)}. */
    public static String getProxyUrl(Context context) {
        return getProxyUrlFor(context, "Remote", null);
    }
}


