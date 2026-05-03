package com.aigy.securenote;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.databinding.FragmentScheduleBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 日程页面 Fragment
 * 优化：为“返回本周”按钮添加了原生的缩放入场与退场动画，提升视觉体验。
 */
public class ScheduleFragment extends Fragment {

    private FragmentScheduleBinding binding;
    private static final int CELL_HEIGHT_DP = 100;
    private int columnWidthPx;
    private final boolean[] isPastDayArray = new boolean[7];
    private AppDatabase db;
    private List<Note> allNotes;
    private final Calendar[] weekDates = new Calendar[7];
    private boolean isHideBorders = false;

    private int weekOffset = 0;
    private GestureDetector gestureDetector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentScheduleBinding.inflate(inflater, container, false);
        db = AppDatabase.getDatabase(requireContext());
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        columnWidthPx = screenWidth / 8;

        adjustFixedViewsWidth();
        setupScrollSync();
        setupGestureDetection();
        initBackToTodayFab();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        isHideBorders = PrefUtils.isHideScheduleBorder(requireContext());
        loadNotesAndSetupTable();
        refreshTitle();
        applyFabColor();
    }

    /**
     * 初始化“返回本周”按钮的点击事件与初始状态
     */
    private void initBackToTodayFab() {
        binding.fabBackToToday.setOnClickListener(v -> {
            if (weekOffset != 0) {
                weekOffset = 0;
                refreshTitle();
                loadNotesAndSetupTable();
                updateFabVisibility();
            }
        });
        // 初始检查显隐状态
        updateFabVisibility();
    }

    /**
     * 动态应用按钮主题色
     */
    private void applyFabColor() {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;
        int iconColor = PrefUtils.getIconMainColor(requireContext(), defaultColor);
        binding.fabBackToToday.setBackgroundTintList(ColorStateList.valueOf(iconColor));
    }

    /**
     * 更新悬浮按钮的显隐状态，并带入原生缩放动画
     */
    private void updateFabVisibility() {
        if (binding == null) return;
        
        // weekOffset != 0 表示当前不在本周
        if (weekOffset != 0) {
            // 如果当前处于隐藏状态，则执行入场动画
            if (!binding.fabBackToToday.isShown()) {
                binding.fabBackToToday.show();
            }
        } else {
            // 如果当前处于显示状态，则执行退场动画
            if (binding.fabBackToToday.isShown()) {
                binding.fabBackToToday.hide();
            }
        }
    }

    public void refreshTitle() {
        if (isAdded() && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateScheduleTitle(weekOffset);
        }
    }

    private void setupGestureDetection() {
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 50;
            private static final int SWIPE_VELOCITY_THRESHOLD = 50;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) changeWeek(-1);
                        else changeWeek(1);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void handleTouch(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
    }

    private void changeWeek(int delta) {
        weekOffset += delta;
        refreshTitle();
        loadNotesAndSetupTable();
        // 每次切换周时刷新按钮状态
        updateFabVisibility();
    }

    private void adjustFixedViewsWidth() {
        ViewGroup.LayoutParams cornerParams = binding.cornerLabel.getLayoutParams();
        cornerParams.width = columnWidthPx;
        binding.cornerLabel.setLayoutParams(cornerParams);

        ViewGroup.LayoutParams vScrollParams = binding.headerVScroll.getLayoutParams();
        vScrollParams.width = columnWidthPx;
        binding.headerVScroll.setLayoutParams(vScrollParams);
    }

    private void loadNotesAndSetupTable() {
        new Thread(() -> {
            allNotes = db.noteDao().getAllNotesSync();
            if (isAdded()) {
                requireActivity().runOnUiThread(this::setupScheduleTable);
            }
        }).start();
    }

    private void setupScheduleTable() {
        if (binding == null) return;
        binding.headerHLayout.removeAllViews();
        binding.headerVTable.removeAllViews();
        binding.contentTable.removeAllViews();

        Calendar now = Calendar.getInstance();
        Calendar todayStart = (Calendar) now.clone();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Calendar actualNow = Calendar.getInstance();
        int currentDayIdx = (actualNow.get(Calendar.DAY_OF_WEEK) + 5) % 7; 
        int currentHour = actualNow.get(Calendar.HOUR_OF_DAY);
        int currentRowIdx = currentHour / 2;

        SimpleDateFormat dateFmt = new SimpleDateFormat("MM/dd", Locale.getDefault());
        String[] weekDaysNames = {
                getString(R.string.day_mon), getString(R.string.day_tue),
                getString(R.string.day_wed), getString(R.string.day_thu),
                getString(R.string.day_fri), getString(R.string.day_sat),
                getString(R.string.day_sun)
        };

        for (int i = 0; i < 7; i++) {
            isPastDayArray[i] = calendar.before(todayStart);
            weekDates[i] = (Calendar) calendar.clone();
            String fullHeaderText = weekDaysNames[i] + "\n" + dateFmt.format(calendar.getTime());
            binding.headerHLayout.addView(createHeaderCell(fullHeaderText));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        String[] intervals = {
                "00:00\n|\n02:00", "02:00\n|\n04:00", "04:00\n|\n06:00", "06:00\n|\n08:00",
                "08:00\n|\n10:00", "10:00\n|\n12:00", "12:00\n|\n14:00", "14:00\n|\n16:00",
                "16:00\n|\n18:00", "18:00\n|\n20:00", "20:00\n|\n22:00", "22:00\n|\n24:00"
        };

        for (int rowIdx = 0; rowIdx < intervals.length; rowIdx++) {
            TableRow vHeaderRow = new TableRow(getContext());
            vHeaderRow.addView(createTimeCell(intervals[rowIdx]));
            binding.headerVTable.addView(vHeaderRow);

            TableRow contentRow = new TableRow(getContext());
            for (int colIdx = 0; colIdx < 7; colIdx++) {
                List<Note> notesInCell = getNotesForCell(colIdx, rowIdx);
                boolean isCurrent = (weekOffset == 0 && colIdx == currentDayIdx && rowIdx == currentRowIdx);
                boolean isPast = isPastDayArray[colIdx] || (weekOffset == 0 && colIdx == currentDayIdx && rowIdx < currentRowIdx);
                contentRow.addView(createContentCell(notesInCell, isPast, isCurrent, weekDates[colIdx]));
            }
            binding.contentTable.addView(contentRow);
        }
    }

    private View createContentCell(List<Note> notes, boolean isPast, boolean isCurrent, Calendar cellDate) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(6, 12, 6, 8);
        container.setLayoutParams(new TableRow.LayoutParams(columnWidthPx, dpToPx(CELL_HEIGHT_DP)));
        container.setBackground(getContentBackground(isPast, isCurrent));

        TypedValue tvAttr = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvAttr, true);
        int textColor = tvAttr.data;
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tvAttr, true);
        int primaryColor = tvAttr.data;

        if (notes.isEmpty()) {
            container.setOnClickListener(v -> {});
        } else if (notes.size() == 1) {
            Note note = notes.get(0);
            TextView titleTv = new TextView(getContext());
            titleTv.setText(note.getTitle());
            titleTv.setTextSize(10);
            titleTv.setTextColor(textColor);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            titleTv.setMaxLines(3);
            container.addView(titleTv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

            TextView remainTv = new TextView(getContext());
            remainTv.setText(getRemainingTime(note, cellDate));
            remainTv.setTextSize(8.5f);
            remainTv.setTextColor(isPast ? (textColor & 0x61FFFFFF) : (textColor & 0x99FFFFFF));
            remainTv.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            container.addView(remainTv);

            container.setOnClickListener(v -> openNote(note.getId()));
        } else {
            container.setGravity(Gravity.CENTER);
            TextView countTv = new TextView(getContext());
            countTv.setText(notes.size() + " 个笔记");
            countTv.setTextSize(10);
            countTv.setTextColor(isCurrent ? textColor : primaryColor);
            countTv.setTypeface(null, Typeface.BOLD);
            container.addView(countTv);
            container.setOnClickListener(v -> showNotesList(notes, cellDate));
        }
        return container;
    }

    private android.graphics.drawable.Drawable getContentBackground(boolean isPast, boolean isCurrent) {
        TypedValue tv = new TypedValue();
        int bgColor;
        int strokeColor = 0;
        int defaultStrokeWidthPx = (int) dpToPx(0.5f);

        if (isCurrent) {
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, tv, true);
            bgColor = tv.data;
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, tv, true);
            strokeColor = tv.data;
        } else if (isPast) {
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, tv, true);
            bgColor = tv.data;
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, tv, true);
            strokeColor = tv.data;
        } else {
            requireContext().getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true);
            bgColor = tv.data;
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, tv, true);
            strokeColor = tv.data;
        }

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(bgColor);
        if (!isHideBorders || isCurrent) {
            shape.setStroke(defaultStrokeWidthPx, strokeColor);
        }
        if (isCurrent) shape.setCornerRadius(dpToPx(4));
        return shape;
    }

    private String getRemainingTime(Note note, Calendar cellDate) {
        try {
            long targetTime;
            if (note.getReminderDate() != null && !note.getReminderDate().isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                targetTime = sdf.parse(note.getReminderDate() + " " + note.getOnceReminderTime()).getTime();
            } else {
                Calendar cal = (Calendar) cellDate.clone();
                String[] timeParts = note.getReminderTime().split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                targetTime = cal.getTimeInMillis();
            }
            long diff = targetTime - System.currentTimeMillis();
            if (diff < 0) return "已结束";
            long days = diff / (1000 * 60 * 60 * 24);
            long hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
            long mins = (diff % (1000 * 60 * 60)) / (1000 * 60);
            if (days > 0) return "剩" + days + "天" + hours + "h";
            else if (hours > 0) return "剩" + hours + "h" + mins + "m";
            else return "剩" + mins + "m";
        } catch (Exception e) { return ""; }
    }

    private List<Note> getNotesForCell(int colIdx, int rowIdx) {
        List<Note> result = new ArrayList<>();
        Calendar cellDate = weekDates[colIdx];
        int startHour = rowIdx * 2;
        int endHour = startHour + 2;
        SimpleDateFormat sdfDateOnly = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (Note note : allNotes) {
            try {
                if (note.getReminderDate() != null && !note.getReminderDate().isEmpty()) {
                    if (note.getReminderDate().equals(sdfDateOnly.format(cellDate.getTime()))) {
                        int hour = Integer.parseInt(note.getOnceReminderTime().split(":")[0]);
                        if (hour >= startHour && hour < endHour) result.add(note);
                    }
                } else if (note.getReminderTime() != null && !note.getReminderTime().isEmpty()) {
                    int hour = Integer.parseInt(note.getReminderTime().split(":")[0]);
                    if (hour >= startHour && hour < endHour) result.add(note);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private void openNote(long noteId) {
        Intent intent = new Intent(getContext(), writeActivity.class);
        intent.putExtra("note_id", noteId);
        startActivity(intent);
    }

    private void showNotesList(List<Note> notes, Calendar cellDate) {
        String[] displayItems = new String[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            String remain = getRemainingTime(n, cellDate);
            displayItems[i] = n.getTitle() + " (" + remain + ")";
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("时段任务列表")
                .setItems(displayItems, (dialog, which) -> openNote(notes.get(which).getId()))
                .setNegativeButton("返回", null)
                .show();
    }

    private void setupScrollSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.contentVScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> binding.headerVScroll.scrollTo(0, y));
            binding.headerVScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> binding.contentVScroll.scrollTo(0, y));
            binding.contentHScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> binding.headerHScroll.scrollTo(x, 0));
            binding.headerHScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> binding.contentHScroll.scrollTo(x, 0));
        }
    }

    private TextView createHeaderCell(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextSize(13);
        TypedValue val = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, val, true);
        tv.setTextColor(val.data);
        tv.setBackgroundResource(R.drawable.bg_schedule_cell_header);
        tv.setLayoutParams(new LinearLayout.LayoutParams(columnWidthPx, ViewGroup.LayoutParams.MATCH_PARENT));
        return tv;
    }

    private TextView createTimeCell(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(11);
        TypedValue val = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, val, true);
        tv.setTextColor(val.data);
        tv.setBackgroundResource(R.drawable.bg_schedule_cell_header);
        tv.setLayoutParams(new TableRow.LayoutParams(columnWidthPx, dpToPx(CELL_HEIGHT_DP)));
        return tv;
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
    
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
