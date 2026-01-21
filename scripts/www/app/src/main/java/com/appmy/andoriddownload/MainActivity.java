package com.appmy.andoriddownload;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ImageButton btnBack, btnHome;
    private DownloadHelper downloadHelper;
    
    // 全屏视频相关
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    
    // 文件选择
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<String> fileChooserLauncher;
    
    // 隐藏设置相关
    private int homeClickCount = 0;
    private long lastClickTime = 0;
    private boolean isDialogShowing = false;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE);
        downloadHelper = new DownloadHelper(this);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        
        // 文件选择器
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(uri != null ? new Uri[]{uri} : null);
                    filePathCallback = null;
                }
            }
        );
        
        initViews();
        setupWebView();
        loadHomePage();
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);
        
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        
        btnHome.setOnClickListener(v -> {
            if (isDialogShowing) return;
            checkHiddenTrigger();
            if (!isDialogShowing) {
                loadHomePage();
            }
        });
    }
    
    private void checkHiddenTrigger() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastClickTime < AppConfig.CLICK_INTERVAL) {
            homeClickCount++;
            if (homeClickCount >= AppConfig.CLICK_THRESHOLD) {
                homeClickCount = 0;
                showSetHomeDialog();
            }
        } else {
            homeClickCount = 1;
        }
        lastClickTime = currentTime;
    }
    
    private void showSetHomeDialog() {
        isDialogShowing = true;
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_home, null);
        EditText etHomeUrl = dialogView.findViewById(R.id.etHomeUrl);
        etHomeUrl.setText(getHomeUrl());
        
        new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("保存", (dialog, which) -> {
                isDialogShowing = false;
                String url = etHomeUrl.getText().toString().trim();
                if (!url.isEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }
                    saveHomeUrl(url);
                    loadHomePage();
                    Toast.makeText(this, "主页已设置", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", (dialog, which) -> isDialogShowing = false)
            .setNeutralButton("恢复默认", (dialog, which) -> {
                isDialogShowing = false;
                prefs.edit().remove(AppConfig.KEY_HOME_URL).apply();
                loadHomePage();
                Toast.makeText(this, "已恢复默认主页", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    private String getHomeUrl() {
        return prefs.getString(AppConfig.KEY_HOME_URL, AppConfig.DEFAULT_HOME_URL);
    }
    
    private void saveHomeUrl(String url) {
        prefs.edit().putString(AppConfig.KEY_HOME_URL, url).apply();
    }
    
    private void loadHomePage() {
        webView.loadUrl(getHomeUrl());
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " Mobile");
        
        // 不使用缓存
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            // JS alert 弹窗
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("确定", (d, w) -> result.confirm())
                    .setCancelable(false)
                    .show();
                return true;
            }
            
            // JS confirm 弹窗
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("确定", (d, w) -> result.confirm())
                    .setNegativeButton("取消", (d, w) -> result.cancel())
                    .setCancelable(false)
                    .show();
                return true;
            }
            
            // JS prompt 弹窗
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("确定", (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("取消", (d, w) -> result.cancel())
                    .setCancelable(false)
                    .show();
                return true;
            }
            
            // 页面离开确认 - 直接允许
            @Override
            public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }
            
            // 文件选择 <input type="file">
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                filePathCallback = callback;
                fileChooserLauncher.launch("*/*");
                return true;
            }
            
            // 全屏视频
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }
            
            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    fullscreenContainer.removeView(customView);
                    fullscreenContainer.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    customView = null;
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                        customViewCallback = null;
                    }
                }
            }
            
            // 地理位置权限
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            
            // 摄像头/麦克风权限
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            
            // window.open() 弹出新窗口
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView newWebView = new WebView(MainActivity.this);
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        webView.loadUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });
        
        // 下载监听 - 显示进度对话框
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, 
                    String contentDisposition, String mimeType, long contentLength) {
                String cookies = CookieManager.getInstance().getCookie(url);
                downloadHelper.download(url, userAgent, contentDisposition, mimeType, cookies);
            }
        });
    }
    
    @Override
    public void onBackPressed() {
        // 退出全屏视频
        if (customView != null) {
            customViewCallback.onCustomViewHidden();
            fullscreenContainer.removeView(customView);
            fullscreenContainer.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            customView = null;
            customViewCallback = null;
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
