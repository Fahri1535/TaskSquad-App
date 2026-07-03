package id.fahri.projectakhirmoprog.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import id.fahri.projectakhirmoprog.data.model.Task;

/**
 * Helper untuk schedule & cancel alarm reminder deadline tugas menggunakan AlarmManager.
 * Panggil scheduleReminder() setiap kali tugas dibuat/diedit dengan deadline baru,
 * dan cancelReminder() saat tugas dihapus atau ditandai selesai.
 *
 * Catatan migrasi Firestore: taskId sekarang String (document ID). AlarmManager
 * butuh int sebagai request code, jadi dipakai Task.toRequestCode() (hash dari
 * String id) supaya tetap unik per tugas tanpa perlu ubah API Android.
 */
public class ReminderScheduler {

    // Reminder ditampilkan 1 jam sebelum deadline
    private static final long REMINDER_OFFSET_MILLIS = 60 * 60 * 1000L;

    private ReminderScheduler() {
        // no instance
    }

    public static void scheduleReminder(Context context, String taskId, String judulTugas, long deadlineMillis) {
        long triggerAt = deadlineMillis - REMINDER_OFFSET_MILLIS;

        // Kalau waktu reminder sudah lewat, jangan dijadwalkan
        if (triggerAt <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context, taskId, judulTugas);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                // Fallback: alarm tidak presisi kalau user belum kasih izin exact alarm
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancelReminder(Context context, String taskId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context, taskId, null);
        alarmManager.cancel(pendingIntent);
    }

    private static PendingIntent buildPendingIntent(Context context, String taskId, String judulTugas) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_JUDUL, judulTugas);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        int requestCode = Task.toRequestCode(taskId);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
