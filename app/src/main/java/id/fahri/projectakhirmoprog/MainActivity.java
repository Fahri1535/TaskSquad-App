package id.fahri.projectakhirmoprog;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import id.fahri.projectakhirmoprog.ui.DashboardFragment;
import id.fahri.projectakhirmoprog.ui.GrupFragment;
import id.fahri.projectakhirmoprog.ui.ProfilFragment;

/**
 * Host untuk 3 tab utama aplikasi: Dashboard, Grup, Profil.
 * Menggunakan bottom navigation custom (bukan BottomNavigationView bawaan Material)
 * sesuai desain activity_main.xml — fragment disembunyikan/ditampilkan agar
 * state tiap tab (posisi scroll, dsb.) tetap terjaga saat pindah tab.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_DASHBOARD = "tag_dashboard";
    private static final String TAG_GRUP = "tag_grup";
    private static final String TAG_PROFIL = "tag_profil";

    private LinearLayout navDashboard, navGrup, navProfil;
    private ImageView navIconDashboard, navIconGrup, navIconProfil;
    private TextView navLabelDashboard, navLabelGrup, navLabelProfil;

    private Fragment dashboardFragment, grupFragment, profilFragment;
    private Fragment activeFragment;

    private enum Tab { DASHBOARD, GRUP, PROFIL }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupFragments(savedInstanceState);
        setupNavListeners();
    }

    private void initViews() {
        navDashboard = findViewById(R.id.navDashboard);
        navGrup = findViewById(R.id.navGrup);
        navProfil = findViewById(R.id.navProfil);

        navIconDashboard = findViewById(R.id.navIconDashboard);
        navIconGrup = findViewById(R.id.navIconGrup);
        navIconProfil = findViewById(R.id.navIconProfil);

        navLabelDashboard = findViewById(R.id.navLabelDashboard);
        navLabelGrup = findViewById(R.id.navLabelGrup);
        navLabelProfil = findViewById(R.id.navLabelProfil);
    }

    private void setupFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState != null) {
            dashboardFragment = fm.findFragmentByTag(TAG_DASHBOARD);
            grupFragment = fm.findFragmentByTag(TAG_GRUP);
            profilFragment = fm.findFragmentByTag(TAG_PROFIL);
        }

        if (dashboardFragment == null) dashboardFragment = new DashboardFragment();
        if (grupFragment == null) grupFragment = new GrupFragment();
        if (profilFragment == null) profilFragment = new ProfilFragment();

        FragmentTransaction transaction = fm.beginTransaction();
        if (!dashboardFragment.isAdded()) {
            transaction.add(R.id.fragmentContainer, dashboardFragment, TAG_DASHBOARD);
        }
        if (!grupFragment.isAdded()) {
            transaction.add(R.id.fragmentContainer, grupFragment, TAG_GRUP);
        }
        if (!profilFragment.isAdded()) {
            transaction.add(R.id.fragmentContainer, profilFragment, TAG_PROFIL);
        }
        transaction.hide(grupFragment).hide(profilFragment);
        transaction.commitNow();

        activeFragment = dashboardFragment;
        setActiveTab(Tab.DASHBOARD);
    }

    private void setupNavListeners() {
        navDashboard.setOnClickListener(v -> switchTab(Tab.DASHBOARD));
        navGrup.setOnClickListener(v -> switchTab(Tab.GRUP));
        navProfil.setOnClickListener(v -> switchTab(Tab.PROFIL));
    }

    private void switchTab(@NonNull Tab tab) {
        Fragment target;
        switch (tab) {
            case GRUP:
                target = grupFragment;
                break;
            case PROFIL:
                target = profilFragment;
                break;
            case DASHBOARD:
            default:
                target = dashboardFragment;
                break;
        }

        if (target == activeFragment) return;

        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();

        activeFragment = target;
        setActiveTab(tab);
    }

    private void setActiveTab(Tab tab) {
        int accent = getColor(R.color.accent_start);
        int secondary = getColor(R.color.text_secondary);

        // Reset semua ke inactive dulu
        navIconDashboard.setImageResource(R.drawable.ic_home);
        navIconGrup.setImageResource(R.drawable.ic_layers);
        navIconProfil.setImageResource(R.drawable.ic_user_circle);

        navLabelDashboard.setTextColor(secondary);
        navLabelGrup.setTextColor(secondary);
        navLabelProfil.setTextColor(secondary);

        navLabelDashboard.setTypeface(null, Typeface.NORMAL);
        navLabelGrup.setTypeface(null, Typeface.NORMAL);
        navLabelProfil.setTypeface(null, Typeface.NORMAL);

        switch (tab) {
            case GRUP:
                navIconGrup.setImageResource(R.drawable.ic_layers_active);
                navLabelGrup.setTextColor(accent);
                navLabelGrup.setTypeface(null, Typeface.BOLD);
                break;
            case PROFIL:
                navIconProfil.setImageResource(R.drawable.ic_user_circle_active);
                navLabelProfil.setTextColor(accent);
                navLabelProfil.setTypeface(null, Typeface.BOLD);
                break;
            case DASHBOARD:
            default:
                navIconDashboard.setImageResource(R.drawable.ic_home_active);
                navLabelDashboard.setTextColor(accent);
                navLabelDashboard.setTypeface(null, Typeface.BOLD);
                break;
        }
    }
}
