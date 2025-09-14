package com.brothers.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private final String domain = "brothersdoaz.org";
    private final String primaryUrl = "https://brothersdoaz.org";
    // fallback proxy URL (optional - edit later if you have a proxy)
    private final String fallbackProxy = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(android.view.View.GONE);
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showUnblockDialog();
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // For simplicity, do not intercept; we rely on WebView default requests.
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                // use DownloadManager to handle downloads and support resuming where possible
                if (checkAndRequestStoragePermission()) {
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Downloading file...") ;
                    request.setTitle(filename);
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    String folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/Brothers Do AZ";
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Brothers Do AZ/" + filename);

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    Toast.makeText(getApplicationContext(), "Download started: " + filename, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Storage permission required to download", Toast.LENGTH_LONG).show();
                }
            }
        });

        // load initial URL
        webView.loadUrl(primaryUrl);
    }

    private boolean checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // scoped storage: DownloadManager will handle saving, permission may not be required
            return true;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showUnblockDialog() {
        final String[] options = new String[] {"Retry","Google DNS","Cloudflare DNS","Quad9 DNS","Use Proxy (if configured)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection Issue");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Retry
                        webView.reload();
                        break;
                    case 1: // Google
                        resolveWithDoH("https://dns.google/resolve?name=" + domain + "&type=A");
                        break;
                    case 2: // Cloudflare
                        resolveWithDoH("https://cloudflare-dns.com/dns-query?name=" + domain + "&type=A");
                        break;
                    case 3: // Quad9
                        resolveWithDoH("https://dns.quad9.net/dns-query?name=" + domain + "&type=A");
                        break;
                    case 4: // proxy
                        if (fallbackProxy != null && !fallbackProxy.isEmpty()) {
                            webView.loadUrl(fallbackProxy);
                        } else {
                            Toast.makeText(MainActivity.this, "No proxy configured", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        });
        builder.setCancelable(true);
        builder.show();
    }

    private void resolveWithDoH(String dohUrl) {
        new DoHTask().execute(dohUrl);
    }

    private class DoHTask extends AsyncTask<String, Void, String> {
        OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String url = strings[0];
            Request request = new Request.Builder().url(url).get().addHeader("Accept","application/dns-json").build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                String body = response.body().string();
                JSONObject obj = new JSONObject(body);
                if (obj.has("Answer")) {
                    JSONArray ans = obj.getJSONArray("Answer");
                    for (int i=0;i<ans.length();i++) {
                        JSONObject a = ans.getJSONObject(i);
                        String data = a.getString("data");
                        if (data!=null && data.length()>0 && data.matches("\\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                            return data;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String ip) {
            if (ip!=null) {
                Toast.makeText(MainActivity.this, "Resolved IP: " + ip + ". Attempting to load via IP (http)", Toast.LENGTH_LONG).show();
                Map<String, String> headers = new HashMap<>();
                headers.put("Host", domain);
                webView.loadUrl("http://" + ip + "/", headers);
            } else {
                Toast.makeText(MainActivity.this, "DNS resolution failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
