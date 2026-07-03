package id.fahri.projectakhirmoprog.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.util.ArrayList;
import java.util.List;

/**
 * Model grup/kategori tugas untuk Cloud Firestore (koleksi "groups").
 *
 * Beda dari versi Room lama:
 * - id sekarang String (Firestore document ID), bukan int auto-increment.
 * - ownerId: pembuat grup (dulu "userId" tunggal).
 * - memberIds: daftar uid semua anggota grup (termasuk owner). Dipakai untuk
 *   query "grup yang saya ikuti" dan untuk security rules (siapa boleh baca/tulis).
 * - joinCode: kode 6 karakter untuk undang anggota lewat share manual.
 *
 * Constructor kosong wajib ada supaya Firestore bisa deserialize otomatis
 * lewat toObject(Group.class).
 */
public class Group {

    @Exclude
    private String id; // diisi manual dari document.getId(), bukan field asli di Firestore

    private String ownerId;
    private String namaGrup;
    private String deskripsi;
    private String joinCode;
    private String warna;
    private List<String> memberIds;
    private long createdAt;

    public Group() {
        // wajib untuk Firestore deserialization
        this.memberIds = new ArrayList<>();
    }

    public Group(String ownerId, String namaGrup, String deskripsi, String joinCode, long createdAt) {
        this(ownerId, namaGrup, deskripsi, joinCode, "green", createdAt);
    }

    public Group(String ownerId, String namaGrup, String deskripsi, String joinCode, String warna, long createdAt) {
        this.ownerId = ownerId;
        this.namaGrup = namaGrup;
        this.deskripsi = deskripsi;
        this.joinCode = joinCode;
        this.warna = warna;
        this.createdAt = createdAt;
        this.memberIds = new ArrayList<>();
        this.memberIds.add(ownerId);
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getNamaGrup() {
        return namaGrup;
    }

    public void setNamaGrup(String namaGrup) {
        this.namaGrup = namaGrup;
    }

    public String getDeskripsi() {
        return deskripsi;
    }

    public void setDeskripsi(String deskripsi) {
        this.deskripsi = deskripsi;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    public String getWarna() {
        return warna;
    }

    public void setWarna(String warna) {
        this.warna = warna;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public int getJumlahAnggota() {
        return memberIds != null ? memberIds.size() : 0;
    }
}
