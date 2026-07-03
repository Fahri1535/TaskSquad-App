package id.fahri.projectakhirmoprog;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import id.fahri.projectakhirmoprog.auth.LoginActivity;

/**
 * Entry point aplikasi.
 * Cek apakah user sudah login:
 * - Sudah login → langsung ke MainActivity
 * - Belum login → ke LoginActivity
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            // User sudah login sebelumnya, langsung masuk
            startActivity(new Intent(this, MainActivity.class));
        } else {
            // Belum login, arahkan ke Login
            startActivity(new Intent(this, LoginActivity.class));
        }

        finish(); // Tutup SplashActivity supaya tidak bisa back ke sini
    }
}
