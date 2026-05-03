package com.aigy.securenote.Statusbar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class Permission {
    private static final String TAG = "PermissionHelper";
    private static final String PREF_NAME = "permission_prefs";
    private static final String KEY_HAS_REQUESTED = "has_requested_location";

    private final ComponentActivity activity;
    private final PermissionCallback callback;
    private final ActivityResultLauncher<String[]> requestPermissionLauncher;
    private final SharedPreferences sp;
    
    private boolean isRequesting = false;

    public static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }

    public Permission(ComponentActivity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        this.requestPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    isRequesting = false;
                    sp.edit().putBoolean(KEY_HAS_REQUESTED, true).apply();
                    handlePermissionResult(result);
                }
        );
    }

    /**
     * 检查并请求定位权限
     * 优化：仅调用系统原生请求，不包含任何 App 层面的弹窗。
     */
    public void checkAndRequestLocationPermission() {
        if (hasLocationPermission()) {
            if (callback != null) callback.onPermissionGranted();
            return;
        }

        if (isRequesting) return;
        performSystemRequest();
    }

    private void performSystemRequest() {
        isRequesting = true;
        requestPermissionLauncher.launch(LOCATION_PERMISSIONS);
    }

    private void handlePermissionResult(Map<String, Boolean> result) {
        boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

        if (fineGranted || coarseGranted) {
            if (callback != null) callback.onPermissionGranted();
        } else {
            // 优化：拒绝后直接回调，不再弹出说明弹窗，防止第三方系统重复弹窗
            if (callback != null) callback.onPermissionDenied();
        }
    }

    /**
     * 电池白名单申请
     * 使用符合主题风格的对话框告知用户原因
     */
    @SuppressLint("BatteryLife")
    public void checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("确保提醒准时")
                        .setMessage("为了防止笔记提醒被系统后台杀掉，建议将本应用设为“不优化电池”。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                activity.startActivity(intent);
                            }
                        })
                        .setNegativeButton("暂不需要", null)
                        .show();
            }
        }
    }

    /**
     * 自启动引导
     */
    public void requestAutoStartPermission() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("允许自启动")
                .setMessage("开启自启动权限可确保手机重启后提醒依然有效。")
                .setPositiveButton("去开启", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                    activity.startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
