package com.aigy.securenote.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 全局主题适配工具类
 * 负责统一文字主题色和图标主题色的应用逻辑，确保全软件视觉风格统一
 */
public class ThemeUtils {

    /**
     * 获取当前系统主题的默认 Primary 颜色
     */
    public static int getDefaultPrimaryColor(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
    }

    /**
     * 获取用户定义的文字主色调（带系统主题色兜底）
     */
    public static int getTextMainColor(Context context) {
        return PrefUtils.getTextMainColor(context, getDefaultPrimaryColor(context));
    }

    /**
     * 获取用户定义的图标主色调（带系统主题色兜底）
     */
    public static int getIconMainColor(Context context) {
        return PrefUtils.getIconMainColor(context, getDefaultPrimaryColor(context));
    }

    /**
     * 批量应用文字主色调
     * 支持：TextView, Button (文字), 以及普通 View (背景色，常用于分割线)
     */
    public static void applyTextTheme(int color, View... views) {
        if (views == null) return;
        for (View v : views) {
            if (v == null) continue;
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(color);
            } else if (v instanceof Button) {
                ((Button) v).setTextColor(color);
            } else {
                // 通用 View 通常作为分割线，应用为背景色
                v.setBackgroundColor(color);
            }
        }
    }

    /**
     * 批量应用图标主色调
     * 支持：ImageView (滤镜), CheckBox (勾选框)
     */
    public static void applyIconTheme(int color, View... views) {
        if (views == null) return;
        ColorStateList csl = ColorStateList.valueOf(color);
        for (View v : views) {
            if (v == null) continue;
            if (v instanceof ImageView) {
                ((ImageView) v).setColorFilter(color);
            } else if (v instanceof CheckBox) {
                ((CheckBox) v).setButtonTintList(csl);
            }
        }
    }

    /**
     * 为 MaterialSwitch 应用专用的双色调逻辑
     * 轨道使用图标色，圆盘使用加深后的图标色
     */
    public static void applySwitchTheme(Context context, int iconColor, MaterialSwitch... switches) {
        if (switches == null) return;

        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        // 圆盘颜色计算逻辑：取图标色的 75% 亮度
        float t = 0.75f;
        int darkenedColor = Color.rgb(
                (int) (Color.red(iconColor) * t),
                (int) (Color.green(iconColor) * t),
                (int) (Color.blue(iconColor) * t)
        );

        // 特殊处理：如果是纯黑图标色，圆盘改用白色以保证可见度
        if ((iconColor & 0xFFFFFF) == 0x000000) {
            darkenedColor = Color.WHITE;
        }

        // 构造轨道颜色状态列表
        ColorStateList trackTintList = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{iconColor, Color.LTGRAY}
        );

        // 构造圆盘颜色状态列表
        ColorStateList thumbTintList = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{darkenedColor, isDarkMode ? Color.GRAY : Color.WHITE}
        );

        for (MaterialSwitch s : switches) {
            if (s != null) {
                s.setTrackTintList(trackTintList);
                s.setThumbTintList(thumbTintList);
            }
        }
    }
}

/* ---------------------------------------------------------
   使用示例（直接在 Activity 的 onResume 或初始化中调用）：

   // 1. 自动批量应用（推荐）：
   int txtColor = ThemeUtils.getTextMainColor(this);
   int icoColor = ThemeUtils.getIconMainColor(this);

   ThemeUtils.applyTextTheme(txtColor, binding.tvTitle, binding.btnAction);
   ThemeUtils.applyIconTheme(icoColor, binding.ivIcon, binding.checkBox);
   ThemeUtils.applySwitchTheme(this, icoColor, binding.mySwitch);

   // 2. 手动获取颜色值：
   int currentTextTheme = ThemeUtils.getTextMainColor(this);
   --------------------------------------------------------- */
