package id.fahri.projectakhirmoprog.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helper untuk menyalin foto/file bukti progress dari storage internal app
 * ke folder Download publik, supaya user bisa mengakses/mengirim file
 * tersebut dari luar aplikasi (mis. lewat File Manager atau WhatsApp).
 *
 * Menggunakan MediaStore.Downloads (Android 10+/API 29+) yang tidak
 * membutuhkan permission WRITE_EXTERNAL_STORAGE tambahan. Untuk versi
 * Android di bawah itu, fallback ke folder Download klasik langsung.
 */
public class DownloadUtils {

    private DownloadUtils() {
        // no instance
    }

    /**
     * Salin file lokal ke folder Download publik dengan nama file yang diberikan.
     * Menampilkan Toast sukses/gagal secara otomatis.
     */
    public static void saveToDownloads(Context context, String sourcePath, String displayName) {
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeName = (displayName == null || displayName.trim().isEmpty())
                ? sourceFile.getName()
                : displayName;

        String extension = FileUtils.getExtension(safeName);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mimeType == null) mimeType = "application/octet-stream";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, sourceFile, safeName, mimeType);
            } else {
                saveViaLegacyPath(sourceFile, safeName);
            }
            Toast.makeText(context, "Tersimpan di folder Download: " + safeName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(context, "Gagal menyimpan ke Download", Toast.LENGTH_SHORT).show();
        }
    }

    private static void saveViaMediaStore(Context context, File sourceFile, String displayName, String mimeType) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri itemUri = resolver.insert(collection, values);
        if (itemUri == null) throw new IOException("Tidak bisa membuat entri Download");

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = resolver.openOutputStream(itemUri)) {
            if (out == null) throw new IOException("Tidak bisa membuka output Download");
            copyStream(in, out);
        }

        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(itemUri, values, null, null);
    }

    private static void saveViaLegacyPath(File sourceFile, String displayName) throws IOException {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        File destFile = new File(downloadDir, displayName);

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new java.io.FileOutputStream(destFile)) {
            copyStream(in, out);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }
}
