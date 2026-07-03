package id.fahri.projectakhirmoprog.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.data.model.Group;

/**
 * Adapter untuk menampilkan daftar Group di RecyclerView (item_group.xml).
 * Progress per grup (jumlah tugas & persentase selesai) di-supply dari luar
 * lewat setGroupStats(), karena datanya berasal dari query terpisah (TaskRepository).
 *
 * Catatan migrasi Firestore: groupId sekarang String (document ID), bukan int.
 */
public class GroupAdapter extends ListAdapter<Group, GroupAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    public interface OnGroupLongClickListener {
        void onGroupLongClick(Group group);
    }

    public static class GroupStat {
        public final int totalTugas;
        public final int selesai;

        public GroupStat(int totalTugas, int selesai) {
            this.totalTugas = totalTugas;
            this.selesai = selesai;
        }

        public int getPercentage() {
            if (totalTugas == 0) return 0;
            return (int) ((selesai * 100f) / totalTugas);
        }
    }

    private final OnGroupClickListener listener;
    private final OnGroupLongClickListener longClickListener;
    private final Map<String, GroupStat> statsMap = new HashMap<>();

    public GroupAdapter(OnGroupClickListener listener) {
        this(listener, null);
    }

    public GroupAdapter(OnGroupClickListener listener, OnGroupLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    /**
     * Update statistik (jumlah tugas & progress) untuk sebuah grup tanpa
     * perlu me-refresh seluruh list item lainnya.
     */
    public void setGroupStats(String groupId, int totalTugas, int selesai) {
        statsMap.put(groupId, new GroupStat(totalTugas, selesai));
        for (int i = 0; i < getCurrentList().size(); i++) {
            if (Objects.equals(getCurrentList().get(i).getId(), groupId)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    private static final DiffUtil.ItemCallback<Group> DIFF_CALLBACK = new DiffUtil.ItemCallback<Group>() {
        @Override
        public boolean areItemsTheSame(@NonNull Group oldItem, @NonNull Group newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Group oldItem, @NonNull Group newItem) {
            return oldItem.getNamaGrup().equals(newItem.getNamaGrup())
                    && (oldItem.getDeskripsi() == null
                        ? newItem.getDeskripsi() == null
                        : oldItem.getDeskripsi().equals(newItem.getDeskripsi()))
                    && oldItem.getJumlahAnggota() == newItem.getJumlahAnggota()
                    && Objects.equals(oldItem.getWarna(), newItem.getWarna())
                    && Objects.equals(oldItem.getJoinCode(), newItem.getJoinCode());
        }
    };

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = getItem(position);
        GroupStat stat = statsMap.get(group.getId());
        holder.bind(group, stat, listener, longClickListener);
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {

        private final View grupDot;
        private final TextView tvKodeGrup;
        private final ImageView btnSalinKodeGrup;
        private final TextView tvNamaGrup;
        private final TextView tvJumlahTugas;
        private final ProgressBar progressGrup;
        private final TextView tvProgressGrup;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            grupDot = itemView.findViewById(R.id.grupDot);
            tvKodeGrup = itemView.findViewById(R.id.tvKodeGrup);
            btnSalinKodeGrup = itemView.findViewById(R.id.btnSalinKodeGrup);
            tvNamaGrup = itemView.findViewById(R.id.tvNamaGrup);
            tvJumlahTugas = itemView.findViewById(R.id.tvJumlahTugas);
            progressGrup = itemView.findViewById(R.id.progressGrup);
            tvProgressGrup = itemView.findViewById(R.id.tvProgressGrup);
        }

        void bind(Group group, GroupStat stat, OnGroupClickListener listener,
                  OnGroupLongClickListener longClickListener) {
            tvNamaGrup.setText(group.getNamaGrup());
            tvKodeGrup.setText(String.format("Kode: %s",
                    group.getJoinCode() != null ? group.getJoinCode() : "-"));
            grupDot.setBackgroundResource(resolveDotDrawable(group.getWarna()));

            View.OnClickListener copyListener = v -> {
                if (group.getJoinCode() == null) return;
                Context context = v.getContext();
                ClipboardManager clipboard =
                        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kode grup", group.getJoinCode()));
                    Toast.makeText(context, "Kode grup disalin", Toast.LENGTH_SHORT).show();
                }
            };
            btnSalinKodeGrup.setOnClickListener(copyListener);
            tvKodeGrup.setOnClickListener(copyListener);

            int total = stat != null ? stat.totalTugas : 0;
            int selesai = stat != null ? stat.selesai : 0;
            int percentage = stat != null ? stat.getPercentage() : 0;

            tvJumlahTugas.setText(String.format(Locale.getDefault(), "%d tugas", total));
            progressGrup.setProgress(percentage);
            tvProgressGrup.setText(String.format(Locale.getDefault(),
                    "%d%% selesai · %d anggota", percentage, group.getJumlahAnggota()));

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onGroupClick(group);
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onGroupLongClick(group);
                    return true;
                }
                return false;
            });
        }
        private static int resolveDotDrawable(String warna) {
            if (warna == null) return R.drawable.bar_status_done;
            switch (warna) {
                case "amber":
                    return R.drawable.bar_status_progress;
                case "red":
                    return R.drawable.bar_status_overdue;
                case "gray":
                    return R.drawable.bar_status_pending;
                case "green":
                default:
                    return R.drawable.bar_status_done;
            }
        }
    }
}
