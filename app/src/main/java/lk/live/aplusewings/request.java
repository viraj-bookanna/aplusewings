package lk.live.aplusewings;

import android.os.AsyncTask;
import android.app.ProgressDialog;
import android.content.Context;
import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

public class request extends AsyncTask<String, Void, String> {
	public Context context;
	public String message = "";
	public String url;
	public String method = "GET";
	public JSONObject headers = null;
	public String postData = "";
	public AsyncResponse delegate = null;
	public boolean showProgress = true;
	public String id = null;
	ProgressDialog progress;

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progress = new ProgressDialog(context);
		progress.setMessage(message);
		progress.setIndeterminate(true);
		progress.setCancelable(false);
		if(showProgress){
			progress.show();
		}
	}

	@Override
	protected String doInBackground(String... params) {
		String response = "";
		try{
			HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
			con.setRequestMethod(method);
			con.setUseCaches(false);
			con.setDoInput(true);
			if(headers != null){
				Iterator<String> keys = headers.keys();
				while(keys.hasNext()) {
					String key = keys.next();
					String value = headers.getString(key);
					con.setRequestProperty(key, value);
				}
			}
			if(method == "POST"){
				con.setDoOutput(true);
				con.setRequestProperty("Content-Length", ""+postData.length());
				OutputStream outputStream = con.getOutputStream(); 
				Writer writer = new OutputStreamWriter(outputStream);
				writer.write(postData);
				writer.flush();
				writer.close();
			}
			int responseCode = con.getResponseCode();
			if (responseCode == HttpsURLConnection.HTTP_OK) {
				String line;
				BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
				while ((line = br.readLine()) != null) {
					response += line;
				}
			}
			else {
				response = "";    
			}
		}
		catch(Exception e){
			response = e.toString();
		}
		return response;
	}

	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);
		if(vars.signature_ok){
			progress.dismiss();
		}
		//call delegate
		delegate.onProcessFinish(result, id);
	}

}
