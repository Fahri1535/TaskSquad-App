package id.fahri.projectakhirmoprog.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import id.fahri.projectakhirmoprog.MainActivity;
import id.fahri.projectakhirmoprog.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private ImageView btnTogglePassword;
    private Button btnLogin;
    private TextView tvGoToRegister, tvForgotPassword;

    private FirebaseAuth mAuth;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        initViews();
        setListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setListeners() {
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        btnLogin.setOnClickListener(v -> doLogin());
        tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError(getString(R.string.error_email_kosong));
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.error_password_kosong));
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError(getString(R.string.error_password_pendek));
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Memproses...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sinkronkanProfilKeFirestore();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        btnLogin.setEnabled(true);
                        btnLogin.setText(getString(R.string.btn_masuk));
                        Toast.makeText(this,
                                getString(R.string.error_login_gagal),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Jaga-jaga untuk akun yang register-nya sebelum fitur Firestore users
     * ditambahkan: pastikan dokumen users/{uid} selalu ada tiap kali login,
     * supaya fitur "tambah anggota grup lewat email" tetap bisa menemukannya.
     * Pakai merge supaya tidak menimpa field lain kalau nanti ditambah.
     */
    private void sinkronkanProfilKeFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        Map<String, Object> data = new HashMap<>();
        // Jangan pernah menimpa "nama" dengan string kosong — kalau displayName
        // di client belum ke-sync (mis. tepat setelah register), field "nama"
        // yang sudah tersimpan di Firestore (dari register/login sebelumnya)
        // harus tetap dipertahankan, bukan ditimpa jadi kosong.
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            data.put("nama", displayName);
        } else {
            // Tidak ada displayName sama sekali (mis. dokumen users/{uid} belum
            // pernah tersimpan dan profil Auth juga kosong). Daripada nanti
            // fallback ke uid mentah di dropdown assign tugas, pakai bagian
            // sebelum "@" dari email sebagai nama sementara yang lebih terbaca.
            String email = user.getEmail();
            String namaFallback = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            data.put("nama", namaFallback);
        }
        data.put("email", user.getEmail().trim().toLowerCase(Locale.getDefault()));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> android.util.Log.w("LoginActivity",
                        "Gagal sinkronkan profil ke Firestore untuk uid=" + user.getUid(), e));
    }

    private void sendPasswordReset() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            etEmail.setError("Masukkan email dulu untuk reset kata sandi");
            etEmail.requestFocus();
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Email reset kata sandi dikirim ke " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Gagal kirim email reset, periksa email yang dimasukkan",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
