package com.example.webtoapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                showUnblockOptions();
            }
        });

        webView.loadUrl("https://brothersdoaz.org");
    }

    private void showUnblockOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Connection Issue")
                .setMessage("Site blocked? Try unblock options.")
                .setPositiveButton("Retry", (dialog, which) -> webView.reload())
                .setNegativeButton("Change DNS (demo)", (dialog, which) -> {
                    // TODO: Implement DNS/proxy switching here
                    webView.reload();
                })
                .show();
    }
}
