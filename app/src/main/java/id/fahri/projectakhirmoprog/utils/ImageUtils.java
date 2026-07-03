package id.fahri.projectakhirmoprog.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper untuk fitur kamera: membuat file tujuan foto bukti progress tugas,
 * mendapatkan content:// URI via FileProvider (dibutuhkan MediaStore.ACTION_IMAGE_CAPTURE),
 * dan operasi terkait file gambar lainnya.
 *
 * Konsisten dengan konfigurasi res/xml/file_paths.xml (folder "images/" di internal storage).
 */
public class ImageUtils {

    private static final String FOLDER_NAME = "images";
    private static final String FILE_PREFIX = "TASKSQUAD_";
    private static final String FILE_SUFFIX = ".jpg";

    private ImageUtils() {
        // no instance
    }

    /**
     * Buat File baru dengan nama unik (timestamp) di folder internal app/files/images/.
     * Folder ini sudah didaftarkan di file_paths.xml sebagai "internal_images".
     */
    public static File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = FILE_PREFIX + timeStamp + "_";

        File storageDir = new File(context.getFilesDir(), FOLDER_NAME);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        return File.createTempFile(fileName, FILE_SUFFIX, storageDir);
    }

    /**
     * Dapatkan content:// URI dari sebuah File lokal lewat FileProvider,
     * dibutuhkan untuk memberi izin sementara ke aplikasi kamera menulis hasil foto.
     * Authority harus sama persis dengan yang dideklarasikan di AndroidManifest.xml.
     */
    public static Uri getUriForFile(Context context, File file) {
        String authority = context.getPackageName() + ".fileprovider";
        return FileProvider.getUriForFile(context, authority, file);
    }

    /**
     * Hapus file foto berdasarkan path lokal (dipanggil saat tugas dihapus
     * atau foto bukti diganti dengan yang baru).
     */
    public static boolean deleteImage(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return false;
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    /**
     * Cek apakah path foto masih valid/ada di storage.
     * Berguna sebelum menampilkan foto lewat Glide di TaskDetailActivity.
     */
    public static boolean isImageExists(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return false;
        return new File(filePath).exists();
    }

    /**
     * Cek ketersediaan penyimpanan eksternal (opsional, untuk fallback
     * kalau ingin simpan foto di Pictures/ eksternal, sesuai "external_images"
     * pada file_paths.xml).
     */
    public static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
