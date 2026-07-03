package id.fahri.projectakhirmoprog.task;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.GroupRepository;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;
import id.fahri.projectakhirmoprog.notification.ReminderScheduler;
import id.fahri.projectakhirmoprog.utils.DownloadUtils;
import id.fahri.projectakhirmoprog.utils.FileUtils;
import id.fahri.projectakhirmoprog.utils.ImageUtils;

/**
 * Menampilkan detail lengkap sebuah tugas: deskripsi, assignee, deadline,
 * status, dan bukti progress berupa foto (kamera) atau file dokumen
 * (Word/PDF/Excel/dll, dipilih lewat file picker sistem). Mendukung
 * mengubah status tugas (siklus Belum -> Proses -> Selesai).
 *
 * Catatan migrasi Firestore: taskId sekarang String. Bukti progress
 * (foto/file) tetap disimpan lokal per-device seperti sebelumnya.
 */
public class TaskDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";

    private ImageView btnBack, btnEdit;
    private TextView tvBadgeGrup, tvJudulDetail, tvDeskripsiDetail;
    private TextView tvAssignedDetail, tvDeadlineDetail, tvStatusDetail;
    private android.view.View statusDot;
    private ImageView ivBuktiFoto;
    private android.view.View placeholderUpload;
    private android.view.View cardBuktiFile;
    private ImageView ivFileIcon;
    private TextView tvFileName, btnLihatFile;
    private ImageView btnDownloadBukti;
    private Button btnUploadFoto, btnUploadFile, btnUbahStatus;

    private TaskRepository taskRepository;
    private GroupRepository groupRepository;

    private String taskId;
    private Task currentTask;
    private File pendingPhotoFile;

    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);

        taskRepository = new TaskRepository();
        groupRepository = new GroupRepository();

        initViews();
        registerLaunchers();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTask();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEdit = findViewById(R.id.btnEdit);
        tvBadgeGrup = findViewById(R.id.tvBadgeGrup);
        tvJudulDetail = findViewById(R.id.tvJudulDetail);
        tvDeskripsiDetail = findViewById(R.id.tvDeskripsiDetail);
        tvAssignedDetail = findViewById(R.id.tvAssignedDetail);
        tvDeadlineDetail = findViewById(R.id.tvDeadlineDetail);
        tvStatusDetail = findViewById(R.id.tvStatusDetail);
        statusDot = findViewById(R.id.statusDot);
        ivBuktiFoto = findViewById(R.id.ivBuktiFoto);
        placeholderUpload = findViewById(R.id.placeholderUpload);
        cardBuktiFile = findViewById(R.id.cardBuktiFile);
        ivFileIcon = findViewById(R.id.ivFileIcon);
        tvFileName = findViewById(R.id.tvFileName);
        btnLihatFile = findViewById(R.id.btnLihatFile);
        btnDownloadBukti = findViewById(R.id.btnDownloadBukti);
        btnUploadFoto = findViewById(R.id.btnUploadFoto);
        btnUploadFile = findViewById(R.id.btnUploadFile);
        btnUbahStatus = findViewById(R.id.btnUbahStatus);
    }

    private void registerLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingPhotoFile != null) {
                        savePhotoPath(pendingPhotoFile.getAbsolutePath());
                    } else {
                        Toast.makeText(this, "Pengambilan foto dibatalkan", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto bukti", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handlePickedFile(uri);
                    } else {
                        Toast.makeText(this, "Pemilihan file dibatalkan", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEdit.setOnClickListener(v -> {
            if (currentTask == null) return;
            Intent intent = new Intent(this, TaskFormActivity.class);
            intent.putExtra(TaskFormActivity.EXTRA_TASK_ID, currentTask.getId());
            startActivity(intent);
        });

        btnUploadFoto.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        btnUploadFile.setOnClickListener(v -> launchFilePicker());

        btnLihatFile.setOnClickListener(v -> {
            if (currentTask != null) {
                FileUtils.openFile(this, currentTask.getFilePath(), currentTask.getFileName());
            }
        });

        btnDownloadBukti.setOnClickListener(v -> downloadBuktiProgress());

        btnUbahStatus.setOnClickListener(v -> cycleStatus());
    }

    private void loadTask() {
        if (taskId == null) return;

        taskRepository.getTaskById(taskId, new TaskRepository.OnTaskResult() {
            @Override
            public void onSuccess(Task task) {
                currentTask = task;
                bindTaskToUi();
                loadGroupName(task.getGroupId());
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskDetailActivity.this,
                        "Gagal memuat tugas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadGroupName(String groupId) {
        if (groupId == null) return;
        groupRepository.getGroupById(groupId, new GroupRepository.OnGroupResult() {
            @Override
            public void onSuccess(Group group) {
                tvBadgeGrup.setText(group.getNamaGrup());
            }

            @Override
            public void onError(Exception e) {
                tvBadgeGrup.setText("Grup");
            }
        });
    }

    private void bindTaskToUi() {
        if (currentTask == null) return;

        tvJudulDetail.setText(currentTask.getJudul());

        String deskripsi = currentTask.getDeskripsi();
        tvDeskripsiDetail.setText((deskripsi == null || deskripsi.trim().isEmpty()) ? "Tidak ada deskripsi" : deskripsi);

        String assigned = currentTask.getNamaAnggota();
        tvAssignedDetail.setText((assigned == null || assigned.trim().isEmpty()) ? "Belum diassign" : assigned);

        if (currentTask.getDeadline() > 0) {
            tvDeadlineDetail.setText(DateFormat.format("dd MMM yyyy, HH:mm", currentTask.getDeadline()));
        } else {
            tvDeadlineDetail.setText("Tidak ada deadline");
        }

        applyStatusUi(currentTask.getStatus());

        String fotoPath = currentTask.getFotoPath();
        String filePath = currentTask.getFilePath();
        boolean hasFoto = fotoPath != null && ImageUtils.isImageExists(fotoPath);
        boolean hasFile = filePath != null && FileUtils.isFileExists(filePath);

        if (hasFoto) {
            ivBuktiFoto.setVisibility(android.view.View.VISIBLE);
            cardBuktiFile.setVisibility(android.view.View.GONE);
            placeholderUpload.setVisibility(android.view.View.GONE);
            btnDownloadBukti.setVisibility(android.view.View.VISIBLE);
            Glide.with(this).load(new File(fotoPath)).centerCrop().into(ivBuktiFoto);
        } else if (hasFile) {
            ivBuktiFoto.setVisibility(android.view.View.GONE);
            cardBuktiFile.setVisibility(android.view.View.VISIBLE);
            placeholderUpload.setVisibility(android.view.View.GONE);
            btnDownloadBukti.setVisibility(android.view.View.VISIBLE);
            String fileName = currentTask.getFileName();
            tvFileName.setText(fileName != null ? fileName : "Dokumen");
            ivFileIcon.setImageResource(FileUtils.getIconForExtension(FileUtils.getExtension(fileName)));
        } else {
            ivBuktiFoto.setVisibility(android.view.View.GONE);
            cardBuktiFile.setVisibility(android.view.View.GONE);
            placeholderUpload.setVisibility(android.view.View.VISIBLE);
            btnDownloadBukti.setVisibility(android.view.View.GONE);
        }
    }

    private void applyStatusUi(String status) {
        if (status == null) status = "PENDING";

        int dotDrawable;
        int textColor;
        String label;
        String buttonLabel;

        switch (status) {
            case "IN_PROGRESS":
                dotDrawable = R.drawable.bar_status_progress;
                textColor = getColor(R.color.status_progress);
                label = getString(R.string.status_proses);
                buttonLabel = "Tandai selesai";
                break;
            case "DONE":
                dotDrawable = R.drawable.bar_status_done;
                textColor = getColor(R.color.status_done);
                label = getString(R.string.status_selesai);
                buttonLabel = "Kembalikan ke proses";
                break;
            case "OVERDUE":
                dotDrawable = R.drawable.bar_status_overdue;
                textColor = getColor(R.color.status_overdue);
                label = getString(R.string.status_terlambat);
                buttonLabel = "Tandai selesai";
                break;
            case "PENDING":
            default:
                dotDrawable = R.drawable.bar_status_pending;
                textColor = getColor(R.color.status_pending);
                label = getString(R.string.status_belum);
                buttonLabel = "Mulai kerjakan";
                break;
        }

        statusDot.setBackgroundResource(dotDrawable);
        tvStatusDetail.setText(label);
        tvStatusDetail.setTextColor(textColor);
        btnUbahStatus.setText(buttonLabel);
    }

    /**
     * Siklus status sederhana: PENDING -> IN_PROGRESS -> DONE -> PENDING.
     * Reminder otomatis dibatalkan begitu tugas ditandai selesai.
     */
    private void cycleStatus() {
        if (currentTask == null) return;

        String nextStatus;
        switch (currentTask.getStatus()) {
            case "PENDING":
                nextStatus = "IN_PROGRESS";
                break;
            case "IN_PROGRESS":
            case "OVERDUE":
                nextStatus = "DONE";
                break;
            case "DONE":
            default:
                nextStatus = "PENDING";
                break;
        }

        currentTask.setStatus(nextStatus);
        applyStatusUi(nextStatus);

        taskRepository.update(currentTask, new TaskRepository.OnSimpleResult() {
            @Override
            public void onSuccess() {
                if ("DONE".equals(currentTask.getStatus())) {
                    ReminderScheduler.cancelReminder(getApplicationContext(), currentTask.getId());
                } else if (currentTask.getDeadline() > 0) {
                    ReminderScheduler.scheduleReminder(getApplicationContext(), currentTask.getId(),
                            currentTask.getJudul(), currentTask.getDeadline());
                }
                Toast.makeText(TaskDetailActivity.this, R.string.success_status_diubah, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskDetailActivity.this,
                        "Gagal mengubah status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Simpan salinan bukti progress (foto atau file dokumen, mana yang sedang
     * aktif) ke folder Download publik, supaya bisa diakses dari luar aplikasi.
     */
    private void downloadBuktiProgress() {
        if (currentTask == null) return;

        String fotoPath = currentTask.getFotoPath();
        String filePath = currentTask.getFilePath();

        if (fotoPath != null && ImageUtils.isImageExists(fotoPath)) {
            String namaFoto = new File(fotoPath).getName();
            DownloadUtils.saveToDownloads(this, fotoPath, namaFoto);
        } else if (filePath != null && FileUtils.isFileExists(filePath)) {
            String namaFile = currentTask.getFileName();
            DownloadUtils.saveToDownloads(this, filePath, namaFile);
        } else {
            Toast.makeText(this, "Belum ada bukti untuk diunduh", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchFilePicker() {
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain"
        };
        filePickerLauncher.launch(mimeTypes);
    }

    private void handlePickedFile(Uri uri) {
        String originalName = FileUtils.getFileNameFromUri(this, uri);
        try {
            File copiedFile = FileUtils.copyToInternalStorage(this, uri, originalName);
            saveFilePath(copiedFile.getAbsolutePath(), originalName);
        } catch (IOException e) {
            Toast.makeText(this, "Gagal menyalin file", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFilePath(String path, String fileName) {
        if (currentTask == null) return;

        // Hapus file dokumen lama & foto lama supaya tidak menumpuk file yang tidak terpakai
        // (bukti progress hanya menampilkan salah satu: foto ATAU file terbaru)
        String oldFilePath = currentTask.getFilePath();
        if (oldFilePath != null) {
            FileUtils.deleteFile(oldFilePath);
        }
        String oldFotoPath = currentTask.getFotoPath();
        if (oldFotoPath != null) {
            ImageUtils.deleteImage(oldFotoPath);
            currentTask.setFotoPath(null);
        }

        currentTask.setFilePath(path);
        currentTask.setFileName(fileName);

        taskRepository.update(currentTask, new TaskRepository.OnSimpleResult() {
            @Override
            public void onSuccess() {
                Toast.makeText(TaskDetailActivity.this, "File bukti berhasil disimpan", Toast.LENGTH_SHORT).show();
                bindTaskToUi();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskDetailActivity.this,
                        "Gagal menyimpan file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkCameraPermissionAndLaunch() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (granted) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            pendingPhotoFile = ImageUtils.createImageFile(this);
            Uri photoUri = ImageUtils.getUriForFile(this, pendingPhotoFile);
            cameraLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "Gagal menyiapkan file foto", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePhotoPath(String path) {
        if (currentTask == null) return;

        // Hapus foto lama & file dokumen lama supaya tidak menumpuk file yang tidak terpakai
        // (bukti progress hanya menampilkan salah satu: foto ATAU file terbaru)
        String oldPath = currentTask.getFotoPath();
        if (oldPath != null) {
            ImageUtils.deleteImage(oldPath);
        }
        String oldFilePath = currentTask.getFilePath();
        if (oldFilePath != null) {
            FileUtils.deleteFile(oldFilePath);
            currentTask.setFilePath(null);
            currentTask.setFileName(null);
        }

        currentTask.setFotoPath(path);

        taskRepository.update(currentTask, new TaskRepository.OnSimpleResult() {
            @Override
            public void onSuccess() {
                Toast.makeText(TaskDetailActivity.this, R.string.success_foto_disimpan, Toast.LENGTH_SHORT).show();
                bindTaskToUi();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TaskDetailActivity.this,
                        "Gagal menyimpan foto: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
