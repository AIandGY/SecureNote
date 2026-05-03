package com.aigy.securenote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aigy.securenote.Database.Note;
import com.aigy.securenote.databinding.ItemNoteBinding;
import com.aigy.securenote.utils.PrefUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记列表适配器
 * 负责主页笔记项的展示、排序、过滤及主题色应用
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {

    private List<Note> allNotes = new ArrayList<>(); 
    private List<Note> filteredNotes = new ArrayList<>(); 
    
    private final OnItemClickListener listener;
    private OnItemLongClickListener longListener;
    private OnStartDragListener dragStartListener;
    
    private boolean isSelectionMode = false;
    private final Set<Long> selectedNoteIds = new HashSet<>();

    public interface OnItemClickListener {
        void onItemClick(Note note);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Note note);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public NoteAdapter(List<Note> notes, OnItemClickListener listener) {
        this.allNotes = notes;
        this.filteredNotes = new ArrayList<>(notes);
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longListener) {
        this.longListener = longListener;
    }

    public void setOnStartDragListener(OnStartDragListener dragStartListener) {
        this.dragStartListener = dragStartListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Note note = filteredNotes.get(position);
        holder.bind(note, listener, longListener, isSelectionMode, selectedNoteIds.contains(note.getId()));
        
        holder.binding.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return filteredNotes == null ? 0 : filteredNotes.size();
    }

    public void updateData(List<Note> newNotes) {
        this.allNotes = newNotes;
        this.filteredNotes = new ArrayList<>(newNotes);
        notifyDataSetChanged(); 
    }

    public void applyFilterAndSort(Context context, int sortMode, String filterPriority) {
        List<Note> result = new ArrayList<>(allNotes);

        if (filterPriority != null && !filterPriority.isEmpty() && !filterPriority.equals("全部")) {
            result = result.stream()
                    .filter(n -> calculateEffectiveImportance(context, n).equals(filterPriority))
                    .collect(Collectors.toList());
        }

        switch (sortMode) {
            case 0: Collections.sort(result, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp())); break;
            case 1: Collections.sort(result, (n1, n2) -> Long.compare(n1.getTimestamp(), n2.getTimestamp())); break;
            case 2: Collections.sort(result, (n1, n2) -> n1.getTitle().compareToIgnoreCase(n2.getTitle())); break;
            case 3: Collections.sort(result, (n1, n2) -> n2.getTitle().compareToIgnoreCase(n1.getTitle())); break;
            case 4: Collections.sort(result, (n1, n2) -> Integer.compare(getPriorityValue(calculateEffectiveImportance(context, n1)), getPriorityValue(calculateEffectiveImportance(context, n2)))); break;
            case 5: Collections.sort(result, (n1, n2) -> Integer.compare(getPriorityValue(calculateEffectiveImportance(context, n2)), getPriorityValue(calculateEffectiveImportance(context, n1)))); break;
        }

        this.filteredNotes = result;
        notifyDataSetChanged();
    }

    private int getPriorityValue(String importance) {
        switch (importance) {
            case "重要且紧急": return 0;
            case "重要不紧急": return 1;
            case "紧急不重要": return 2;
            case "不重要不紧急": return 3;
            default: return 4;
        }
    }

    public List<Note> getNotes() {
        return filteredNotes;
    }

    public Note getNoteAt(int position) {
        if (filteredNotes != null && position >= 0 && position < filteredNotes.size()) {
            return filteredNotes.get(position);
        }
        return null;
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) {
            selectedNoteIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(long noteId) {
        if (selectedNoteIds.contains(noteId)) {
            selectedNoteIds.remove(noteId);
        } else {
            selectedNoteIds.add(noteId);
        }
        notifyDataSetChanged();
    }

    public List<Long> getSelectedNoteIds() {
        return new ArrayList<>(selectedNoteIds);
    }

    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(filteredNotes, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static String calculateEffectiveImportance(Context context, Note note) {
        String base = note.getImportance();
        if (base == null) base = "不重要不紧急";
        
        long now = System.currentTimeMillis();
        long targetTime = getTargetReminderTime(note);

        if (targetTime == 0) return base;

        if (now > targetTime) {
            return "不重要不紧急";
        }

        long diffMillis = targetTime - now;
        int advance = note.getReminderMinutes();

        if ("重要不紧急".equals(base)) {
            if (diffMillis <= (long) advance * 60000) return "重要且紧急";
        } else if ("紧急不重要".equals(base)) {
            int upgradeMinutes = PrefUtils.getUrgentUpgradeTime(context);
            if (diffMillis <= (long) upgradeMinutes * 60000) return "重要且紧急";
        }

        return base;
    }

    /**
     * 获取笔记的提醒目标时间戳
     * 修正：优先判断特定日期（单次提醒），避免 AI 笔记误触每日提醒逻辑
     */
    private static long getTargetReminderTime(Note note) {
        // 1. 优先检查具有特定日期的提醒 (单次提醒)
        if (note.getReminderDate() != null && !note.getReminderDate().isEmpty() && note.getOnceReminderTime() != null && !note.getOnceReminderTime().isEmpty()) {
            try {
                String full = note.getReminderDate() + " " + note.getOnceReminderTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date d = sdf.parse(full);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        
        // 2. 其次检查每日提醒时间
        if (note.getReminderTime() != null && note.getReminderTime().contains(":")) {
            try {
                String[] p = note.getReminderTime().split(":");
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0].trim()));
                c.set(Calendar.MINUTE, Integer.parseInt(p[1].trim()));
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                return c.getTimeInMillis();
            } catch (Exception ignored) {}
        }
        
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ItemNoteBinding binding;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        public ViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Note note, OnItemClickListener listener, OnItemLongClickListener longListener, boolean isSelectionMode, boolean isSelected) {
            Context context = itemView.getContext();
            binding.tvTitle.setText(note.getTitle());
            binding.tvContent.setText(note.getContent());

            if (PrefUtils.isLimitContent(context)) {
                binding.tvContent.setMaxLines(1); 
                binding.tvContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
            } else {
                binding.tvContent.setMaxLines(Integer.MAX_VALUE); 
            }
            
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
            int defaultPrimary = typedValue.data;

            // 应用文字主色调
            int customTextColor = PrefUtils.getTextMainColor(context, defaultPrimary);
            binding.tvTimestamp.setTextColor(customTextColor);

            if (isSelectionMode && isSelected) {
                binding.cardView.setStrokeWidth(6); 
                binding.cardView.setStrokeColor(customTextColor); 
            } else {
                binding.cardView.setStrokeWidth(0);
            }

            String effectiveImportance = calculateEffectiveImportance(context, note);
            if (PrefUtils.isColorReminder(context)) {
                binding.viewImportanceTag.setVisibility(View.VISIBLE);
                int color;
                switch (effectiveImportance) {
                    case "重要且紧急": color = Color.parseColor("#F44336"); break;
                    case "重要不紧急": color = Color.parseColor("#2196F3"); break;
                    case "紧急不重要": color = Color.parseColor("#FFEB3B"); break;
                    case "不重要不紧急": color = Color.parseColor("#9E9E9E"); break;
                    default: color = Color.TRANSPARENT; break;
                }
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(color);
                binding.viewImportanceTag.setBackground(shape);
            } else {
                binding.viewImportanceTag.setVisibility(View.GONE);
            }

            // 更新剩余时间显示
            updateRemainingTime(note);

            boolean showTimeSetting = PrefUtils.isShowTimestamp(context);
            if (showTimeSetting && note.getTimestamp() > 0) {
                binding.tvTimestamp.setVisibility(View.VISIBLE);
                binding.tvTimestamp.setText(dateFormat.format(new Date(note.getTimestamp())));
            } else {
                binding.tvTimestamp.setVisibility(View.GONE);
            }

            if (note.isAiGenerated()) {
                binding.tvAiTag.setVisibility(View.VISIBLE);
                binding.tvAiTag.setTextColor(customTextColor);
            } else {
                binding.tvAiTag.setVisibility(View.GONE);
            }

            boolean showLoc = PrefUtils.isShowLocation(context);
            String loc = note.getLocation();
            if (showLoc && loc != null && !loc.isEmpty()) {
                binding.tvLocation.setVisibility(View.VISIBLE);
                binding.tvLocation.setText(loc);
            } else {
                binding.tvLocation.setVisibility(View.GONE);
            }

            boolean showWeather = PrefUtils.isShowWeather(context);
            String weather = note.getWeather();
            if (showWeather && weather != null && !weather.isEmpty()) {
                binding.tvWeather.setVisibility(View.VISIBLE);
                String prefix = binding.tvLocation.getVisibility() == View.VISIBLE ? " · " : "";
                binding.tvWeather.setText(prefix + weather);
            } else {
                binding.tvWeather.setVisibility(View.GONE);
            }

            binding.ivDragHandle.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            binding.getRoot().setOnClickListener(v -> {
                if (isSelectionMode) toggleSelection(note.getId());
                else listener.onItemClick(note);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (!isSelectionMode && longListener != null) {
                    longListener.onItemLongClick(note);
                    return true;
                }
                return false;
            });
        }

        private void updateRemainingTime(Note note) {
            long now = System.currentTimeMillis();
            long targetTime = getTargetReminderTime(note);
            // 修正：只有当没有具体日期时才视为每日提醒
            boolean isDaily = (note.getReminderDate() == null || note.getReminderDate().isEmpty()) 
                             && note.getReminderTime() != null && note.getReminderTime().contains(":");

            if (targetTime == 0) {
                binding.tvRemainingTime.setText("未设置提醒");
                return;
            }

            long diffMillis = targetTime - now;

            if (diffMillis < 0) {
                binding.tvRemainingTime.setText(isDaily ? "今日已过期" : "已过期");
            } else {
                long seconds = diffMillis / 1000;
                long minutes = seconds / 60;
                long hours = minutes / 60;
                long days = hours / 24;

                if (days > 0) {
                    binding.tvRemainingTime.setText(String.format(Locale.getDefault(), "剩余 %d天%d小时", days, hours % 24));
                } else if (hours > 0) {
                    binding.tvRemainingTime.setText(String.format(Locale.getDefault(), "剩余 %d小时%d分", hours, minutes % 60));
                } else {
                    binding.tvRemainingTime.setText(String.format(Locale.getDefault(), "剩余 %d分钟", minutes));
                }
            }
        }

        private void toggleSelection(long id) {
             RecyclerView rv = (RecyclerView) itemView.getParent();
             if (rv != null && rv.getAdapter() instanceof NoteAdapter) {
                 ((NoteAdapter) rv.getAdapter()).toggleSelection(id);
             }
        }
    }
}
