package lk.live.corsproxy;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;

public class ProxyServer extends NanoHTTPD {
    private static final String TAG = "ProxyServer";
    private OkHttpClient client;
	

    public ProxyServer(int port) throws IOException {
        super(port);

        // Create OkHttpClient with HTTP/2 support and disabled SSL verification
        client = createHttp2Client();

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Log.d(TAG, "Proxy server started on port " + port + " with HTTP/2 support");
    }
	
    private OkHttpClient createHttp2Client() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all client certificates
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all server certificates
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Create a hostname verifier that always returns true
            final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Build the OkHttpClient with HTTP/2 support
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier(hostnameVerifier)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .protocols(java.util.Arrays.asList(
							   okhttp3.Protocol.HTTP_2,
							   okhttp3.Protocol.HTTP_1_1
						   ));

            return builder.build();

        } catch (Exception e) {
            Log.e(TAG, "Error creating HTTP/2 client, falling back to default", e);
            // Fallback to default client with HTTP/2 support
            return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .protocols(java.util.Arrays.asList(
							   okhttp3.Protocol.HTTP_2,
							   okhttp3.Protocol.HTTP_1_1
						   ))
                .build();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String reqdata = null;

            // Handle different request methods
            if (Method.GET.equals(session.getMethod())) {
                // GET request: reqdata is in query string
                reqdata = session.getParms().get("reqdata");
                Log.d(TAG, "GET request, reqdata from query: " + reqdata);
            } 
            else if (Method.POST.equals(session.getMethod())) {
                // Check content type to determine how to parse
                String contentType = session.getHeaders().get("content-type");

                if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                    // Form data - parse normally
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    reqdata = session.getParms().get("reqdata");
                    Log.d(TAG, "POST form data, reqdata: " + reqdata);
                } 
                else {
                    // Raw JSON body - read directly
                    try {
                        // Get the content length
                        String contentLengthStr = session.getHeaders().get("content-length");
                        if (contentLengthStr != null) {
                            int contentLength = Integer.parseInt(contentLengthStr);

                            // Read the raw body
                            InputStream inputStream = session.getInputStream();
                            byte[] buffer = new byte[contentLength];
                            int bytesRead = inputStream.read(buffer, 0, contentLength);

                            if (bytesRead > 0) {
                                String body = new String(buffer, 0, bytesRead, "UTF-8");

                                // Check if it's JSON with reqdata field
                                if (body.trim().startsWith("{")) {
                                    try {
                                        JSONObject jsonBody = new JSONObject(body);
                                        if (jsonBody.has("reqdata")) {
                                            reqdata = jsonBody.getString("reqdata");
                                        } else {
                                            // If no reqdata field, maybe the whole body is reqdata?
                                            reqdata = body;
                                        }
                                    } catch (JSONException e) {
                                        // Not valid JSON, use as is
                                        reqdata = body;
                                    }
                                } else {
                                    reqdata = body;
                                }
                                Log.d(TAG, "POST raw body, reqdata: " + reqdata);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading POST body", e);
                    }
                }
            }

            if (reqdata == null || reqdata.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, 
											  "text/plain", "Missing reqdata parameter");
            }

            Log.d(TAG, "Final reqdata: " + reqdata);
            return fetchUrl(reqdata, session);

        } catch (Exception e) {
            Log.e(TAG, "Error in serve", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, 
										  "text/plain", e.toString());
        }
    }

    private Response fetchUrl(String reqdata, IHTTPSession session) {
		try {
			// Parse the request data
			JSONObject requestJson = new JSONObject(reqdata);

			// Get URL (required)
			String url = requestJson.getString("url");

			// Get options (with default empty object)
			JSONObject options = requestJson.optJSONObject("options");
			if (options == null) {
				options = new JSONObject();
			}

			// Extract request parameters
			String method = options.optString("method", "GET");
			JSONObject headers = options.optJSONObject("headers");
			String body = options.optString("body", "");

			Log.d(TAG, "Fetching: " + method + " " + url + " (HTTP/2 supported)");

			// Build OkHttp Request
			okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
				.url(url);

			// Add headers
			if (headers != null) {
				Iterator<String> keys = headers.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					requestBuilder.addHeader(key, headers.getString(key));
				}
			}

			// Add body for non-GET requests
			if (!"GET".equals(method) && !body.isEmpty()) {
				okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/octet-stream");
				if (headers != null && headers.has("Content-Type")) {
					mediaType = okhttp3.MediaType.parse(headers.getString("Content-Type"));
				}

				okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(mediaType, body.getBytes("UTF-8"));
				requestBuilder.method(method, requestBody);
			} else {
				requestBuilder.method(method, null);
			}

			// Execute request with OkHttp
			okhttp3.Request okRequest = requestBuilder.build();
			okhttp3.Call call = client.newCall(okRequest);

			try (okhttp3.Response okResponse = call.execute()) {

				// Get protocol used (HTTP/1.1 or HTTP/2)
				Log.d(TAG, "Response protocol: " + okResponse.protocol());

				// Build response headers
				Map<String, String> responseHeaders = new HashMap<>();
				String[] passedHeaders = {"Content-Length", "Content-Type"};

				okhttp3.Headers okHeaders = okResponse.headers();
				for (String header : passedHeaders) {
					String value = okHeaders.get(header);
					if (value != null) {
						responseHeaders.put(header, value);
					}
				}

				// Handle CORS if requested
				if (session.getParameters().containsKey("unblock_cors")) {
					responseHeaders.put("Access-Control-Allow-Origin", "*");
					Log.d(TAG, "Added CORS header");
				}

				// Get response body
				okhttp3.ResponseBody responseBody = okResponse.body();

				// Create NanoHTTPD response
				fi.iki.elonen.NanoHTTPD.Response response;

				if (responseBody != null) {
					// Get the byte array
					byte[] responseData = responseBody.bytes();

					// Create an InputStream from the byte array
					java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(responseData);

					// Use newChunkedResponse with InputStream for binary data
					response = newChunkedResponse(
						fi.iki.elonen.NanoHTTPD.Response.Status.lookup(okResponse.code()),
						responseHeaders.getOrDefault("Content-Type", "application/octet-stream"),
						inputStream
					);

					// Set Content-Length header since we know the size
					response.addHeader("Content-Length", String.valueOf(responseData.length));
				} else {
					response = newFixedLengthResponse(
						fi.iki.elonen.NanoHTTPD.Response.Status.lookup(okResponse.code()),
						responseHeaders.getOrDefault("Content-Type", "text/plain"),
						""
					);
				}

				// Add headers to response
				for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
					response.addHeader(header.getKey(), header.getValue());
				}

				// Add protocol info header for debugging
				response.addHeader("X-Proxy-Protocol", okResponse.protocol().toString());

				return response;
			}

		} catch (JSONException e) {
			Log.e(TAG, "JSON parsing error", e);
			return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.BAD_REQUEST, 
										  "text/plain", "Invalid JSON: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "Fetch error", e);
			return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR, 
										  "text/plain", "Fetch error: " + e.toString());
		} catch (Exception e) {
			Log.e(TAG, "Unexpected error", e);
			return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR, 
										  "text/plain", "Unexpected error: " + e.toString());
		}
	}
}

