package lk.live.aplusewings;
 
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.Button;
import android.view.View.OnClickListener;
import android.view.View;
import android.content.ClipboardManager;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipData;
import org.json.JSONObject;
import java.net.URLEncoder;
import android.net.Uri;
import android.graphics.Color;
import android.content.pm.Signature;
import android.content.pm.PackageManager;
import java.security.MessageDigest;
import android.util.Base64;
import android.provider.Settings;

public class MainActivity extends Activity implements AsyncResponse{ 
    TextView output;
	SharedPreferences prefs;
	SharedPreferences.Editor editor;
	Spinner select_app, select_quality;
	String tmp_selected_app;
	@Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		vars.c = MainActivity.this;
		editor = getSharedPreferences("aplusewings", MODE_PRIVATE).edit();
		select_app = findViewById(R.id.main_select_app);
		select_quality = findViewById(R.id.main_select_quality);
		output = findViewById(R.id.main_output);
		final EditText video_code = findViewById(R.id.main_vid_code);
		Button logout = findViewById(R.id.main_btn_logout);
		Button paste_video_code = findViewById(R.id.main_vid_code_paste);
		Button copy_output = findViewById(R.id.main_btn_output_copy);
		Button generate = findViewById(R.id.main_btn_generate);
		
		util.setSpinner(this, select_app, vars.app_list);
		util.setSpinner(this, select_quality, vars.quality_list);
		
		prefs = getSharedPreferences("aplusewings", MODE_PRIVATE);
		String defaultApp = prefs.getString("default_app", "Aplus");
		String defaultQuality = prefs.getString("default_quality", "240p");
		
		util.setSpinnerSelectedItem(select_app, defaultApp);
		util.setSpinnerSelectedItem(select_quality, defaultQuality);
		
		generate.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				tamper_check();
				tmp_selected_app = select_app.getSelectedItem().toString();
				if(prefs.getString(tmp_selected_app+"_auth", "") == ""){
					editor.putString("default_app", tmp_selected_app);
					editor.apply();
					Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
					startActivity(intent);
					finish();
					return;
				}
				request client = new request();
				client.context = vars.c;
				client.delegate = MainActivity.this;
				client.message = "fetching lesson details";
				client.method = "POST";
				client.url = util.getConfig(
					vars.c,
					tmp_selected_app,
					"apiURL"
				);
				client.headers = util.authorizeHeaders(
				vars.c,
				tmp_selected_app,
				util.getConfigArr(
					vars.c,
					tmp_selected_app,
					"headers")
				);
				client.id = "code";
				client.postData = String.format(
					util.getConfig(
						vars.c,
						tmp_selected_app,
						"getLessonContent"),
					video_code.getText().toString()
				);
				client.execute();
			}
		});
		paste_video_code.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				tamper_check();
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				try{
					CharSequence textToPaste = clipboard.getPrimaryClip().getItemAt(0).getText();
					video_code.setText(textToPaste);
				}
				catch (Exception e){
					Toast.makeText(getApplicationContext(), "can't find text in clipboard", Toast.LENGTH_LONG).show();
				}
			}
		});
		output.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				tamper_check();
				try{
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(output.getText().toString()));
					startActivity(browserIntent);
				}
				catch(Exception e){}
			}
		});
		copy_output.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				tamper_check();
				try{
					ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); 
					ClipData clip = ClipData.newPlainText("link", output.getText().toString());
					clipboard.setPrimaryClip(clip);
					Toast.makeText(getApplicationContext(), "COPIED !", 1).show();
				}
				catch(Exception e){
					Toast.makeText(getApplicationContext(), "something went wrong", 1).show();
				}
			}
		});
		logout.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				tamper_check();
				String sel_app = tmp_selected_app;
				editor.putBoolean(sel_app+"_logged_in", false);
				editor.putString(sel_app+"_auth", "");
				editor.apply();
				Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
				startActivity(intent);
				finish();
			}
		});
	}

	@Override
	public void onProcessFinish(String result, String id){
		JSONObject data;
		request client;
		switch(id){
			case "code":
				String key, hash, lesson_id;
				try{
					data = new JSONObject(result).getJSONObject("data");
					key = data.getJSONObject("getLessonContent").getString("key");
					hash = data.getJSONObject("getLessonContent").getString("hash");
					lesson_id = data.getJSONObject("getLessonContent").getJSONObject("lesson").getString("_id");
				}
				catch(Exception e){
					try{
						output.setText(new JSONObject(result).getJSONArray("errors").getJSONObject(0).getString("message"));
					}
					catch(Exception e2){
						output.setText(e.toString());
					}
					try{
						output.setTextColor(Color.parseColor("#00ff00"));
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(output.getText().toString()));
						startActivity(browserIntent);
					}
					catch(Exception e3){
						output.setTextColor(Color.parseColor("#ff0000"));
					}
					return;
				}
				client = new request();
				client.context = vars.c;
				client.delegate = MainActivity.this;
				client.message = "decrypting video key";
				client.method = "POST";
				client.url = util.getConfig(
					vars.c,
					tmp_selected_app,
					"keydecrypt"
				);
				client.headers = util.authorizeHeadersMy(
					vars.c,
					tmp_selected_app,
					util.getConfigArr(
						vars.c,
						tmp_selected_app,
						"headers")
				);
				client.id = "key_decrypt";
				client.postData = String.format(
					"data=%s&iv=%s&lesson_id=%s&student_id=%s&custom_timestamp=%s",
					key,hash,lesson_id,prefs.getString(tmp_selected_app+"_student_id", ""),System.currentTimeMillis()
				);
				client.execute();
				break;
			case "key_decrypt":
				String playlist_url;
				try{
					data = new JSONObject(result);
					if(!data.getBoolean("ok")){
						output.setText(data.getString("output"));
						return;
					}
					vars.sav = data.getJSONObject("output");
					String quality = "v0";
					if(select_quality.getSelectedItem().toString() == "420p"){
						quality = "v1";
					}
					playlist_url = data.getJSONObject("output").getString("playlist_url").replace("/playback", "/"+quality+"/playback");
					vars.sav.put("playlist_url", playlist_url);
				}
				catch(Exception e){
					try{
						output.setText(new JSONObject(result).getString("output"));
					}
					catch(Exception e2){
						output.setText(e2.toString());
					}
					try{
						output.setTextColor(Color.parseColor("#00ff00"));
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(output.getText().toString()));
						startActivity(browserIntent);
					}
					catch(Exception e3){
						output.setTextColor(Color.parseColor("#ff0000"));
					}
					return;
				}
				client = new request();
				client.context = vars.c;
				client.delegate = MainActivity.this;
				client.message = "downloading playlist";
				client.url = playlist_url;
				client.headers = util.getConfigArr(
					vars.c,
					tmp_selected_app,
					"headers"
				);
				client.id = "download_playlist";
				client.execute();
				break;
			case "download_playlist":
				String key_url, playlist_url2, playlist_iv;
				try{
					data = vars.sav;
					key_url = URLEncoder.encode(data.getString("raw_key"));
					playlist_url2 = URLEncoder.encode(data.getString("playlist_url"));
					playlist_iv = data.getString("playlist_decryption_hash");
				}
				catch(Exception e){
					output.setText(e.toString());
					try{
						output.setTextColor(Color.parseColor("#00ff00"));
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(output.getText().toString()));
						startActivity(browserIntent);
					}
					catch(Exception e3){
						output.setTextColor(Color.parseColor("#ff0000"));
					}
					return;
				}
				client = new request();
				client.context = vars.c;
				client.delegate = MainActivity.this;
				client.message = "decrypting playlist";
				client.method = "POST";
				client.url = util.getConfig(
					vars.c,
					tmp_selected_app,
					"playlistdecrypt"
				);
				client.headers = util.authorizeHeadersMy(
					vars.c,
					tmp_selected_app,
					util.getConfigArr(
						vars.c,
						tmp_selected_app,
						"headers")
				);
				client.id = "decrypt_playlist";
				client.postData = String.format(
					"url=%s&keyurl=%s&data=%s&iv=%s",
					playlist_url2,key_url,result,playlist_iv
				);
				client.execute();
				break;
			case "decrypt_playlist":
				String server_response;
				try{
					data = new JSONObject(result);
					server_response = data.getString("output");
					if(!data.getBoolean("ok")){
						output.setText(server_response);
						return;
					}
				}
				catch(Exception e){
					try{
						output.setText(new JSONObject(result).getString("output"));
					}
					catch(Exception e2){
						output.setText(e2.toString());
					}
					return;
				}
				output.setText(server_response);
				try{
					output.setTextColor(Color.parseColor("#00ff00"));
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(server_response));
					startActivity(browserIntent);
				}
				catch(Exception e){
					output.setTextColor(Color.parseColor("#ff0000"));
				}
		}
	}
	private void tamper_check(){
		if(!prefs.getString("app_signature", "").equals(vars.signature)){
			vars.signature_ok = false;
		}
	}
	private String b64sign(){
		try{
			Signature sig = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(sig.toByteArray());
			return Base64.encodeToString(digest.digest(), Base64.DEFAULT).trim();
		}
		catch(Exception e){
			return "";
		}
	}
}

