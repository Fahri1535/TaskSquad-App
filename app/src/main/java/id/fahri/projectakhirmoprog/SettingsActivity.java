package id.fahri.projectakhirmoprog;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import id.fahri.projectakhirmoprog.util.ThemePrefs;

/**
 * Halaman Pengaturan. Berisi:
 * - Pemilihan tema (Terang / Gelap / Ikuti Sistem) — langsung diterapkan
 *   saat opsi dipilih lewat ThemePrefs, lalu Activity ini recreate() supaya
 *   perubahan terlihat seketika tanpa perlu keluar-masuk app.
 * - Toggle notifikasi pengingat deadline (disimpan terpisah, dibaca oleh
 *   ReminderScheduler sebelum menjadwalkan reminder baru).
 * - Info versi aplikasi.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String NOTIF_PREFS_NAME = "notif_prefs";
    private static final String KEY_NOTIF_ENABLED = "reminder_enabled";

    private ConstraintLayout optTerang, optGelap, optSistem;
    private ImageView checkTerang, checkGelap, checkSistem;
    private SwitchCompat switchNotifikasi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupThemeOptions();
        setupNotificationSwitch();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void initViews() {
        optTerang = findViewById(R.id.optTerang);
        optGelap = findViewById(R.id.optGelap);
        optSistem = findViewById(R.id.optSistem);

        checkTerang = findViewById(R.id.checkTerang);
        checkGelap = findViewById(R.id.checkGelap);
        checkSistem = findViewById(R.id.checkSistem);

        switchNotifikasi = findViewById(R.id.switchNotifikasi);

        optTerang.setOnClickListener(v -> pilihTema(ThemePrefs.MODE_LIGHT));
        optGelap.setOnClickListener(v -> pilihTema(ThemePrefs.MODE_DARK));
        optSistem.setOnClickListener(v -> pilihTema(ThemePrefs.MODE_SYSTEM));
    }

    private void setupThemeOptions() {
        refreshThemeSelectionUI(ThemePrefs.getSavedMode(this));
    }

    private void pilihTema(@NonNull String mode) {
        String modeSebelumnya = ThemePrefs.getSavedMode(this);
        if (mode.equals(modeSebelumnya)) return; // sudah dipilih, tidak perlu apa-apa

        ThemePrefs.setMode(this, mode);
        // recreate() supaya tema baru langsung terlihat di layar ini juga,
        // bukan hanya di Activity yang dibuka berikutnya.
        recreate();
    }

    private void refreshThemeSelectionUI(@NonNull String activeMode) {
        boolean isTerang = ThemePrefs.MODE_LIGHT.equals(activeMode);
        boolean isGelap = ThemePrefs.MODE_DARK.equals(activeMode);
        boolean isSistem = ThemePrefs.MODE_SYSTEM.equals(activeMode);

        checkTerang.setVisibility(isTerang ? View.VISIBLE : View.GONE);
        checkGelap.setVisibility(isGelap ? View.VISIBLE : View.GONE);
        checkSistem.setVisibility(isSistem ? View.VISIBLE : View.GONE);

        optTerang.setBackgroundResource(isTerang ? R.drawable.bg_card_selected : R.drawable.bg_card);
        optGelap.setBackgroundResource(isGelap ? R.drawable.bg_card_selected : R.drawable.bg_card);
        optSistem.setBackgroundResource(isSistem ? R.drawable.bg_card_selected : R.drawable.bg_card);
    }

    private void setupNotificationSwitch() {
        SharedPreferences prefs = getSharedPreferences(NOTIF_PREFS_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_NOTIF_ENABLED, true);
        switchNotifikasi.setChecked(enabled);

        switchNotifikasi.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_NOTIF_ENABLED, isChecked).apply());
    }

    /**
     * Dipanggil dari kelas lain (mis. ReminderScheduler) untuk cek apakah
     * user mengizinkan reminder dijadwalkan. Default true kalau belum
     * pernah diatur.
     */
    public static boolean isReminderEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(NOTIF_PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIF_ENABLED, true);
    }
}
