package id.fahri.projectakhirmoprog.task;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.adapter.TaskAdapter;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;

/**
 * Menampilkan semua tugas dalam sebuah grup, dengan filter status
 * (Semua / Belum / Proses / Selesai) lewat chip di bagian atas.
 *
 * Catatan migrasi Firestore: groupId sekarang String, dan daftar tugas
 * diambil lewat listener realtime (listenTasksByGroup) sehingga otomatis
 * update kalau ada anggota grup lain menambah/mengubah tugas.
 */
public class TaskListActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "extra_group_id";
    public static final String EXTRA_GROUP_NAME = "extra_group_name";

    private ImageView btnBack;
    private TextView tvNamaGrup, tvInfoGrup;
    private TextView chipSemua, chipBelum, chipProses, chipSelesai;
    private RecyclerView rvTugas;
    private android.view.View emptyStateTugas;
    private android.view.View fabTambahTugas;

    private TaskAdapter taskAdapter;
    private TaskRepository taskRepository;
    private ListenerRegistration taskListener;

    private String groupId;
    private String groupName;
    private String currentUserId;

    private List<Task> allTasksCache = new ArrayList<>();
    private String activeFilter = "SEMUA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);

        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";

        taskRepository = new TaskRepository();

        initViews();
        setupRecyclerView();
        setupListeners();

        tvNamaGrup.setText(groupName != null ? groupName : "Grup");
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListeningTasks();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvNamaGrup = findViewById(R.id.tvNamaGrup);
        tvInfoGrup = findViewById(R.id.tvInfoGrup);
        chipSemua = findViewById(R.id.chipSemua);
        chipBelum = findViewById(R.id.chipBelum);
        chipProses = findViewById(R.id.chipProses);
        chipSelesai = findViewById(R.id.chipSelesai);
        rvTugas = findViewById(R.id.rvTugas);
        emptyStateTugas = findViewById(R.id.emptyStateTugas);
        fabTambahTugas = findViewById(R.id.fabTambahTugas);
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(task -> {
            Intent intent = new Intent(this, TaskDetailActivity.class);
            intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        });
        rvTugas.setLayoutManager(new LinearLayoutManager(this));
        rvTugas.setAdapter(taskAdapter);

        // Hanya satu grup di layar ini, jadi cukup map 1 entri untuk nama grup
        if (groupId != null && groupName != null) {
            Map<String, String> namesMap = new HashMap<>();
            namesMap.put(groupId, groupName);
            taskAdapter.setGroupNames(namesMap);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        fabTambahTugas.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskFormActivity.class);
            intent.putExtra(TaskFormActivity.EXTRA_GROUP_ID, groupId);
            startActivity(intent);
        });

        chipSemua.setOnClickListener(v -> applyFilter("SEMUA"));
        chipBelum.setOnClickListener(v -> applyFilter("PENDING"));
        chipProses.setOnClickListener(v -> applyFilter("IN_PROGRESS"));
        chipSelesai.setOnClickListener(v -> applyFilter("DONE"));
    }

    private void applyFilter(String filter) {
        activeFilter = filter;
        updateChipStyles();
        renderFilteredList();
    }

    private void updateChipStyles() {
        resetChip(chipSemua);
        resetChip(chipBelum);
        resetChip(chipProses);
        resetChip(chipSelesai);

        TextView active;
        switch (activeFilter) {
            case "PENDING":
                active = chipBelum;
                break;
            case "IN_PROGRESS":
                active = chipProses;
                break;
            case "DONE":
                active = chipSelesai;
                break;
            case "SEMUA":
            default:
                active = chipSemua;
                break;
        }
        active.setBackgroundResource(R.drawable.bg_button_primary);
        active.setTextColor(getColor(R.color.text_on_accent));
    }

    private void resetChip(TextView chip) {
        chip.setBackgroundResource(R.drawable.bg_button_ghost);
        chip.setTextColor(getColor(R.color.text_secondary));
    }

    private void startListeningTasks() {
        if (groupId == null) return;

        if (taskListener != null) {
            taskListener.remove();
        }

        taskListener = taskRepository.listenTasksByGroupForAssignee(groupId, currentUserId, new TaskRepository.OnTaskListResult() {
            @Override
            public void onSuccess(List<Task> tasks) {
                allTasksCache = tasks;

                int total = tasks.size();
                int selesai = 0;
                for (Task t : tasks) {
                    if ("DONE".equals(t.getStatus())) selesai++;
                }
                int percentage = total == 0 ? 0 : (int) ((selesai * 100f) / total);

                tvInfoGrup.setText(String.format(Locale.getDefault(), "%d tugas · %d%% selesai", total, percentage));
                renderFilteredList();
            }

            @Override
            public void onError(Exception e) {
                android.widget.Toast.makeText(TaskListActivity.this,
                        "Gagal memuat tugas: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderFilteredList() {
        List<Task> filtered;
        if ("SEMUA".equals(activeFilter)) {
            filtered = allTasksCache;
        } else {
            filtered = new ArrayList<>();
            for (Task t : allTasksCache) {
                if (activeFilter.equals(t.getStatus())) {
                    filtered.add(t);
                }
            }
        }

        taskAdapter.submitList(new ArrayList<>(filtered));
        emptyStateTugas.setVisibility(filtered.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        rvTugas.setVisibility(filtered.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }
}
