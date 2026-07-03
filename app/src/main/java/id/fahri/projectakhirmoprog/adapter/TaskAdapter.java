package id.fahri.projectakhirmoprog.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.data.model.Task;

/**
 * Adapter untuk menampilkan daftar Task di RecyclerView (item_task.xml).
 * Menggunakan ListAdapter + DiffUtil supaya update list efisien.
 *
 * Catatan migrasi Firestore: task.getId() dan task.getGroupId() sekarang String.
 * Nama grup tidak lagi disimpan langsung di Task, jadi di-resolve lewat
 * setGroupNames() (map groupId -> namaGrup) yang di-supply dari luar.
 */
public class TaskAdapter extends ListAdapter<Task, TaskAdapter.TaskViewHolder> {

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    private final OnTaskClickListener listener;
    private final Map<String, String> groupNames = new HashMap<>();

    public TaskAdapter(OnTaskClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /**
     * Suplai nama grup untuk ditampilkan di tiap item, karena Task hanya
     * menyimpan groupId. Panggil ulang setiap kali daftar grup berubah.
     */
    public void setGroupNames(Map<String, String> groupIdToName) {
        groupNames.clear();
        if (groupIdToName != null) {
            groupNames.putAll(groupIdToName);
        }
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<Task> DIFF_CALLBACK = new DiffUtil.ItemCallback<Task>() {
        @Override
        public boolean areItemsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
            return oldItem.getJudul().equals(newItem.getJudul())
                    && oldItem.getStatus().equals(newItem.getStatus())
                    && oldItem.getDeadline() == newItem.getDeadline()
                    && (oldItem.getNamaAnggota() == null
                        ? newItem.getNamaAnggota() == null
                        : oldItem.getNamaAnggota().equals(newItem.getNamaAnggota()));
        }
    };

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = getItem(position);
        String groupName = groupNames.get(task.getGroupId());
        holder.bind(task, groupName, listener);
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {

        private final View statusBar;
        private final TextView tvJudulTugas;
        private final TextView tvAssignedTo;
        private final TextView tvDeadline;
        private final TextView badgeStatus;
        private final TextView tvGrupNama;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            statusBar = itemView.findViewById(R.id.statusBar);
            tvJudulTugas = itemView.findViewById(R.id.tvJudulTugas);
            tvAssignedTo = itemView.findViewById(R.id.tvAssignedTo);
            tvDeadline = itemView.findViewById(R.id.tvDeadline);
            badgeStatus = itemView.findViewById(R.id.badgeStatus);
            tvGrupNama = itemView.findViewById(R.id.tvGrupNama);
        }

        void bind(Task task, String groupName, OnTaskClickListener listener) {
            Context context = itemView.getContext();

            tvJudulTugas.setText(task.getJudul());

            String assigned = task.getNamaAnggota();
            tvAssignedTo.setText((assigned == null || assigned.trim().isEmpty())
                    ? "Belum diassign" : assigned);

            if (task.getDeadline() > 0) {
                String formatted = DateFormat.format("dd MMM yyyy, HH:mm", task.getDeadline()).toString();
                tvDeadline.setText(formatted);
            } else {
                tvDeadline.setText("—");
            }

            tvGrupNama.setText((groupName == null || groupName.trim().isEmpty()) ? "Grup" : groupName);

            applyStatus(context, task.getStatus());

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });
        }

        private void applyStatus(Context context, String status) {
            if (status == null) status = "PENDING";

            int barDrawable;
            String label;

            switch (status) {
                case "DONE":
                    barDrawable = R.drawable.bar_status_done;
                    label = "Selesai";
                    break;
                case "IN_PROGRESS":
                    barDrawable = R.drawable.bar_status_progress;
                    label = "Dikerjakan";
                    break;
                case "OVERDUE":
                    barDrawable = R.drawable.bar_status_overdue;
                    label = "Terlambat";
                    break;
                case "PENDING":
                default:
                    barDrawable = R.drawable.bar_status_pending;
                    label = "Belum";
                    break;
            }

            statusBar.setBackgroundResource(barDrawable);
            badgeStatus.setText(label);
        }
    }
}
