package lk.live.aplusewings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.TextView;
import lk.live.security.tools;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity implements AsyncResponse {
    SharedPreferences.Editor editor;

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_splash);
        this.editor = getSharedPreferences("aplusewings", 0).edit();
        SharedPreferences sharedPreferences = getSharedPreferences("aplusewings", 0);
        try {
            String string = Settings.Secure.getString(getContentResolver(), "android_id");
            this.editor.putString("app_signature", hexsign());
            this.editor.putString("device_id", string);
            this.editor.apply();
        } catch (Exception e) {
        }
        ((TextView) findViewById(R.id.splash_live)).setText("LIvE");
        if (sharedPreferences.getBoolean(new StringBuffer().append(sharedPreferences.getString("default_app", "Aplus")).append("_logged_in").toString(), false)) {
            vars.intent = new Intent(getApplicationContext(), MainActivity.class);
        } else {
            vars.intent = new Intent(getApplicationContext(), LoginActivity.class);
        }
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
				@Override
				public void run(){
					startActivity(vars.intent);
				}
			}, 2500);
        request requestVar = new request();
        requestVar.delegate = this;
        requestVar.context = this;
        requestVar.showProgress = false;
        requestVar.url = vars.config_url;
        requestVar.execute();
    }
	
    public void onProcessFinish(String str, String str2) {
        this.editor.putString("app_config", str);
        this.editor.putBoolean("first_start", false);
        this.editor.apply();
        Handler finisher = new Handler();
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
}


