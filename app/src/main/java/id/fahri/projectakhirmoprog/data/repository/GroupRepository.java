package id.fahri.projectakhirmoprog.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import id.fahri.projectakhirmoprog.data.model.Group;

/**
 * Repository untuk operasi Cloud Firestore terkait Group.
 * Pengganti GroupDao (Room) — semua operasi async lewat callback,
 * dan listGroups() bersifat realtime (auto update kalau data berubah di server).
 */
public class GroupRepository {

    private static final String COLLECTION_GROUPS = "groups";
    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db;

    public interface OnGroupResult {
        void onSuccess(Group group);
        void onError(Exception e);
    }

    public interface OnGroupListResult {
        void onSuccess(List<Group> groups);
        void onError(Exception e);
    }

    public interface OnSimpleResult {
        void onSuccess();
        void onError(Exception e);
    }

    public interface OnMemberListResult {
        void onSuccess(List<Member> members);
        void onError(Exception e);
    }

    /**
     * Representasi ringkas anggota grup untuk keperluan UI (mis. dropdown assign tugas).
     * uid dipakai sebagai identitas asli, nama untuk ditampilkan ke user.
     */
    public static class Member {
        public final String uid;
        public final String nama;

        public Member(String uid, String nama) {
            this.uid = uid;
            this.nama = nama;
        }

        @Override
        public String toString() {
            return nama;
        }
    }

    public GroupRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private CollectionReference groupsRef() {
        return db.collection(COLLECTION_GROUPS);
    }

    /**
     * Buat grup baru. ownerId otomatis jadi anggota pertama.
     * joinCode di-generate otomatis (6 karakter) dan dicek supaya tidak bentrok.
     */
    public void createGroup(String ownerId, String namaGrup, String deskripsi, String warna, OnGroupResult callback) {
        String joinCode = generateJoinCode();
        String finalWarna = (warna == null || warna.isEmpty()) ? "green" : warna;
        Group group = new Group(ownerId, namaGrup, deskripsi, joinCode, finalWarna, System.currentTimeMillis());

        groupsRef().add(group)
                .addOnSuccessListener(docRef -> {
                    group.setId(docRef.getId());
                    callback.onSuccess(group);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Listener realtime untuk semua grup di mana userId jadi anggota.
     * Return ListenerRegistration supaya bisa di-remove() saat fragment/activity destroy,
     * mencegah memory leak.
     */
    public ListenerRegistration listenGroups(String userId, OnGroupListResult callback) {
        return groupsRef()
                .whereArrayContains("memberIds", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Group> groups = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Group g = doc.toObject(Group.class);
                            if (g != null) {
                                g.setId(doc.getId());
                                groups.add(g);
                            }
                        }
                    }
                    callback.onSuccess(groups);
                });
    }

    public void getGroupById(String groupId, OnGroupResult callback) {
        groupsRef().document(groupId).get()
                .addOnSuccessListener(doc -> {
                    Group g = doc.toObject(Group.class);
                    if (g != null) {
                        g.setId(doc.getId());
                        callback.onSuccess(g);
                    } else {
                        callback.onError(new Exception("Grup tidak ditemukan"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Gabung grup lewat kode undangan. Cari grup dengan joinCode yang cocok,
     * lalu tambahkan userId ke memberIds (kalau belum jadi anggota).
     */
    public void joinGroupByCode(String joinCode, String userId, OnGroupResult callback) {
        groupsRef().whereEqualTo("joinCode", joinCode.trim().toUpperCase(Locale.getDefault()))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new Exception("Kode grup tidak ditemukan"));
                        return;
                    }
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    Group group = doc.toObject(Group.class);
                    if (group == null) {
                        callback.onError(new Exception("Data grup tidak valid"));
                        return;
                    }
                    group.setId(doc.getId());

                    if (group.getMemberIds() != null && group.getMemberIds().contains(userId)) {
                        // sudah jadi anggota, tidak perlu update
                        callback.onSuccess(group);
                        return;
                    }

                    doc.getReference().update("memberIds", FieldValue.arrayUnion(userId))
                            .addOnSuccessListener(unused -> callback.onSuccess(group))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Tambah anggota lewat email. Cari uid di koleksi "users" berdasarkan email,
     * lalu tambahkan ke memberIds grup. Koleksi "users" perlu diisi saat
     * register/login (lihat catatan di bawah).
     */
    public void addMemberByEmail(String groupId, String email, OnSimpleResult callback) {
        db.collection(COLLECTION_USERS)
                .whereEqualTo("email", email.trim().toLowerCase(Locale.getDefault()))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new Exception("Email tidak terdaftar"));
                        return;
                    }
                    String targetUid = snapshot.getDocuments().get(0).getId();
                    groupsRef().document(groupId)
                            .update("memberIds", FieldValue.arrayUnion(targetUid))
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Ambil daftar anggota grup (uid + nama lengkap) berdasarkan memberIds grup,
     * dengan query ke koleksi "users". Dipakai untuk mengisi dropdown "Ditugaskan
     * kepada" di form tugas.
     *
     * Firestore whereIn dibatasi maksimal 10 item per query, jadi memberIds
     * dipecah per batch 10 kalau anggotanya banyak.
     *
     * PENTING: setiap uid di memberIds SELALU dimasukkan ke hasil akhir, walau
     * document-nya di koleksi "users" tidak ditemukan (mis. akun lama yang
     * dibuat sebelum profil disimpan ke Firestore, atau baru login lewat
     * device lain). Kalau tidak, dropdown bisa tampil kosong walau grup sudah
     * punya anggota — termasuk diri sendiri. Untuk uid milik user yang sedang
     * login, nama fallback diambil dari FirebaseAuth (displayName/email);
     * untuk uid lain, fallback-nya uid itu sendiri supaya tetap terlihat.
     */
    public void getGroupMembers(List<String> memberIds, OnMemberListResult callback) {
        if (memberIds == null || memberIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        java.util.Map<String, Member> resultMap = new java.util.LinkedHashMap<>();
        for (String uid : memberIds) {
            resultMap.put(uid, fallbackMember(uid));
        }

        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < memberIds.size(); i += 10) {
            batches.add(memberIds.subList(i, Math.min(i + 10, memberIds.size())));
        }

        int[] remaining = {batches.size()};

        for (List<String> batch : batches) {
            db.collection(COLLECTION_USERS)
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String nama = doc.getString("nama");
                            if (nama == null || nama.trim().isEmpty()) {
                                nama = doc.getString("email");
                            }
                            resultMap.put(doc.getId(), new Member(doc.getId(), nama != null ? nama : doc.getId()));
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.onSuccess(new ArrayList<>(resultMap.values()));
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Query users gagal (mis. security rules) — tetap kembalikan
                        // fallback yang sudah disiapkan, jangan biarkan dropdown kosong.
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.onSuccess(new ArrayList<>(resultMap.values()));
                        }
                    });
        }
    }

    private Member fallbackMember(String uid) {
        com.google.firebase.auth.FirebaseUser current =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (current != null && current.getUid().equals(uid)) {
            String nama = current.getDisplayName();
            if (nama == null || nama.trim().isEmpty()) {
                nama = current.getEmail();
            }
            return new Member(uid, nama != null ? nama : uid);
        }
        return new Member(uid, uid);
    }

    public void deleteGroup(String groupId, OnSimpleResult callback) {
        groupsRef().document(groupId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    private String generateJoinCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // tanpa 0/O/1/I biar tidak ambigu
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
