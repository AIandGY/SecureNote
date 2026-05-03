package com.aigy.securenote.Statusbar;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;

import com.aigy.securenote.R;
import com.aigy.securenote.databinding.ActivityMainTitleBinding;
import com.aigy.securenote.utils.PoetryManager;
import com.aigy.securenote.utils.PrefUtils;

/**
 * 负责主页标题栏诗词逻辑的控制器
 * 功能：在标题栏显示古诗，并在局域网服务开启时，以周期轮播服务状态提示
 */
public class MainTitleController {
    private static final String TAG = "MainTitleController";
    private final ActivityMainTitleBinding binding;
    private final PoetryManager poetryManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentPoetry = "";
    private boolean isLifecycleActive = false;

    // 缓存原始状态以便恢复（包括颜色和透明度）
    private int originalTextColor;
    private float originalAlpha;

    // 局域网服务状态提示的专用颜色
    private static final int STATUS_COLOR = Color.parseColor("#FF4500");

    // 轮播配置：INTERVAL_TOTAL为总周期，INTERVAL_STATUS为状态显示时间，剩余显示古诗
    private static final int INTERVAL_TOTAL = 15000;
    private static final int INTERVAL_STATUS = 3000;

    public MainTitleController(ActivityMainTitleBinding binding) {
        this.binding = binding;
        this.poetryManager = new PoetryManager(binding.getRoot().getContext());

        // 初始化时记录 TextView 的原始外观属性（对应布局中的 0.2 alpha 和主题文字颜色）
        if (binding.poem != null) {
            this.originalTextColor = binding.poem.getCurrentTextColor();
            this.originalAlpha = binding.poem.getAlpha();
        }
    }

    /**
     * 初始化控制器，拉取首条古诗数据
     */
    public void init() {
        refreshPoetry();
    }

    /**
     * 开启生命周期管理（通常在 Activity 的 onResume 中调用）
     */
    public void startLifecycle() {
        if (!isLifecycleActive) {
            isLifecycleActive = true;
            // 确保开始时立即恢复古诗状态，避免界面残留之前的状态提示
            restorePoetryView();
            scheduleNextCycle();
        }
    }

    /**
     * 停止生命周期管理（通常在 Activity 的 onPause 中调用）
     */
    public void stopLifecycle() {
        isLifecycleActive = false;
        handler.removeCallbacksAndMessages(null);
        // 确保停止时恢复古诗显示，防止状态提示（红色文字）被“冻结”在界面上
        restorePoetryView();
    }

    /**
     * 核心调度逻辑：处理古诗与服务状态的切换
     */
    private void scheduleNextCycle() {
        if (!isLifecycleActive) return;

        // 计算古诗显示时长（总周期 - 状态显示时长）
        int poemDuration = INTERVAL_TOTAL - INTERVAL_STATUS;

        handler.postDelayed(() -> {
            if (!isLifecycleActive) return;

            // 检查局域网服务开启状态（普通开启或强制开启）
            boolean isLanEnabled = PrefUtils.isLanServiceEnabled(binding.getRoot().getContext());
            boolean isLanForced = PrefUtils.isLanForceEnable(binding.getRoot().getContext());

            if (isLanEnabled || isLanForced) {
                // 1. 切换到状态显示模式
                String statusMsg = isLanForced ?
                        binding.getRoot().getContext().getString(R.string.lan_service_forced_hint) :
                        binding.getRoot().getContext().getString(R.string.lan_service_active_hint);

                if (binding.poem != null) {
                    // 获取当前显示古诗时的宽度
                    int poemWidth = binding.poem.getWidth();
                    if (poemWidth > 0) {
                        // 锁定宽度为古诗长度，并设置文字居中对齐
                        ViewGroup.LayoutParams lp = binding.poem.getLayoutParams();
                        lp.width = poemWidth;
                        binding.poem.setLayoutParams(lp);
                        binding.poem.setGravity(Gravity.CENTER);
                    }
                    
                    binding.poem.setText(statusMsg);
                    binding.poem.setTextColor(STATUS_COLOR);
                    // 显示状态时设为不透明，确保用户能看清
                    binding.poem.setAlpha(1.0f);
                }

                // 2. 达到 INTERVAL_STATUS 指定时长后，切回古诗并恢复外观
                handler.postDelayed(() -> {
                    if (isLifecycleActive && binding.poem != null) {
                        restorePoetryView();
                        scheduleNextCycle(); // 递归进行下一次调度
                    }
                }, INTERVAL_STATUS);
            } else {
                // 如果服务未开启，确保视图恢复古诗，并继续等待下一个周期检测
                restorePoetryView();
                scheduleNextCycle();
            }
        }, poemDuration);
    }

    /**
     * 恢复古诗显示及其原始外观样式
     */
    private void restorePoetryView() {
        if (binding.poem != null) {
            // 恢复宽度为 wrap_content，恢复默认左对齐（含垂直居中）
            ViewGroup.LayoutParams lp = binding.poem.getLayoutParams();
            if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                binding.poem.setLayoutParams(lp);
                binding.poem.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            }

            binding.poem.setText(currentPoetry);
            binding.poem.setTextColor(originalTextColor);
            binding.poem.setAlpha(originalAlpha);
        }
    }

    /**
     * 刷新并获取新的古诗内容
     */
    public void refreshPoetry() {
        poetryManager.getPoetry(new PoetryManager.PoetryCallback() {
            @Override
            public void onSuccess(String content) {
                currentPoetry = content;
                if (binding.poem != null) {
                    // 只有在非状态显示期间才立即更新文字，避免覆盖正在轮播的状态
                    if (binding.poem.getAlpha() == originalAlpha) {
                        binding.poem.setText(content);
                    }
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "获取古诗失败: " + error);
            }
        });
    }
}
