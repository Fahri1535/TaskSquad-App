package id.fahri.projectakhirmoprog.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Menyimpan & menerapkan preferensi tema (Terang / Gelap / Ikuti Sistem).
 *
 * Pilihan disimpan di SharedPreferences supaya bertahan lintas sesi app.
 * Penerapan aktualnya lewat AppCompatDelegate.setDefaultNightMode(), yang
 * otomatis membuat semua Activity resolve resource dari values/ (terang)
 * atau values-night/ (gelap) sesuai mode yang aktif.
 *
 * Panggil ThemePrefs.applySavedTheme(context) sekali di awal, idealnya di
 * Application.onCreate(), SEBELUM Activity manapun dibuat — supaya tidak
 * ada kedipan tema salah saat app pertama kali dibuka.
 */
public final class ThemePrefs {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    /** Nilai yang tersimpan di SharedPreferences. */
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";

    private ThemePrefs() {
        // utility class, tidak untuk diinstansiasi
    }

    @NonNull
    public static String getSavedMode(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_MODE, MODE_SYSTEM);
    }

    /**
     * Simpan pilihan baru DAN langsung terapkan ke seluruh app.
     * Activity yang sedang terbuka akan otomatis recreate lewat
     * mekanisme konfigurasi AppCompatDelegate; tidak perlu panggil
     * recreate() manual di pemanggil untuk Activity lain yang sedang
     * berjalan di background, tapi Activity yang sedang di foreground
     * sebaiknya tetap memanggil recreate() supaya perubahan terlihat
     * seketika (lihat SettingsActivity).
     */
    public static void setMode(@NonNull Context context, @NonNull String mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
        applyMode(mode);
    }

    /** Dipanggil sekali saat app start untuk menerapkan pilihan yang tersimpan. */
    public static void applySavedTheme(@NonNull Context context) {
        applyMode(getSavedMode(context));
    }

    private static void applyMode(@NonNull String mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
