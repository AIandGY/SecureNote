package com.aigy.securenote;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.databinding.FragmentNotesBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.ReminderManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 笔记 Fragment
 * 增强：添加了“今日已过期 (每日事件)”和“已过期 (单次事件)”两个可折叠容器
 */
public class NotesFragment extends Fragment {

    private FragmentNotesBinding binding;
    private NoteAdapter adapter;
    private NoteAdapter expiredTodayAdapter;
    private NoteAdapter expiredOnceAdapter;
    
    private AppDatabase db;
    private ItemTouchHelper itemTouchHelper;
    private NoteInteractionListener listener;

    private boolean isExpiredTodayExpanded = false;
    private boolean isExpiredOnceExpanded = false;

    public interface NoteInteractionListener {
        void onEnterSelectionMode();
        void onExitSelectionMode();
        void onAiLongPressSetup(View view);
        void checkSecondaryGuides();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NoteInteractionListener) {
            listener = (NoteInteractionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getDatabase(requireContext());
        
        initMainList();
        initExpiredLists();
        setupCollapsibleContainers();
        
        setupItemTouchHelper();
        applyPersonalization();

        binding.write.setOnClickListener(v -> {
            if (adapter != null && adapter.isSelectionMode()) {
                deleteSelectedNotes();
            } else {
                startActivity(new Intent(requireContext(), writeActivity.class));
            }
        });

        if (listener != null) {
            listener.onAiLongPressSetup(binding.write);
        }
    }

    private void initMainList() {
        adapter = new NoteAdapter(new ArrayList<>(), note -> {
            Intent intent = new Intent(requireContext(), writeActivity.class);
            intent.putExtra("note_id", note.getId());
            startActivity(intent);
        });

        adapter.setOnStartDragListener(viewHolder -> {
            if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
        });

        adapter.setOnItemLongClickListener(note -> {
            if (!adapter.isSelectionMode()) {
                enterSelectionMode();
                adapter.toggleSelection(note.getId());
            }
        });

        binding.Note.setAdapter(adapter);

        db.noteDao().getAllNotes().observe(getViewLifecycleOwner(), notes -> {
            if (notes != null) {
                processAndSplitNotes(notes);
                if (listener != null) listener.checkSecondaryGuides();
            }
        });
    }

    private void initExpiredLists() {
        NoteAdapter.OnItemClickListener itemClick = note -> {
            Intent intent = new Intent(requireContext(), writeActivity.class);
            intent.putExtra("note_id", note.getId());
            startActivity(intent);
        };

        expiredTodayAdapter = new NoteAdapter(new ArrayList<>(), itemClick);
        expiredOnceAdapter = new NoteAdapter(new ArrayList<>(), itemClick);

        binding.rvExpiredToday.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExpiredToday.setAdapter(expiredTodayAdapter);

        binding.rvExpiredOnce.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExpiredOnce.setAdapter(expiredOnceAdapter);
    }

    private void setupCollapsibleContainers() {
        binding.layoutExpiredTodayHeader.setOnClickListener(v -> {
            isExpiredTodayExpanded = !isExpiredTodayExpanded;
            binding.rvExpiredToday.setVisibility(isExpiredTodayExpanded ? View.VISIBLE : View.GONE);
            binding.ivExpiredTodayArrow.setRotation(isExpiredTodayExpanded ? 90 : 270);
        });

        binding.layoutExpiredOnceHeader.setOnClickListener(v -> {
            isExpiredOnceExpanded = !isExpiredOnceExpanded;
            binding.rvExpiredOnce.setVisibility(isExpiredOnceExpanded ? View.VISIBLE : View.GONE);
            binding.ivExpiredOnceArrow.setRotation(isExpiredOnceExpanded ? 90 : 270);
        });
    }

    /**
     * 将所有笔记拆分为：未过期、今日已过期、已过期三部分
     */
    private void processAndSplitNotes(List<Note> allNotes) {
        List<Note> activeNotes = new ArrayList<>();
        List<Note> expiredTodayNotes = new ArrayList<>();
        List<Note> expiredOnceNotes = new ArrayList<>();

        long now = System.currentTimeMillis();
        Calendar calNow = Calendar.getInstance();
        
        for (Note note : allNotes) {
            long targetTime = getTargetReminderTime(note);
            boolean isDaily = (note.getReminderDate() == null || note.getReminderDate().isEmpty()) 
                             && note.getReminderTime() != null && note.getReminderTime().contains(":");

            if (targetTime == 0 || targetTime > now) {
                activeNotes.add(note);
            } else {
                // 已过期
                if (isDaily) {
                    expiredTodayNotes.add(note);
                } else {
                    expiredOnceNotes.add(note);
                }
            }
        }

        // 默认排序：主列表跟随设置，过期列表按时间倒序（后进排前）
        Collections.sort(expiredTodayNotes, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
        Collections.sort(expiredOnceNotes, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));

        adapter.updateData(activeNotes);
        expiredTodayAdapter.updateData(expiredTodayNotes);
        expiredOnceAdapter.updateData(expiredOnceNotes);

        // 联动显示逻辑
        binding.textEmptyHint.setVisibility(activeNotes.isEmpty() ? View.VISIBLE : View.GONE);
        binding.cardExpiredToday.setVisibility(expiredTodayNotes.isEmpty() ? View.GONE : View.VISIBLE);
        binding.cardExpiredOnce.setVisibility(expiredOnceNotes.isEmpty() ? View.GONE : View.VISIBLE);

        if (getActivity() instanceof MainActivity) {
            MainActivity main = (MainActivity) getActivity();
            adapter.applyFilterAndSort(requireContext(), main.getCurrentSortMode(), main.getCurrentPriorityFilter());
        }
    }

    private long getTargetReminderTime(Note note) {
        if (note.getReminderDate() != null && !note.getReminderDate().isEmpty() && note.getOnceReminderTime() != null && !note.getOnceReminderTime().isEmpty()) {
            try {
                String full = note.getReminderDate() + " " + note.getOnceReminderTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date d = sdf.parse(full);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
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

    public void applyFilterAndSort(int sortMode, String priorityFilter) {
        if (adapter != null) adapter.applyFilterAndSort(requireContext(), sortMode, priorityFilter);
    }

    public void applyPersonalization() {
        if (binding == null) return;
        int listMode = PrefUtils.getListMode(requireContext());
        RecyclerView.LayoutManager layoutManager;
        if (listMode == 1) layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        else layoutManager = new LinearLayoutManager(requireContext());
        
        binding.Note.setLayoutManager(layoutManager);
        
        // 过期列表通常使用线性布局更整齐
        binding.rvExpiredToday.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExpiredOnce.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        binding.write.setImageResource(android.R.drawable.ic_menu_delete);
        if (listener != null) listener.onEnterSelectionMode();
    }

    public void exitSelectionMode() {
        adapter.setSelectionMode(false);
        binding.write.setImageResource(R.drawable.icons_write);
        if (listener != null) listener.onExitSelectionMode();
    }

    public boolean isSelectionMode() { return adapter != null && adapter.isSelectionMode(); }
    public NoteAdapter getAdapter() { return adapter; }

    private void deleteSelectedNotes() {
        List<Long> selectedIds = adapter.getSelectedNoteIds();
        if (selectedIds.isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("批量删除")
                .setMessage("确定删除选中的 " + selectedIds.size() + " 条笔记吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    new Thread(() -> {
                        for (Long id : selectedIds) {
                            db.noteDao().deleteById(id);
                            ReminderManager.cancelReminder(requireContext(), id);
                        }
                        requireActivity().runOnUiThread(this::exitSelectionMode);
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = vh.getAbsoluteAdapterPosition();
                int toPos = target.getAbsoluteAdapterPosition();
                if (adapter != null) {
                    adapter.onItemMove(fromPos, toPos);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAbsoluteAdapterPosition();
                Note note = adapter.getNoteAt(pos);
                if (note != null) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("删除笔记")
                            .setMessage("确定删除吗？")
                            .setPositiveButton("删除", (d, w) -> performDelete(note))
                            .setNegativeButton("取消", (d, w) -> adapter.notifyItemChanged(pos))
                            .show();
                }
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                // 拖动时增加半透明效果，提升交互感，与密码本页面保持一致
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.7f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 停止拖动后恢复不透明度
                viewHolder.itemView.setAlpha(1.0f);
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.Note);
    }

    private void performDelete(Note note) {
        new Thread(() -> {
            db.noteDao().delete(note);
            ReminderManager.cancelReminder(requireContext(), note.getId());
        }).start();
    }

    public View getWriteButton() { return binding.write; }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
