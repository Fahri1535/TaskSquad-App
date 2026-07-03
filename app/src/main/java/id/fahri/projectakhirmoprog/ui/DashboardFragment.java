package id.fahri.projectakhirmoprog.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.adapter.TaskAdapter;
import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.GroupRepository;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;
import id.fahri.projectakhirmoprog.task.TaskDetailActivity;

/**
 * Tab Beranda: menampilkan statistik progress keseluruhan (total, selesai,
 * mendatang) dan daftar 5 tugas terbaru dari SEMUA grup yang diikuti user
 * yang sedang login (bukan cuma tugas yang dia buat sendiri).
 *
 * Catatan migrasi Firestore: alurnya dua tahap —
 * 1) listen daftar grup yang diikuti user (memberIds contains uid)
 * 2) listen semua tugas dari grup-grup itu (whereIn groupId)
 * Setiap kali daftar grup berubah, listener tugas di-restart dengan daftar
 * groupId terbaru.
 */
public class DashboardFragment extends Fragment {

    private TextView tvGreeting, tvUserName, tvAvatarInitial;
    private TextView tvStatTotal, tvStatSelesai, tvStatMendatang;
    private TextView tvProgressPercent;
    private ProgressBar progressBar;
    private RecyclerView rvRecentTasks;
    private View emptyState;

    private TaskAdapter taskAdapter;
    private GroupRepository groupRepository;
    private TaskRepository taskRepository;
    private String currentUserId;

    private ListenerRegistration groupListener;
    private ListenerRegistration taskListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupRepository = new GroupRepository();
        taskRepository = new TaskRepository();

        initViews(view);
        setupHeader();
        setupRecyclerView();
    }

    @Override
    public void onStart() {
        super.onStart();
        startListeningGroups();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (groupListener != null) {
            groupListener.remove();
            groupListener = null;
        }
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    private void initViews(View view) {
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvAvatarInitial = view.findViewById(R.id.tvAvatarInitial);
        tvStatTotal = view.findViewById(R.id.tvStatTotal);
        tvStatSelesai = view.findViewById(R.id.tvStatSelesai);
        tvStatMendatang = view.findViewById(R.id.tvStatMendatang);
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent);
        progressBar = view.findViewById(R.id.progressBar);
        rvRecentTasks = view.findViewById(R.id.rvRecentTasks);
        emptyState = view.findViewById(R.id.emptyState);
    }

    private void setupHeader() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";

        String displayName = (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty())
                ? user.getDisplayName()
                : "Pengguna";

        tvUserName.setText(displayName);
        tvAvatarInitial.setText(displayName.substring(0, 1).toUpperCase(Locale.getDefault()));
        tvGreeting.setText(getGreetingByTime());
    }

    private String getGreetingByTime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 11) return "Selamat pagi,";
        if (hour < 15) return "Selamat siang,";
        if (hour < 18) return "Selamat sore,";
        return "Selamat malam,";
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(task -> {
            if (getContext() == null) return;
            android.content.Intent intent = new android.content.Intent(getContext(), TaskDetailActivity.class);
            intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        });
        rvRecentTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentTasks.setAdapter(taskAdapter);
    }

    private void startListeningGroups() {
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) return;

        if (groupListener != null) {
            groupListener.remove();
        }

        groupListener = groupRepository.listenGroups(currentUserId, new GroupRepository.OnGroupListResult() {
            @Override
            public void onSuccess(List<Group> groups) {
                Map<String, String> groupNames = new HashMap<>();
                List<String> groupIds = new ArrayList<>();
                for (Group g : groups) {
                    groupIds.add(g.getId());
                    groupNames.put(g.getId(), g.getNamaGrup());
                }
                taskAdapter.setGroupNames(groupNames);
                startListeningTasks(groupIds);
            }

            @Override
            public void onError(Exception e) {
                // Gagal memuat grup — biarkan dashboard tetap kosong, tidak fatal
            }
        });
    }

    private void startListeningTasks(List<String> groupIds) {
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }

        if (groupIds.isEmpty()) {
            renderStats(new ArrayList<>());
            return;
        }

        taskListener = taskRepository.listenTasksByGroupIdsForAssignee(groupIds, currentUserId, new TaskRepository.OnTaskListResult() {
            @Override
            public void onSuccess(List<Task> tasks) {
                renderStats(tasks);
            }

            @Override
            public void onError(Exception e) {
                // Gagal memuat tugas — biarkan dashboard tetap menampilkan data terakhir
            }
        });
    }

    private void renderStats(List<Task> tasks) {
        if (getContext() == null) return;

        int total = tasks.size();
        int selesai = 0;
        int mendatang = 0;
        long now = System.currentTimeMillis();

        for (Task t : tasks) {
            if ("DONE".equals(t.getStatus())) {
                selesai++;
            } else if (t.getDeadline() > now) {
                mendatang++;
            }
        }

        int percentage = total == 0 ? 0 : (int) ((selesai * 100f) / total);
        List<Task> recent = new ArrayList<>(tasks.subList(0, Math.min(5, tasks.size())));

        tvStatTotal.setText(String.valueOf(total));
        tvStatSelesai.setText(String.valueOf(selesai));
        tvStatMendatang.setText(String.valueOf(mendatang));
        tvProgressPercent.setText(percentage + "%");
        progressBar.setProgress(percentage);

        taskAdapter.submitList(recent);
        emptyState.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
        rvRecentTasks.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
