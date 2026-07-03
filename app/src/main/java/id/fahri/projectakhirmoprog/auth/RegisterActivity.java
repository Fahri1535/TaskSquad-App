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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import id.fahri.projectakhirmoprog.MainActivity;
import id.fahri.projectakhirmoprog.R;

public class RegisterActivity extends AppCompatActivity {

    private EditText etNama, etEmail, etPassword, etKonfirmasi;
    private ImageView btnBack, btnTogglePassword, btnToggleKonfirmasi;
    private Button btnRegister;
    private TextView tvGoToLogin;

    private FirebaseAuth mAuth;
    private boolean isPasswordVisible = false;
    private boolean isKonfirmasiVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        initViews();
        setListeners();
    }

    private void initViews() {
        etNama = findViewById(R.id.etNama);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etKonfirmasi = findViewById(R.id.etKonfirmasi);
        btnBack = findViewById(R.id.btnBack);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnToggleKonfirmasi = findViewById(R.id.btnToggleKonfirmasi);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            etPassword.setTransformationMethod(isPasswordVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etPassword.setSelection(etPassword.getText().length());
        });

        btnToggleKonfirmasi.setOnClickListener(v -> {
            isKonfirmasiVisible = !isKonfirmasiVisible;
            etKonfirmasi.setTransformationMethod(isKonfirmasiVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etKonfirmasi.setSelection(etKonfirmasi.getText().length());
        });

        btnRegister.setOnClickListener(v -> doRegister());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void doRegister() {
        String nama = etNama.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String konfirmasi = etKonfirmasi.getText().toString().trim();

        // Validasi semua field
        if (nama.isEmpty()) {
            etNama.setError(getString(R.string.error_nama_kosong));
            etNama.requestFocus();
            return;
        }
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
        if (!password.equals(konfirmasi)) {
            etKonfirmasi.setError(getString(R.string.error_password_tidak_cocok));
            etKonfirmasi.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Membuat akun...");

        // Buat akun Firebase
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Simpan nama tampilan ke profil Firebase
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(nama)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        // Simpan mapping email -> uid di Firestore, dipakai untuk
                                        // fitur "tambah anggota grup lewat email"
                                        simpanProfilKeFirestore(user.getUid(), nama, email);
                                        // Lanjut ke MainActivity terlepas profil update berhasil/gagal
                                        goToMain();
                                    });
                        } else {
                            goToMain();
                        }
                    } else {
                        btnRegister.setEnabled(true);
                        btnRegister.setText(getString(R.string.btn_daftar));
                        String pesanError = task.getException() != null
                                ? task.getException().getMessage()
                                : getString(R.string.error_register_gagal);
                        Toast.makeText(this, pesanError, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void simpanProfilKeFirestore(String uid, String nama, String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("nama", nama);
        data.put("email", email.trim().toLowerCase(Locale.getDefault()));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(data)
                .addOnFailureListener(e -> {
                    // Gagal simpan profil saat register (mis. koneksi putus/security
                    // rules). Tidak menghalangi user masuk ke app — tapi dicatat di
                    // Logcat supaya kelihatan saat debugging, dan akan otomatis
                    // diperbaiki oleh sinkronkanProfilKeFirestore() di LoginActivity
                    // pada login berikutnya.
                    android.util.Log.w("RegisterActivity",
                            "Gagal menyimpan profil ke Firestore untuk uid=" + uid, e);
                });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
