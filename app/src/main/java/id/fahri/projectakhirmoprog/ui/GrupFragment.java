package id.fahri.projectakhirmoprog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Locale;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.adapter.GroupAdapter;
import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.GroupRepository;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;
import id.fahri.projectakhirmoprog.task.TaskListActivity;

/**
 * Tab Grup: menampilkan semua grup yang diikuti user (sebagai owner maupun
 * anggota), dengan progress ringkas per grup. FAB "+" membuat grup baru,
 * FAB kedua membuka dialog "Gabung grup" lewat kode undangan. Long-press
 * pada sebuah grup membuka dialog "Tambah anggota lewat email" (hanya
 * berguna untuk owner, tapi tidak dibatasi UI-nya di sini).
 *
 * Catatan migrasi Firestore: semua data sekarang realtime lewat
 * addSnapshotListener via GroupRepository/TaskRepository, bukan lagi
 * query Room sekali jalan.
 */
public class GrupFragment extends Fragment {

    private RecyclerView rvGrup;
    private View emptyStateGrup;
    private View fabTambahGrup;
    private View fabGabungGrup;
    private TextView tvSubtitleGrup;

    private GroupAdapter groupAdapter;
    private GroupRepository groupRepository;
    private TaskRepository taskRepository;
    private String currentUserId;

    private ListenerRegistration groupListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";

        groupRepository = new GroupRepository();
        taskRepository = new TaskRepository();

        initViews(view);
        setupRecyclerView();
        fabTambahGrup.setOnClickListener(v -> showCreateGroupDialog());
        fabGabungGrup.setOnClickListener(v -> showJoinGroupDialog());
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
    }

    private void initViews(View view) {
        rvGrup = view.findViewById(R.id.rvGrup);
        emptyStateGrup = view.findViewById(R.id.emptyStateGrup);
        fabTambahGrup = view.findViewById(R.id.fabTambahGrup);
        fabGabungGrup = view.findViewById(R.id.fabGabungGrup);
        tvSubtitleGrup = view.findViewById(R.id.tvSubtitleGrup);
    }

    private void setupRecyclerView() {
        groupAdapter = new GroupAdapter(
                group -> {
                    if (getContext() == null) return;
                    Intent intent = new Intent(getContext(), TaskListActivity.class);
                    intent.putExtra(TaskListActivity.EXTRA_GROUP_ID, group.getId());
                    intent.putExtra(TaskListActivity.EXTRA_GROUP_NAME, group.getNamaGrup());
                    startActivity(intent);
                },
                group -> showGroupOptionsDialog(group)
        );
        rvGrup.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGrup.setAdapter(groupAdapter);
    }

    private void startListeningGroups() {
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) return;

        if (groupListener != null) {
            groupListener.remove();
        }

        groupListener = groupRepository.listenGroups(currentUserId, new GroupRepository.OnGroupListResult() {
            @Override
            public void onSuccess(List<Group> groups) {
                if (getActivity() == null) return;

                groupAdapter.submitList(groups);
                tvSubtitleGrup.setText(String.format(Locale.getDefault(), "%d grup aktif", groups.size()));
                emptyStateGrup.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
                rvGrup.setVisibility(groups.isEmpty() ? View.GONE : View.VISIBLE);

                // Muat statistik progress tiap grup secara terpisah
                for (Group g : groups) {
                    loadGroupStat(g.getId());
                }
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Gagal memuat grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadGroupStat(String groupId) {
        taskRepository.getTasksByGroupSync(groupId, new TaskRepository.OnTaskListResult() {
            @Override
            public void onSuccess(List<Task> tasks) {
                int total = tasks.size();
                int selesai = 0;
                for (Task t : tasks) {
                    if ("DONE".equals(t.getStatus())) selesai++;
                }
                if (getActivity() == null) return;
                groupAdapter.setGroupStats(groupId, total, selesai);
            }

            @Override
            public void onError(Exception e) {
                // Statistik gagal dimuat untuk 1 grup — tidak fatal, biarkan tampil 0
            }
        });
    }

    private void showCreateGroupDialog() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_group, null);
        dialog.setContentView(dialogView);

        EditText etNamaGrup = dialogView.findViewById(R.id.etNamaGrup);
        View btnBuatGrup = dialogView.findViewById(R.id.btnBuatGrup);

        View swatchGreen = dialogView.findViewById(R.id.swatchGreen);
        View swatchAmber = dialogView.findViewById(R.id.swatchAmber);
        View swatchRed = dialogView.findViewById(R.id.swatchRed);
        View swatchGray = dialogView.findViewById(R.id.swatchGray);

        // Map tiap FrameLayout swatch ke kode warna yang disimpan di Firestore.
        java.util.Map<View, String> swatchToWarna = new java.util.LinkedHashMap<>();
        swatchToWarna.put(swatchGreen, "green");
        swatchToWarna.put(swatchAmber, "amber");
        swatchToWarna.put(swatchRed, "red");
        swatchToWarna.put(swatchGray, "gray");

        final String[] selectedWarna = {"green"};
        swatchGreen.setBackgroundResource(R.drawable.bg_color_swatch_selected);

        View.OnClickListener swatchClickListener = v -> {
            selectedWarna[0] = swatchToWarna.get(v);
            for (View swatch : swatchToWarna.keySet()) {
                swatch.setBackgroundResource(
                        swatch == v ? R.drawable.bg_color_swatch_selected : 0);
            }
        };
        for (View swatch : swatchToWarna.keySet()) {
            swatch.setOnClickListener(swatchClickListener);
        }

        btnBuatGrup.setOnClickListener(v -> {
            String nama = etNamaGrup.getText().toString().trim();
            if (nama.isEmpty()) {
                etNamaGrup.setError(getString(R.string.error_judul_kosong));
                etNamaGrup.requestFocus();
                return;
            }
            createGroup(nama, selectedWarna[0]);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createGroup(String nama, String warna) {
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) return;

        groupRepository.createGroup(currentUserId, nama, null, warna, new GroupRepository.OnGroupResult() {
            @Override
            public void onSuccess(Group group) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), R.string.success_grup_dibuat, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Gagal membuat grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showJoinGroupDialog() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_join_group, null);
        dialog.setContentView(dialogView);

        EditText etKodeGrup = dialogView.findViewById(R.id.etKodeGrup);
        View btnGabungGrup = dialogView.findViewById(R.id.btnGabungGrup);

        btnGabungGrup.setOnClickListener(v -> {
            String kode = etKodeGrup.getText().toString().trim();
            if (kode.isEmpty()) {
                etKodeGrup.setError("Kode grup tidak boleh kosong");
                etKodeGrup.requestFocus();
                return;
            }
            joinGroup(kode);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void joinGroup(String kode) {
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) return;

        groupRepository.joinGroupByCode(kode, currentUserId, new GroupRepository.OnGroupResult() {
            @Override
            public void onSuccess(Group group) {
                if (getContext() == null) return;
                Toast.makeText(getContext(),
                        "Berhasil gabung ke grup \"" + group.getNamaGrup() + "\"",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Gagal gabung grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showGroupOptionsDialog(Group group) {
        if (getContext() == null) return;

        boolean isOwner = currentUserId != null && currentUserId.equals(group.getOwnerId());

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_group_options, null);
        dialog.setContentView(dialogView);

        TextView tvOpsiNamaGrup = dialogView.findViewById(R.id.tvOpsiNamaGrup);
        View optTambahAnggota = dialogView.findViewById(R.id.optTambahAnggota);
        View optHapusGrup = dialogView.findViewById(R.id.optHapusGrup);

        tvOpsiNamaGrup.setText(group.getNamaGrup());

        // Hapus grup hanya boleh oleh owner — anggota biasa tidak melihat opsi ini.
        optHapusGrup.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        optTambahAnggota.setOnClickListener(v -> {
            dialog.dismiss();
            showAddMemberDialog(group);
        });

        optHapusGrup.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteGroup(group);
        });

        dialog.show();
    }

    private void confirmDeleteGroup(Group group) {
        if (getContext() == null) return;

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Hapus grup?")
                .setMessage("Grup \"" + group.getNamaGrup() + "\" beserta semua tugas di dalamnya akan "
                        + "dihapus permanen untuk semua anggota. Tindakan ini tidak bisa dibatalkan.")
                .setPositiveButton("Hapus", (d, which) -> deleteGroup(group))
                .setNegativeButton(getString(R.string.btn_batal), null)
                .show();
    }

    private void deleteGroup(Group group) {
        if (getContext() == null) return;

        // Hapus semua task milik grup ini dulu, baru grupnya sendiri —
        // supaya tidak ada task "yatim" yang groupId-nya mengarah ke
        // dokumen yang sudah tidak ada (dan tidak lagi lolos rules Firestore).
        taskRepository.deleteTasksByGroupId(group.getId(), new TaskRepository.OnSimpleResult() {
            @Override
            public void onSuccess() {
                groupRepository.deleteGroup(group.getId(), new GroupRepository.OnSimpleResult() {
                    @Override
                    public void onSuccess() {
                        if (getContext() == null) return;
                        Toast.makeText(getContext(), "Grup berhasil dihapus", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getContext() == null) return;
                        Toast.makeText(getContext(), "Gagal menghapus grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Gagal menghapus tugas grup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddMemberDialog(Group group) {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_member, null);
        dialog.setContentView(dialogView);

        TextView tvKodeGrupInfo = dialogView.findViewById(R.id.tvKodeGrupInfo);
        EditText etEmailAnggota = dialogView.findViewById(R.id.etEmailAnggota);
        View btnTambahAnggota = dialogView.findViewById(R.id.btnTambahAnggota);

        tvKodeGrupInfo.setText(String.format(Locale.getDefault(),
                "Kode grup: %s (bisa dibagikan manual juga)", group.getJoinCode()));

        btnTambahAnggota.setOnClickListener(v -> {
            String email = etEmailAnggota.getText().toString().trim();
            if (email.isEmpty()) {
                etEmailAnggota.setError(getString(R.string.error_email_kosong));
                etEmailAnggota.requestFocus();
                return;
            }
            addMember(group.getId(), email);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addMember(String groupId, String email) {
        if (getContext() == null) return;

        groupRepository.addMemberByEmail(groupId, email, new GroupRepository.OnSimpleResult() {
            @Override
            public void onSuccess() {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Anggota berhasil ditambahkan", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Gagal menambah anggota: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
