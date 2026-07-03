package id.fahri.projectakhirmoprog;

import android.app.Application;

import id.fahri.projectakhirmoprog.util.ThemePrefs;

/**
 * Application class kustom. Satu-satunya tugasnya saat ini: menerapkan
 * preferensi tema (terang/gelap/ikuti sistem) yang tersimpan SEBELUM
 * Activity manapun (termasuk SplashActivity) dibuat, supaya tidak ada
 * "kedipan" tema salah sesaat app dibuka.
 *
 * Didaftarkan lewat android:name di AndroidManifest.xml pada tag
 * <application>.
 */
public class AppEntry extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.applySavedTheme(this);
    }
}
