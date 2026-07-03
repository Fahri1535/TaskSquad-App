package id.fahri.projectakhirmoprog.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper untuk fitur upload file dokumen (Word/PDF/Excel/dll) sebagai bukti
 * progress tugas: menyalin file dari content:// URI (hasil ACTION_OPEN_DOCUMENT)
 * ke storage internal app, membaca nama file asli, dan membuka file lewat
 * aplikasi viewer eksternal (Word/PDF reader, dst).
 *
 * Konsisten dengan konfigurasi res/xml/file_paths.xml (folder "documents/").
 */
public class FileUtils {

    private static final String FOLDER_NAME = "documents";

    private FileUtils() {
        // no instance
    }

    /**
     * Ambil nama file asli dari sebuah content:// URI (mis. hasil ACTION_OPEN_DOCUMENT).
     * Fallback ke nama generik kalau nama tidak bisa dibaca dari cursor.
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
            // fallback di bawah
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "Dokumen_" + System.currentTimeMillis();
        }
        return fileName;
    }

    /**
     * Salin isi file dari content:// URI ke folder internal app/files/documents/,
     * dengan nama file unik (timestamp) tapi tetap mempertahankan ekstensi asli.
     * Mengembalikan File hasil salinan, atau null jika gagal.
     */
    public static File copyToInternalStorage(Context context, Uri sourceUri, String originalFileName) throws IOException {
        File storageDir = new File(context.getFilesDir(), FOLDER_NAME);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String extension = getExtension(originalFileName);
        String safeName = "TASKSQUAD_DOC_" + timeStamp + (extension.isEmpty() ? "" : "." + extension);

        File destFile = new File(storageDir, safeName);

        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) throw new IOException("Tidak bisa membuka file sumber");

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(bytesRead == buffer.length ? buffer : trim(buffer, bytesRead));
            }
        }

        return destFile;
    }

    private static byte[] trim(byte[] buffer, int length) {
        byte[] trimmed = new byte[length];
        System.arraycopy(buffer, 0, trimmed, 0, length);
        return trimmed;
    }

    /**
     * Ambil ekstensi file dari nama file (tanpa titik), huruf kecil.
     * Contoh: "Laporan Bab 1.docx" -> "docx"
     */
    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) return "";
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Dapatkan content:// URI dari sebuah File lokal lewat FileProvider,
     * dibutuhkan untuk membuka file di aplikasi viewer eksternal (Word/PDF/Excel).
     */
    public static Uri getUriForFile(Context context, File file) {
        String authority = context.getPackageName() + ".fileprovider";
        return FileProvider.getUriForFile(context, authority, file);
    }

    /**
     * Buka file dokumen lewat aplikasi viewer eksternal yang sesuai
     * (mis. Word untuk .docx, Adobe/Drive untuk .pdf, Excel untuk .xlsx).
     * Menampilkan Toast jika tidak ada aplikasi yang bisa membukanya.
     */
    public static void openFile(Context context, String filePath, String fileName) {
        if (filePath == null || filePath.trim().isEmpty()) return;
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = getUriForFile(context, file);
        String extension = getExtension(fileName != null ? fileName : filePath);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mimeType == null) mimeType = "*/*";

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hapus file dokumen berdasarkan path lokal (dipanggil saat tugas dihapus
     * atau file bukti diganti dengan yang baru).
     */
    public static boolean deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return false;
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    /**
     * Cek apakah path file dokumen masih valid/ada di storage.
     */
    public static boolean isFileExists(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return false;
        return new File(filePath).exists();
    }

    /**
     * Pilih ikon dokumen berdasarkan ekstensi file, agar user bisa langsung
     * mengenali jenis file (Word/PDF/Excel/lainnya) dari tampilan card.
     */
    public static int getIconForExtension(String extension) {
        if (extension == null) return id.fahri.projectakhirmoprog.R.drawable.ic_file_generic;
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "doc":
            case "docx":
                return id.fahri.projectakhirmoprog.R.drawable.ic_file_word;
            case "xls":
            case "xlsx":
                return id.fahri.projectakhirmoprog.R.drawable.ic_file_excel;
            case "pdf":
                return id.fahri.projectakhirmoprog.R.drawable.ic_file_pdf;
            default:
                return id.fahri.projectakhirmoprog.R.drawable.ic_file_generic;
        }
    }
}
