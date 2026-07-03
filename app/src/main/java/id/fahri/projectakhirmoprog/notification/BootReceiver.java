package id.fahri.projectakhirmoprog.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.fahri.projectakhirmoprog.data.model.Group;
import id.fahri.projectakhirmoprog.data.model.Task;

/**
 * Setelah device reboot, semua alarm yang dijadwalkan AlarmManager otomatis hilang.
 * Receiver ini membaca ulang tugas dari Cloud Firestore (khusus grup yang diikuti
 * user yang sedang login di device ini) dan menjadwalkan ulang reminder-nya.
 *
 * Catatan migrasi Firestore: query Firestore selalu async, sedangkan BroadcastReceiver
 * hanya hidup sebentar. Karena itu dipakai goAsync() (perpanjang lifetime receiver)
 * dikombinasikan Tasks.await() di background thread untuk menunggu hasil query
 * sebelum receiver benar-benar selesai.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return; // tidak ada user login di device ini, tidak ada yang di-reschedule

        Context appContext = context.getApplicationContext();
        String userId = user.getUid();

        PendingResult pendingResult = goAsync();

        executor.execute(() -> {
            try {
                rescheduleAllReminders(appContext, userId);
            } catch (Exception e) {
                // Gagal reschedule tidak fatal — reminder akan tetap terjadwal ulang
                // begitu user membuka app secara normal (lewat TaskFormActivity/DetailActivity).
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void rescheduleAllReminders(Context appContext, String userId) throws ExecutionException, InterruptedException {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Ambil semua grup di mana user ini jadi anggota
        QuerySnapshot groupSnapshots = Tasks.await(
                db.collection("groups").whereArrayContains("memberIds", userId).get()
        );

        List<String> groupIds = new ArrayList<>();
        for (DocumentSnapshot doc : groupSnapshots.getDocuments()) {
            Group g = doc.toObject(Group.class);
            if (g != null) {
                groupIds.add(doc.getId());
            }
        }
        if (groupIds.isEmpty()) return;

        // Firestore whereIn dibatasi maksimal 30 item per query
        List<String> limitedGroupIds = groupIds.size() > 30 ? groupIds.subList(0, 30) : groupIds;

        // 2. Ambil semua tugas dari grup-grup tersebut
        QuerySnapshot taskSnapshots = Tasks.await(
                db.collection("tasks").whereIn("groupId", limitedGroupIds).get()
        );

        long now = System.currentTimeMillis();
        for (DocumentSnapshot doc : taskSnapshots.getDocuments()) {
            Task task = doc.toObject(Task.class);
            if (task == null) continue;
            task.setId(doc.getId());

            if (!"DONE".equals(task.getStatus()) && task.getDeadline() > now) {
                ReminderScheduler.scheduleReminder(
                        appContext,
                        task.getId(),
                        task.getJudul(),
                        task.getDeadline()
                );
            }
        }
    }
}
