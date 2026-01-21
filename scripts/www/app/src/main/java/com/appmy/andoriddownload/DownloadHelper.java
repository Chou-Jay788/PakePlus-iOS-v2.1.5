package com.appmy.andoriddownload;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadHelper {
    
    private final Activity activity;
    private final DownloadManager downloadManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private View notificationView;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private TextView tvFileName;
    private long currentDownloadId = -1;
    private boolean isDownloading = false;
    
    public DownloadHelper(Activity activity) {
        this.activity = activity;
        this.downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
    }
    
    public void download(String url, String userAgent, String contentDisposition, 
                         String mimeType, String cookies) {
        if (isDownloading) {
            Toast.makeText(activity, "已有下载任务进行中", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            
            request.setTitle(fileName);
            request.setDescription("正在下载...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            currentDownloadId = downloadManager.enqueue(request);
            isDownloading = true;
            
            showNotification(fileName);
            startProgressUpdate();
            
        } catch (Exception e) {
            Toast.makeText(activity, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showNotification(String fileName) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        
        notificationView = LayoutInflater.from(activity).inflate(R.layout.view_download_notification, null);
        progressBar = notificationView.findViewById(R.id.progressBar);
        tvProgress = notificationView.findViewById(R.id.tvProgress);
        tvFileName = notificationView.findViewById(R.id.tvFileName);
        ImageView btnClose = notificationView.findViewById(R.id.btnClose);
        
        tvFileName.setText(fileName);
        progressBar.setProgress(0);
        tvProgress.setText("准备下载...");
        
        btnClose.setOnClickListener(v -> dismissNotification());
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        params.topMargin = getStatusBarHeight();
        
        rootView.addView(notificationView, params);
        
        // 入场动画
        notificationView.setTranslationY(-200);
        notificationView.animate().translationY(0).setDuration(300).start();
    }
    
    private void dismissNotification() {
        if (notificationView != null) {
            notificationView.animate()
                .translationY(-200)
                .setDuration(200)
                .withEndAction(() -> {
                    ViewGroup rootView = activity.findViewById(android.R.id.content);
                    rootView.removeView(notificationView);
                    notificationView = null;
                })
                .start();
        }
    }
    
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    
    private void startProgressUpdate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isDownloading || currentDownloadId == -1) return;
                
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(currentDownloadId);
                
                Cursor cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    
                    int status = cursor.getInt(statusIndex);
                    long bytesDownloaded = cursor.getLong(bytesIndex);
                    long totalBytes = cursor.getLong(totalIndex);
                    
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        if (totalBytes > 0) {
                            int progress = (int) ((bytesDownloaded * 100) / totalBytes);
                            updateProgress(progress, bytesDownloaded, totalBytes);
                        }
                        handler.postDelayed(this, 200);
                    } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        onDownloadComplete(true);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        onDownloadComplete(false);
                    }
                    cursor.close();
                } else {
                    handler.postDelayed(this, 200);
                }
            }
        });
    }
    
    private void updateProgress(int progress, long downloaded, long total) {
        if (progressBar != null && tvProgress != null) {
            progressBar.setProgress(progress);
            tvProgress.setText(progress + "%  " + formatSize(downloaded) + " / " + formatSize(total));
        }
    }
    
    private void onDownloadComplete(boolean success) {
        isDownloading = false;
        currentDownloadId = -1;
        
        if (tvProgress != null) {
            tvProgress.setText(success ? "下载完成" : "下载失败");
        }
        if (progressBar != null) {
            progressBar.setProgress(100);
        }
        
        // 2秒后自动关闭
        handler.postDelayed(this::dismissNotification, 2000);
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
