package lk.live.aplusewings;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;
import org.json.JSONArray;
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

    public static void setSpinnerSelectedItem(Spinner spinner, String str) {
        spinner.setSelection(((ArrayAdapter) spinner.getAdapter()).getPosition(str));
    }

    public static String getConfig(Context context, String str, String str2) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", "")).getJSONObject(str).getString(str2);
        } catch (Exception e) {
            return e.toString();
        }
    }

    public static JSONObject getConfigArr(Context context, String str, String str2) {
        try {
            return new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", "")).getJSONObject(str).getJSONObject(str2);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject authorizeHeaders(Context context, String str, JSONObject jSONObject) {
        try {
            jSONObject.put("Authorization", context.getSharedPreferences("aplusewings", 0).getString(new StringBuffer().append(str).append("_auth").toString(), ""));
            return jSONObject;
        } catch (Exception e) {
            Toast.makeText(context, e.toString(), 1).show();
            return null;
        }
    }

    public static JSONObject authorizeHeadersMy(Context context, String str, JSONObject jSONObject) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("aplusewings", 0);
        String string = sharedPreferences.getString(new StringBuffer().append(str).append("_auth").toString(), "");
        String string2 = sharedPreferences.getString("device_id", "");
        try {
            jSONObject.put("Authorization", string);
            jSONObject.put("Content-Type", "application/x-www-form-urlencoded");
            jSONObject.put("App", str);
            jSONObject.put("Device", string2);
            return jSONObject;
        } catch (Exception e) {
            Toast.makeText(context, e.toString(), 1).show();
            return null;
        }
    }

    public static String get_req_options(Context context, String str) {
        try {
            JSONObject jSONObject = new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", ""));
            JSONObject jSONObject2 = new JSONObject();
            JSONObject jSONObject3 = new JSONObject();
            jSONObject3.put("User-Agent", jSONObject.getJSONObject(str).getJSONObject("headers").get("User-Agent"));
            String obj = vars.sav.get("vod_auth").toString();
            if (obj != "null") {
                jSONObject3.put("Authorization", new StringBuffer().append("Bearer ").append(obj).toString());
            }
            jSONObject2.put("headers", jSONObject3);
            return jSONObject2.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONObject authorizeHeadersVOD(JSONObject jSONObject) {
        try {
            jSONObject.put("Authorization", new StringBuffer().append("Bearer ").append(vars.sav.get("vod_auth")).toString());
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

    public static String getProxyUrl(Context context) {
        try {
            String string = new JSONObject(context.getSharedPreferences("aplusewings", 0).getString("app_config", "")).getString("proxy_url");
            if (string.equals("")) {
                return "";
            }
            return new StringBuffer().append(string).append("?reqdata=").toString();
        } catch (Exception e) {
            return "";
        }
    }
}


