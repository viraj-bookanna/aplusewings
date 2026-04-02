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

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        vars.c = this;
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
		JSONArray proxy_list = new JSONArray();
		proxy_list.put("Local");
		proxy_list.put("Custom");
		util.setSpinner(this, select_proxy, proxy_list);
        SharedPreferences sharedPreferences = getSharedPreferences("aplusewings", 0);
        prefs = sharedPreferences;
        String string = sharedPreferences.getString("default_app", "Aplus");
        String string2 = this.prefs.getString("default_quality", "240p");
        util.setSpinnerSelectedItem(this.select_app, string);
        util.setSpinnerSelectedItem(this.select_quality, string2);
		btn_proxy_toggle.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					proxy_toggle();
				}
			});
		select_proxy.setOnItemSelectedListener(new OnItemSelectedListener(){
				@Override
				public void onItemSelected(AdapterView parent, View view, int pos, long id){
					String proxy_typ = parent.getItemAtPosition(pos).toString();
					btn_proxy_toggle.setVisibility(proxy_typ.equals("Local")?View.VISIBLE:View.INVISIBLE);
					proxy_url.setEnabled(proxy_typ.equals("Custom"));
				}
				@Override
				public void onNothingSelected(AdapterView p){}
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
    }
	private void gen_link() {
		tamper_check();
		tmp_selected_app = select_app.getSelectedItem().toString();
		if (prefs.getString(new StringBuffer().append(tmp_selected_app).append("_auth").toString(), "") == "") {
			editor.putString("default_app", tmp_selected_app);
			editor.apply();
			startActivity(new Intent(getApplicationContext(), LoginActivity.class));
			finish();
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
		editor.putBoolean(new StringBuffer().append(tmp_selected_app).append("_logged_in").toString(), false);
		editor.putString(new StringBuffer().append(tmp_selected_app).append("_auth").toString(), "");
		editor.apply();
		startActivity(new Intent(getApplicationContext(), LoginActivity.class));
		finish();
	}
	private void proxy_toggle(){
		Context c = getApplicationContext();
		if(proxy_on){
			lk.live.corsproxy.ProxyManager.stopProxyServer(c);
			proxy_on = false;
			btn_proxy_toggle.setText("Start");
		}
		else{
			Intent main = new Intent(c, MainActivity.class);
			lk.live.corsproxy.ProxyManager.startProxyServer(c, main, 12522);
			proxy_on = true;
			btn_proxy_toggle.setText("Stop");
		}
	}

	
    public void onProcessFinish(String str, String str2) {
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
                requestVar.postData = String.format("data=%s&iv=%s&lesson_id=%s&student_id=%s&custom_timestamp=%s", string, string2, string3, this.prefs.getString(new StringBuffer().append(this.tmp_selected_app).append("_student_id").toString(), ""), new Long(System.currentTimeMillis()));
                requestVar.execute(new String[0]);
                return;
            } catch (Exception e) {
                try {
                    this.output.setText(new JSONObject(str).getJSONArray("errors").getJSONObject(0).getString("message"));
                } catch (Exception e2) {
                    this.output.setText(e.toString());
                }
                try {
                    this.output.setTextColor(Color.parseColor("#00ff00"));
                    startActivity(new Intent("android.intent.action.VIEW", Uri.parse(this.output.getText().toString())));
                    return;
                } catch (Exception e3) {
                    this.output.setTextColor(Color.parseColor("#ff0000"));
                    return;
                }
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
                if (this.select_quality.getSelectedItem().toString() == "420p") {
                    str3 = "v1";
                }
                String replace = jSONObject2.getJSONObject("output").getString("playlist_url").replace("/playback", new StringBuffer().append(new StringBuffer().append("/").append(str3).toString()).append("/playback").toString());
                vars.sav.put("playlist_url", replace);
                vars.sav.put("vod_auth", jSONObject2.getJSONObject("output").getString("pass"));
                request requestVar2 = new request();
                requestVar2.context = vars.c;
                requestVar2.delegate = this;
                requestVar2.message = "downloading playlist";
                requestVar2.url = replace;
                requestVar2.headers = util.getConfigArr(vars.c, this.tmp_selected_app, "headers");
                requestVar2.id = "download_playlist";
                requestVar2.execute(new String[0]);
                return;
            } catch (Exception e4) {
                try {
                    this.output.setText(new JSONObject(str).getString("output"));
                } catch (Exception e5) {
                    this.output.setText(e5.toString());
                }
                try {
                    this.output.setTextColor(Color.parseColor("#00ff00"));
                    startActivity(new Intent("android.intent.action.VIEW", Uri.parse(output.getText().toString())));
                    return;
                } catch (Exception e6) {
                    this.output.setTextColor(Color.parseColor("#ff0000"));
                    return;
                }
            }
        }
        if (str2.equals("download_playlist")) {
            try {
                JSONObject jSONObject3 = vars.sav;
                String encode = URLEncoder.encode(jSONObject3.getString("raw_key"));
                String encode2 = URLEncoder.encode(jSONObject3.getString("playlist_url"));
                String string4 = jSONObject3.getString("playlist_decryption_hash");
                request requestVar3 = new request();
                requestVar3.context = vars.c;
                requestVar3.delegate = this;
                requestVar3.message = "decrypting playlist";
                requestVar3.method = "POST";
                requestVar3.url = util.getConfig(vars.c, this.tmp_selected_app, "playlistdecrypt");
                requestVar3.headers = util.authorizeHeadersMy(vars.c, this.tmp_selected_app, util.getConfigArr(vars.c, this.tmp_selected_app, "headers"));
                requestVar3.id = "decrypt_playlist";
                if (util.getProxyUrl(vars.c).equals("")) {
                    requestVar3.postData = String.format("url=%s&keyurl=%s&data=%s&iv=%s", encode2, encode, str, string4);
                } else {
                    requestVar3.postData = String.format("url=%s&keyurl=%s&data=%s&iv=%s&proxy=%s&req_options=%s", encode2, encode, str, string4, util.getProxyUrl(vars.c), util.get_req_options(vars.c, this.tmp_selected_app));
                }
                requestVar3.execute(new String[0]);
                return;
            } catch (Exception e7) {
                this.output.setText(e7.toString());
                try {
                    this.output.setTextColor(Color.parseColor("#00ff00"));
                    startActivity(new Intent("android.intent.action.VIEW", Uri.parse(this.output.getText().toString())));
                    return;
                } catch (Exception e8) {
                    this.output.setTextColor(Color.parseColor("#ff0000"));
                    return;
                }
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
                try {
                    this.output.setTextColor(Color.parseColor("#00ff00"));
                    startActivity(new Intent("android.intent.action.VIEW", Uri.parse(string5)));
                } catch (Exception e9) {
                    this.output.setTextColor(Color.parseColor("#ff0000"));
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
        if (this.prefs.getString("app_signature", "").equals(util.get_sign(vars.c))) {
            return;
        }
        vars.signature_ok = false;
    }
}


