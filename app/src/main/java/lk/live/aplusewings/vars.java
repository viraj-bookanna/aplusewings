//
// Decompiled by Jadx - 735ms
//
package lk.live.aplusewings;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import org.json.JSONArray;

public class vars {
    public static Context c;
    public static Intent intent;
    public static JSONObject sav;
    public static String config_url = "https://pastebin.com/raw/bpGSu490";
    public static JSONArray quality_list(){
		JSONArray j = new JSONArray();
		j.put("240p");
		j.put("420p");
		return j;
	}
    public static String signature = "Wft/u7OWYYaJxGguHQqZZA==";
    public static boolean signature_ok = true;
}

