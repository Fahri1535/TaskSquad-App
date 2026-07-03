package id.fahri.projectakhirmoprog.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import id.fahri.projectakhirmoprog.MainActivity;
import id.fahri.projectakhirmoprog.R;
import id.fahri.projectakhirmoprog.data.model.Task;

/**
 * Receiver yang dipanggil AlarmManager saat waktu reminder deadline tugas tiba.
 * Menampilkan notifikasi lokal berisi judul tugas yang akan/sudah jatuh tempo.
 *
 * Catatan migrasi Firestore: EXTRA_TASK_ID sekarang berisi String (document ID).
 * Notification id (int) diturunkan dari Task.toRequestCode() supaya tetap unik.
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "tasksquad_reminder_channel";
    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String EXTRA_TASK_JUDUL = "extra_task_judul";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        String judul = intent.getStringExtra(EXTRA_TASK_JUDUL);
        if (judul == null || judul.trim().isEmpty()) {
            judul = "Tugas kamu";
        }

        createNotificationChannel(context);
        showNotification(context, taskId, judul);
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reminder Deadline Tugas",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifikasi pengingat deadline tugas TaskSquad");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(Context context, String taskId, String judul) {
        // Tap notifikasi -> buka MainActivity (bisa diarahkan ke TaskDetailActivity nanti)
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra(EXTRA_TASK_ID, taskId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        int notifId = Task.toRequestCode(taskId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notifId, openIntent, flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle("Deadline tugas sudah dekat")
                .setContentText(judul)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(judul))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Izin notifikasi belum diberikan (Android 13+) — batalkan tampilkan notifikasi.
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(notifId, builder.build());
        }
    }
}
