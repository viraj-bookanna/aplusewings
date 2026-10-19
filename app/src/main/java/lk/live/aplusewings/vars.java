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

    // Universal "who am I" GQL request body. Works against every brand's /api/gql
    // (api.apluseducation.lk, api.ewings.lk, api.combinedmaths.lk, api.imsedu.lk).
    // Used to backfill student_id after a token-only callback deep-link.
    public static String meQuery =
        "{\"operationName\":\"Me\",\"variables\":{},"
        + "\"query\":\"query Me { me { _id fname lname email role status created_at updated_at } }\"}";
}

