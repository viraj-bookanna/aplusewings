package lk.live.aplusewings;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.Signature;
import java.security.MessageDigest;
import android.util.Base64;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.TextView;
import android.os.Handler;
import android.content.Intent;
import android.content.pm.PackageManager;

public class SplashActivity extends Activity implements AsyncResponse{ 
	SharedPreferences.Editor editor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
		editor = getSharedPreferences("aplusewings", MODE_PRIVATE).edit();
		SharedPreferences prefs = getSharedPreferences("aplusewings", MODE_PRIVATE);
		try{
			Signature sig = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(sig.toByteArray());
			String b64sig = Base64.encodeToString(digest.digest(), Base64.DEFAULT).trim();
			String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
			editor.putString("app_signature", b64sig);
			editor.putString("device_id", id);
			editor.apply();
		}
		catch(Exception e){}
		TextView live = findViewById(R.id.splash_live);
		live.setText("LIvE");
		String defaultApp = prefs.getString("default_app", "Aplus");
		if(prefs.getBoolean(defaultApp+"_logged_in", false)){
			vars.intent = new Intent(getApplicationContext(), MainActivity.class);
		}
		else{
			vars.intent = new Intent(getApplicationContext(), LoginActivity.class);
		}
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
				@Override
				public void run(){
					startActivity(vars.intent);
				}
			}, 2500);
		request request = new request();
		request.delegate = this;
		request.context = this;
		request.showProgress = false;
		request.url = vars.config_url;
		request.execute();
    }

	@Override
	public void onProcessFinish(String result, String id) {
		editor.putString("app_config", result);
		editor.putBoolean("first_start", false);
		editor.apply();
		Handler finisher = new Handler();
		finisher.postDelayed(new Runnable() {
				@Override
				public void run(){
					finish();
				}
			}, 5000);
	}

}
