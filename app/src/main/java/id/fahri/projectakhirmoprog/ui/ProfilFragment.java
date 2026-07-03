package id.fahri.projectakhirmoprog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.auth.LoginActivity;
import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;
import id.fahri.projectakhirmoprog.data.repository.GroupRepository;
import id.fahri.projectakhirmoprog.data.repository.TaskRepository;

/**
 * Tab Profil: menampilkan info akun (nama, email), statistik ringkas
 * (total tugas, jumlah grup, tugas selesai), dan tombol logout.
 *
 * Catatan migrasi Firestore: statistik sekarang dihitung dari SEMUA grup
 * yang diikuti user (bukan cuma yang dia buat), lewat listener realtime
 * yang sama seperti DashboardFragment.
 */
public class ProfilFragment extends Fragment {

    private TextView tvAvatarBig, tvNamaProfil, tvEmailProfil;
    private TextView tvStatTotalProfil, tvStatGrupProfil, tvStatSelesaiProfil;
    private View btnLogout, btnPengaturan;

    private GroupRepository groupRepository;
    private TaskRepository taskRepository;
    private String currentUserId;

    private ListenerRegistration groupListener;
    private ListenerRegistration taskListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profil, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupRepository = new GroupRepository();
        taskRepository = new TaskRepository();

        initViews(view);
        setupProfileInfo();
        btnLogout.setOnClickListener(v -> confirmLogout());
        btnPengaturan.setOnClickListener(v ->
                startActivity(new Intent(getContext(), id.fahri.projectakhirmoprog.SettingsActivity.class)));
    }

    @Override
    public void onStart() {
        super.onStart();
        startListeningStats();
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
        tvAvatarBig = view.findViewById(R.id.tvAvatarBig);
        tvNamaProfil = view.findViewById(R.id.tvNamaProfil);
        tvEmailProfil = view.findViewById(R.id.tvEmailProfil);
        tvStatTotalProfil = view.findViewById(R.id.tvStatTotalProfil);
        tvStatGrupProfil = view.findViewById(R.id.tvStatGrupProfil);
        tvStatSelesaiProfil = view.findViewById(R.id.tvStatSelesaiProfil);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnPengaturan = view.findViewById(R.id.btnPengaturan);
    }

    private void setupProfileInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";

        String displayName = (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty())
                ? user.getDisplayName()
                : "Pengguna";
        String email = user != null && user.getEmail() != null ? user.getEmail() : "-";

        tvNamaProfil.setText(displayName);
        tvEmailProfil.setText(email);
        tvAvatarBig.setText(displayName.substring(0, 1).toUpperCase(Locale.getDefault()));
    }

    private void startListeningStats() {
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) return;

        if (groupListener != null) {
            groupListener.remove();
        }

        groupListener = groupRepository.listenGroups(currentUserId, new GroupRepository.OnGroupListResult() {
            @Override
            public void onSuccess(List<Group> groups) {
                if (getActivity() == null) return;
                tvStatGrupProfil.setText(String.valueOf(groups.size()));

                List<String> groupIds = new ArrayList<>();
                for (Group g : groups) {
                    groupIds.add(g.getId());
                }
                startListeningTasks(groupIds);
            }

            @Override
            public void onError(Exception e) {
                // Gagal memuat grup — biarkan statistik lama tetap tampil
            }
        });
    }

    private void startListeningTasks(List<String> groupIds) {
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }

        if (groupIds.isEmpty()) {
            if (getActivity() == null) return;
            tvStatTotalProfil.setText("0");
            tvStatSelesaiProfil.setText("0");
            return;
        }

        taskListener = taskRepository.listenTasksByGroupIdsForAssignee(groupIds, currentUserId, new TaskRepository.OnTaskListResult() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (getActivity() == null) return;

                int total = tasks.size();
                int selesai = 0;
                for (Task t : tasks) {
                    if ("DONE".equals(t.getStatus())) selesai++;
                }

                tvStatTotalProfil.setText(String.valueOf(total));
                tvStatSelesaiProfil.setText(String.valueOf(selesai));
            }

            @Override
            public void onError(Exception e) {
                // Gagal memuat tugas — biarkan statistik lama tetap tampil
            }
        });
    }

    private void confirmLogout() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.btn_logout))
                .setMessage("Yakin ingin keluar dari akun ini?")
                .setPositiveButton(getString(R.string.btn_logout), (dialog, which) -> doLogout())
                .setNegativeButton(getString(R.string.btn_batal), null)
                .show();
    }

    private void doLogout() {
        FirebaseAuth.getInstance().signOut();
        if (getContext() == null) return;

        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
