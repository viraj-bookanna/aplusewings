package lk.live.aplusewings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.Button;
import android.content.SharedPreferences;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import org.json.JSONObject;
import java.net.URLEncoder;

public class LoginActivity extends Activity implements AsyncResponse{ 
	Spinner select_app;
	EditText input_usrname, input_pass;
	CheckBox remember;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
		select_app = findViewById(R.id.login_select_app);
		input_usrname = findViewById(R.id.login_username);
		input_pass = findViewById(R.id.login_password);
		remember = findViewById(R.id.login_remember);
		TextView for_remember = findViewById(R.id.login_for_remember);
		Button login = findViewById(R.id.login_btn_login);
		
		util.setSpinner(this, select_app, vars.app_list);
		
		SharedPreferences prefs = getSharedPreferences("aplusewings", MODE_PRIVATE);
		String defaultApp = prefs.getString("default_app", "Aplus");
		input_usrname.setText(prefs.getString(defaultApp+"_saved_username", ""));
		input_pass.setText(prefs.getString(defaultApp+"_saved_password", ""));
		
		util.setSpinnerSelectedItem(select_app, defaultApp);
		
		for_remember.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				remember.setChecked(!remember.isChecked());
			}
		});
		
		login.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v1){
				String username = input_usrname.getText().toString();
				String password = input_pass.getText().toString();
				request request = new request();
				request.delegate = LoginActivity.this;
				request.context = LoginActivity.this;
				request.method = "POST";
				request.message = "Authenticating...";
				request.id = "login";
				request.url = util.getConfig(
					LoginActivity.this,
					select_app.getSelectedItem().toString(),
					"apiURL"
				);
				request.headers = util.getConfigArr(
					LoginActivity.this,
					select_app.getSelectedItem().toString(),
					"headers"
				);
				request.postData = String.format(util.getConfig(
					LoginActivity.this,
					select_app.getSelectedItem().toString(),
					"loginStudent"
				), username, password);
				request.execute();
			}
		});
    }

	@Override
	public void onProcessFinish(String result, String id) {
		SharedPreferences.Editor editor = getSharedPreferences("aplusewings", MODE_PRIVATE).edit();
		String sel_app = select_app.getSelectedItem().toString();
		switch(id){
			case "login":
				String auth, student_id;
				try{
					JSONObject data = new JSONObject(result).getJSONObject("data");
					auth = data.getJSONObject("loginStudent").getString("accessToken");
					student_id = data.getJSONObject("loginStudent").getJSONObject("userAccount").getString("_id");
				}
				catch(Exception e){
					try{
						Toast.makeText(
							getApplicationContext(),
							new JSONObject(result).getJSONArray("errors").getJSONObject(0).getString("message"),
							1
						).show();
					}
					catch(Exception e2){
						Toast.makeText(getApplicationContext(), e.toString(), 1).show();
					}
					return;
				}
				if(remember.isChecked()){
					editor.putString(sel_app+"_saved_username", input_usrname.getText().toString());
					editor.putString(sel_app+"_saved_password", input_pass.getText().toString());
				}
				editor.putString("default_app", sel_app);
				editor.putBoolean(sel_app+"_logged_in", true);
				editor.putString(sel_app+"_auth", auth);
				editor.putString(sel_app+"_student_id", student_id);
				editor.apply();
				request request = new request();
				request.delegate = LoginActivity.this;
				request.context = LoginActivity.this;
				request.method = "POST";
				request.id = "regDevice";
				request.message = "Registering device";
				request.url = util.getConfig(
					LoginActivity.this,
					sel_app,
					"devReg"
				);
				request.headers = util.authorizeHeadersMy(
					LoginActivity.this,
					sel_app,
					util.getConfigArr(
						LoginActivity.this,
						sel_app,
						"headers"
					)
				);
				request.postData = String.format(
					"student_id=%s&u=%s&p=%s",
					student_id,
					URLEncoder.encode(input_usrname.getText().toString()),
					URLEncoder.encode(input_pass.getText().toString())
				);
				request.execute();
				break;
			case "regDevice":
				try{
					JSONObject json = new JSONObject(result);
					if(json.getBoolean("ok")){
						Intent intent = new Intent(getApplicationContext(), MainActivity.class);
						startActivity(intent);
						finish();
						return;
					}
				}
				catch(Exception e){}
				editor.putBoolean(sel_app+"_logged_in", true);
				editor.putString(sel_app+"_auth", "");
				editor.putString(sel_app+"_student_id", "");
				editor.apply();
				break;
		}
		
		
	}
}
