package lk.live.aplusewings;

import android.widget.ArrayAdapter;
import android.content.Context;
import android.widget.Spinner;
import org.json.JSONObject;
import android.content.SharedPreferences;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import android.widget.Toast;
import org.json.JSONArray;
import android.widget.TextView;

public class util {
    public static void setSpinner(Context ctx, Spinner spinner, String[] spinner_items){
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, spinner_items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
	}
    public static void setSpinnerSelectedItem(Spinner spinner, String value){
		spinner.setSelection(((ArrayAdapter)spinner.getAdapter()).getPosition(value));
	}
    public static String getConfig(Context c, String app, String key){
		try{
			SharedPreferences prefs = c.getSharedPreferences("aplusewings", c.MODE_PRIVATE);
			String json_string = prefs.getString("app_config", "");
			JSONObject json = new JSONObject(json_string);
			return json.getJSONObject(app).getString(key);
		}
		catch(Exception e){
			return e.toString();
		}
	}
	public static JSONObject getConfigArr(Context c, String app, String key){
		try{
			SharedPreferences prefs = c.getSharedPreferences("aplusewings", c.MODE_PRIVATE);
			String json_string = prefs.getString("app_config", "");
			JSONObject json = new JSONObject(json_string);
			return json.getJSONObject(app).getJSONObject(key);
		}
		catch(Exception e){
			return null;
		}
	}
	public static JSONObject authorizeHeaders(Context c, String app, JSONObject headers){
		SharedPreferences prefs = c.getSharedPreferences("aplusewings", c.MODE_PRIVATE);
		String auth = prefs.getString(app+"_auth", "");
		try{
			headers.put("Authorization", auth);
			return headers;
		}
		catch(Exception e){
			Toast.makeText(c, e.toString(), 1).show();
			return null;
		}
	}
	public static JSONObject authorizeHeadersMy(Context c, String app, JSONObject headers){
		SharedPreferences prefs = c.getSharedPreferences("aplusewings", c.MODE_PRIVATE);
		String auth = prefs.getString(app+"_auth", "");
		String device_id = prefs.getString("device_id", "");
		try{
			headers.put("Authorization", auth);
			headers.put("Content-Type", "application/x-www-form-urlencoded");
			headers.put("App", app);
			headers.put("Device", device_id);
			return headers;
		}
		catch(Exception e){
			Toast.makeText(c, e.toString(), 1).show();
			return null;
		}
	}
}
