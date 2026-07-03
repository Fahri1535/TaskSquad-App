package id.fahri.projectakhirmoprog.data.model;

import com.google.firebase.firestore.Exclude;

/**
 * Model tugas individual untuk Cloud Firestore (koleksi "tasks").
 *
 * Beda dari versi Room lama:
 * - id sekarang String (Firestore document ID), bukan int auto-increment.
 * - groupId sekarang String, merujuk ke id dokumen Group di koleksi "groups".
 * - userId sekarang berarti "pembuat tugas" (createdBy), bukan pemilik tunggal —
 *   siapa saja anggota grup (lihat Group.memberIds) boleh melihat & mengubah tugas ini.
 *
 * status: "PENDING", "IN_PROGRESS", "DONE", "OVERDUE".
 * fotoPath / filePath tetap path lokal per-device (bukti progress tidak disinkronkan
 * ke cloud dalam versi ini).
 */
public class Task {

    @Exclude
    private String id;

    private String userId;
    private String groupId;
    private String judul;
    private String deskripsi;
    private long deadline;
    private String status;
    private String namaAnggota;
    private String assignedToUid;
    private String fotoPath;
    private String filePath;
    private String fileName;
    private long createdAt;

    public Task() {
        // wajib untuk Firestore deserialization
    }

    public Task(String userId, String groupId, String judul, String deskripsi,
                long deadline, String status, String namaAnggota, String assignedToUid,
                String fotoPath, String filePath, String fileName, long createdAt) {
        this.userId = userId;
        this.groupId = groupId;
        this.judul = judul;
        this.deskripsi = deskripsi;
        this.deadline = deadline;
        this.status = status;
        this.namaAnggota = namaAnggota;
        this.assignedToUid = assignedToUid;
        this.fotoPath = fotoPath;
        this.filePath = filePath;
        this.fileName = fileName;
        this.createdAt = createdAt;
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getJudul() {
        return judul;
    }

    public void setJudul(String judul) {
        this.judul = judul;
    }

    public String getDeskripsi() {
        return deskripsi;
    }

    public void setDeskripsi(String deskripsi) {
        this.deskripsi = deskripsi;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNamaAnggota() {
        return namaAnggota;
    }

    public void setNamaAnggota(String namaAnggota) {
        this.namaAnggota = namaAnggota;
    }

    public String getAssignedToUid() {
        return assignedToUid;
    }

    public void setAssignedToUid(String assignedToUid) {
        this.assignedToUid = assignedToUid;
    }

    public String getFotoPath() {
        return fotoPath;
    }

    public void setFotoPath(String fotoPath) {
        this.fotoPath = fotoPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Request-code int untuk AlarmManager/NotificationManager, diturunkan dari
     * hash document ID Firestore (String). AlarmManager & notification API
     * android butuh int, jadi ini jembatannya — konsisten dipakai di
     * ReminderScheduler & ReminderReceiver.
     */
    @Exclude
    public static int toRequestCode(String firestoreTaskId) {
        return firestoreTaskId == null ? -1 : firestoreTaskId.hashCode();
    }
}
