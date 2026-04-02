package lk.live.aplusewings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import android.view.View.OnClickListener;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity implements AsyncResponse {
    EditText input_pass;
    EditText input_usrname;
    CheckBox remember;
    Spinner select_app;

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_login);
        this.select_app = findViewById(R.id.login_select_app);
        this.input_usrname = findViewById(R.id.login_username);
        this.input_pass = findViewById(R.id.login_password);
        this.remember = findViewById(R.id.login_remember);
        TextView textView = findViewById(R.id.login_for_remember);
        Button button = findViewById(R.id.login_btn_login);
        util.setSpinner(this, this.select_app, util.getAppList(this));
        SharedPreferences sharedPreferences = getSharedPreferences("aplusewings", 0);
        String string = sharedPreferences.getString("default_app", "Aplus");
        this.input_usrname.setText(sharedPreferences.getString(new StringBuffer().append(string).append("_saved_username").toString(), ""));
        this.input_pass.setText(sharedPreferences.getString(new StringBuffer().append(string).append("_saved_password").toString(), ""));
        util.setSpinnerSelectedItem(this.select_app, string);
        textView.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					toggle_remember();
				}
			});
        button.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v1){
					login();
				}
			});
    }
	public void toggle_remember() {
		remember.setChecked(!remember.isChecked());
	}
	public void login() {
		Matcher matcher = Pattern.compile("^(?:\\+?94|0)?(7[01245678]\\d{7})$").matcher(input_usrname.getText().toString());
		if (!matcher.matches()) {
			Toast.makeText(getApplicationContext(), "Invalid phone number", Toast.LENGTH_LONG).show();
			return;
		}
		String stringBuffer = new StringBuffer().append("+94").append(matcher.group(1)).toString();
		String obj = input_pass.getText().toString();
		request requestVar = new request();
		requestVar.delegate = this;
		requestVar.context = this;
		requestVar.method = "POST";
		requestVar.message = "Authenticating...";
		requestVar.id = "login";
		requestVar.url = util.getConfig(this, select_app.getSelectedItem().toString(), "apiURL");
		requestVar.headers = util.getConfigArr(this, select_app.getSelectedItem().toString(), "headers");
		requestVar.postData = String.format(util.getConfig(this, select_app.getSelectedItem().toString(), "loginStudent"), stringBuffer, obj);
		requestVar.execute();
	}

    public void onProcessFinish(String str, String str2) {
        SharedPreferences.Editor edit = getSharedPreferences("aplusewings", 0).edit();
        String obj = this.select_app.getSelectedItem().toString();
        if (str2.equals("login")) {
            try {
                JSONObject jSONObject = new JSONObject(str).getJSONObject("data");
                String string = jSONObject.getJSONObject("loginStudent").getString("accessToken");
                String string2 = jSONObject.getJSONObject("loginStudent").getJSONObject("userAccount").getString("_id");
                if (this.remember.isChecked()) {
                    edit.putString(new StringBuffer().append(obj).append("_saved_username").toString(), this.input_usrname.getText().toString());
                    edit.putString(new StringBuffer().append(obj).append("_saved_password").toString(), this.input_pass.getText().toString());
                }
                edit.putString("default_app", obj);
                edit.putBoolean(new StringBuffer().append(obj).append("_logged_in").toString(), true);
                edit.putString(new StringBuffer().append(obj).append("_auth").toString(), string);
                edit.putString(new StringBuffer().append(obj).append("_student_id").toString(), string2);
                edit.apply();
                request requestVar = new request();
                requestVar.delegate = this;
                requestVar.context = this;
                requestVar.method = "POST";
                requestVar.id = "regDevice";
                requestVar.message = "Registering device";
                requestVar.url = util.getConfig(this, obj, "devReg");
                requestVar.headers = util.authorizeHeadersMy(this, obj, util.getConfigArr(this, obj, "headers"));
                requestVar.postData = String.format("student_id=%s&u=%s&p=%s", string2, URLEncoder.encode(this.input_usrname.getText().toString()), URLEncoder.encode(this.input_pass.getText().toString()));
                requestVar.execute(new String[0]);
                return;
            } catch (Exception e) {
                try {
                    Toast.makeText(getApplicationContext(), new JSONObject(str).getJSONArray("errors").getJSONObject(0).getString("message"), 1).show();
                    return;
                } catch (Exception e2) {
                    Toast.makeText(getApplicationContext(), e.toString(), 1).show();
                    return;
                }
            }
        }
        if (str2.equals("regDevice")) {
            try {
                if (new JSONObject(str).getBoolean("ok")) {
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
					finish();
                }
            } catch (Exception e4) {
            }
            edit.putBoolean(new StringBuffer().append(obj).append("_logged_in").toString(), true);
            edit.putString(new StringBuffer().append(obj).append("_auth").toString(), "");
            edit.putString(new StringBuffer().append(obj).append("_student_id").toString(), "");
            edit.apply();
        }
    }
}


