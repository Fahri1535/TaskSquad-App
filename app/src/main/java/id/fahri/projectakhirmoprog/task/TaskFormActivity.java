package id.fahri.projectakhirmoprog.task;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.GroupRepository;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;
import id.fahri.projectakhirmoprog.notification.ReminderScheduler;

/**
 * Form untuk membuat tugas baru atau mengedit tugas yang sudah ada.
 * Mode edit aktif jika EXTRA_TASK_ID dikirim lewat Intent.
 *
 * Catatan migrasi Firestore: groupId & taskId sekarang String.
 */
public class TaskFormActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "extra_group_id";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    private ImageView btnBack;
    private TextView tvPageTitle;
    private EditText etJudul, etDeskripsi;
    private Spinner spinnerAssignTo;
    private TextView tvDeadline;
    private TextView btnStatusBelum, btnStatusProses, btnStatusSelesai;
    private Button btnSimpan, btnBatal;

    private TaskRepository taskRepository;
    private GroupRepository groupRepository;

    private String groupId;
    private String taskId;
    private boolean isEditMode = false;
    private String currentUserId;

    private long selectedDeadline = 0L;
    private String selectedStatus = "PENDING";
    private Task existingTask;

    private final List<GroupRepository.Member> memberList = new ArrayList<>();
    private ArrayAdapter<GroupRepository.Member> assignAdapter;
    // Nama & uid anggota yang perlu dipilihkan otomatis di spinner setelah member
    // selesai dimuat (dipakai saat mode edit, karena data tugas & anggota grup
    // dimuat secara terpisah/async).
    private String pendingAssignName;
    private String pendingAssignUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_form);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";

        taskRepository = new TaskRepository();
        groupRepository = new GroupRepository();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        isEditMode = taskId != null;

        initViews();
        setupListeners();
        setupAssignSpinner();

        if (isEditMode) {
            tvPageTitle.setText("Edit tugas");
            loadExistingTask();
        } else {
            tvPageTitle.setText("Tugas baru");
            applyStatusSelectionUi();
            if (groupId != null && !groupId.isEmpty()) {
                loadGroupMembers();
            }
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvPageTitle = findViewById(R.id.tvPageTitle);
        etJudul = findViewById(R.id.etJudul);
        etDeskripsi = findViewById(R.id.etDeskripsi);
        spinnerAssignTo = findViewById(R.id.spinnerAssignTo);
        tvDeadline = findViewById(R.id.tvDeadline);
        btnStatusBelum = findViewById(R.id.btnStatusBelum);
        btnStatusProses = findViewById(R.id.btnStatusProses);
        btnStatusSelesai = findViewById(R.id.btnStatusSelesai);
        btnSimpan = findViewById(R.id.btnSimpan);
        btnBatal = findViewById(R.id.btnBatal);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnBatal.setOnClickListener(v -> finish());

        tvDeadline.setOnClickListener(v -> showDatePicker());

        btnStatusBelum.setOnClickListener(v -> selectStatus("PENDING"));
        btnStatusProses.setOnClickListener(v -> selectStatus("IN_PROGRESS"));
        btnStatusSelesai.setOnClickListener(v -> selectStatus("DONE"));

        btnSimpan.setOnClickListener(v -> saveTask());
    }

    private void setupAssignSpinner() {
        assignAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, memberList);
        assignAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerAssignTo.setAdapter(assignAdapter);
    }

    /**
     * Muat daftar anggota grup (uid + nama lengkap) dari GroupRepository lalu
     * isi ke spinner. Dipanggil setelah groupId diketahui, baik saat buat
     * tugas baru maupun setelah tugas lama selesai dimuat (mode edit).
     */
    private void loadGroupMembers() {
        groupRepository.getGroupById(groupId, new GroupRepository.OnGroupResult() {
            @Override
            public void onSuccess(Group group) {
                groupRepository.getGroupMembers(group.getMemberIds(), new GroupRepository.OnMemberListResult() {
                    @Override
                    public void onSuccess(List<GroupRepository.Member> members) {
                        memberList.clear();
                        memberList.addAll(members);
                        assignAdapter.notifyDataSetChanged();
                        if (pendingAssignUid != null || pendingAssignName != null) {
                            selectMember(pendingAssignUid, pendingAssignName);
                            pendingAssignUid = null;
                            pendingAssignName = null;
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(TaskFormActivity.this,
                                "Gagal memuat anggota grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskFormActivity.this,
                        "Gagal memuat data grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectMember(String uid, String nama) {
        // Task lama (sebelum assignedToUid ada) cuma punya nama tersimpan —
        // fallback cocokkan by nama supaya spinner tetap ke-set saat edit.
        if (uid != null && !uid.isEmpty()) {
            for (int i = 0; i < memberList.size(); i++) {
                if (memberList.get(i).uid.equals(uid)) {
                    spinnerAssignTo.setSelection(i);
                    return;
                }
            }
        }
        if (nama != null) {
            for (int i = 0; i < memberList.size(); i++) {
                if (memberList.get(i).nama.equals(nama)) {
                    spinnerAssignTo.setSelection(i);
                    return;
                }
            }
        }
    }

    private void selectStatus(String status) {
        selectedStatus = status;
        applyStatusSelectionUi();
    }

    private void applyStatusSelectionUi() {
        resetStatusButton(btnStatusBelum);
        resetStatusButton(btnStatusProses);
        resetStatusButton(btnStatusSelesai);

        TextView active;
        switch (selectedStatus) {
            case "IN_PROGRESS":
                active = btnStatusProses;
                break;
            case "DONE":
                active = btnStatusSelesai;
                break;
            case "PENDING":
            default:
                active = btnStatusBelum;
                break;
        }
        active.setBackgroundResource(R.drawable.bg_button_primary);
        active.setTextColor(getColor(R.color.text_on_accent));
    }

    private void resetStatusButton(TextView button) {
        button.setBackgroundResource(R.drawable.bg_button_ghost);
        button.setTextColor(getColor(R.color.text_secondary));
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDeadline > 0) {
            calendar.setTimeInMillis(selectedDeadline);
        }

        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    showTimePicker(picked);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }

    private void showTimePicker(Calendar dateOnly) {
        Calendar now = Calendar.getInstance();

        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    dateOnly.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    dateOnly.set(Calendar.MINUTE, minute);
                    dateOnly.set(Calendar.SECOND, 0);
                    selectedDeadline = dateOnly.getTimeInMillis();
                    updateDeadlineText();
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );
        timePicker.show();
    }

    private void updateDeadlineText() {
        String formatted = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(selectedDeadline);
        tvDeadline.setText(formatted);
        tvDeadline.setTextColor(getColor(R.color.text_primary));
    }

    private void loadExistingTask() {
        taskRepository.getTaskById(taskId, new TaskRepository.OnTaskResult() {
            @Override
            public void onSuccess(Task task) {
                existingTask = task;
                groupId = task.getGroupId();
                selectedDeadline = task.getDeadline();
                selectedStatus = task.getStatus();

                etJudul.setText(task.getJudul());
                etDeskripsi.setText(task.getDeskripsi());
                pendingAssignName = task.getNamaAnggota();
                pendingAssignUid = task.getAssignedToUid();
                if (selectedDeadline > 0) {
                    updateDeadlineText();
                }
                applyStatusSelectionUi();
                if (groupId != null && !groupId.isEmpty()) {
                    loadGroupMembers();
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskFormActivity.this,
                        "Gagal memuat tugas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveTask() {
        String judul = etJudul.getText().toString().trim();
        String deskripsi = etDeskripsi.getText().toString().trim();
        GroupRepository.Member selectedMember = (GroupRepository.Member) spinnerAssignTo.getSelectedItem();
        String assignTo = selectedMember != null ? selectedMember.nama : "";
        String assignToUid = selectedMember != null ? selectedMember.uid : "";

        if (judul.isEmpty()) {
            etJudul.setError(getString(R.string.error_judul_kosong));
            etJudul.requestFocus();
            return;
        }

        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Grup tidak valid", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSimpan.setEnabled(false);

        if (isEditMode && existingTask != null) {
            existingTask.setJudul(judul);
            existingTask.setDeskripsi(deskripsi);
            existingTask.setNamaAnggota(assignTo);
            existingTask.setAssignedToUid(assignToUid);
            existingTask.setDeadline(selectedDeadline);
            existingTask.setStatus(selectedStatus);

            taskRepository.update(existingTask, new TaskRepository.OnSimpleResult() {
                @Override
                public void onSuccess() {
                    if (selectedDeadline > 0) {
                        ReminderScheduler.scheduleReminder(getApplicationContext(), existingTask.getId(), judul, selectedDeadline);
                    } else {
                        ReminderScheduler.cancelReminder(getApplicationContext(), existingTask.getId());
                    }
                    onSaveFinished();
                }

                @Override
                public void onError(Exception e) {
                    onSaveFailed(e);
                }
            });
        } else {
            Task newTask = new Task(
                    currentUserId,
                    groupId,
                    judul,
                    deskripsi,
                    selectedDeadline,
                    selectedStatus,
                    assignTo,
                    assignToUid,
                    null,
                    null,
                    null,
                    System.currentTimeMillis()
            );

            taskRepository.insert(newTask, new TaskRepository.OnIdResult() {
                @Override
                public void onSuccess(String newTaskId) {
                    if (selectedDeadline > 0) {
                        ReminderScheduler.scheduleReminder(getApplicationContext(), newTaskId, judul, selectedDeadline);
                    }
                    onSaveFinished();
                }

                @Override
                public void onError(Exception e) {
                    onSaveFailed(e);
                }
            });
        }
    }

    private void onSaveFinished() {
        Toast.makeText(this, R.string.success_tugas_disimpan, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void onSaveFailed(Exception e) {
        btnSimpan.setEnabled(true);
        Toast.makeText(this, "Gagal menyimpan tugas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}
