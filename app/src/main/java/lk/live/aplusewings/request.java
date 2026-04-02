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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


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
		String responseString = "";


		try {
			OkHttpClient client = new OkHttpClient();

			Request.Builder builder = new Request.Builder()
                .url(url);

			// Add headers if available
			if (headers != null) {
				Iterator<String> keys = headers.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					String value = headers.getString(key);
					builder.addHeader(key, value);
				}
			}

			// Handle GET / POST
			if ("POST".equalsIgnoreCase(method)) {
				RequestBody body = RequestBody.create(
					null,
                    postData
				);
				builder.post(body);
			} else {
				builder.get();
			}

			// Build request
			Request request = builder.build();

			// Execute
			try (Response response = client.newCall(request).execute()) {
				if (response.isSuccessful()) {
					responseString = response.body().string();
				} else {
					responseString = "HTTP Error: " + response.code();
				}
			}
		} catch (Exception e) {
			responseString = "Exception: " + e.toString();
		}

		return responseString;
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

