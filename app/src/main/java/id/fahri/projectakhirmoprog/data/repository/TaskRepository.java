package id.fahri.projectakhirmoprog.data.repository;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

import id.fahri.projectakhirmoprog.data.model.Task;

/**
 * Repository untuk operasi Cloud Firestore terkait Task.
 * Pengganti TaskDao (Room) — semua operasi async lewat callback,
 * dan listener bersifat realtime (auto update kalau data berubah di server,
 * termasuk perubahan dari anggota grup lain).
 */
public class TaskRepository {

    private static final String COLLECTION_TASKS = "tasks";

    private final FirebaseFirestore db;

    public interface OnTaskResult {
        void onSuccess(Task task);
        void onError(Exception e);
    }

    public interface OnTaskListResult {
        void onSuccess(List<Task> tasks);
        void onError(Exception e);
    }

    public interface OnIdResult {
        void onSuccess(String taskId);
        void onError(Exception e);
    }

    public interface OnSimpleResult {
        void onSuccess();
        void onError(Exception e);
    }

    public TaskRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private CollectionReference tasksRef() {
        return db.collection(COLLECTION_TASKS);
    }

    public void insert(Task task, OnIdResult callback) {
        tasksRef().add(task)
                .addOnSuccessListener(docRef -> callback.onSuccess(docRef.getId()))
                .addOnFailureListener(callback::onError);
    }

    public void update(Task task, OnSimpleResult callback) {
        if (task.getId() == null) {
            callback.onError(new IllegalArgumentException("Task id kosong"));
            return;
        }
        tasksRef().document(task.getId()).set(task)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void delete(String taskId, OnSimpleResult callback) {
        tasksRef().document(taskId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void getTaskById(String taskId, OnTaskResult callback) {
        tasksRef().document(taskId).get()
                .addOnSuccessListener(doc -> {
                    Task t = doc.toObject(Task.class);
                    if (t != null) {
                        t.setId(doc.getId());
                        callback.onSuccess(t);
                    } else {
                        callback.onError(new Exception("Tugas tidak ditemukan"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Listener realtime semua tugas dalam satu grup, diurutkan berdasarkan deadline.
     * Semua anggota grup yang login akan melihat perubahan yang sama secara live.
     */
    public ListenerRegistration listenTasksByGroup(String groupId, OnTaskListResult callback) {
        return tasksRef()
                .whereEqualTo("groupId", groupId)
                .orderBy("deadline", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(toList(snapshots));
                });
    }

    /**
     * Listener realtime tugas dalam satu grup, difilter hanya yang
     * assignedToUid-nya cocok dengan userId (mis. dipakai di TaskListActivity
     * supaya tiap orang cuma lihat tugas yang ditugaskan ke dirinya).
     *
     * PENTING: task lama (dibuat sebelum field assignedToUid ditambahkan) tidak
     * akan pernah cocok di query ini karena field-nya tidak ada di dokumen —
     * jadi task lama tidak tampil di siapa pun sampai di-edit ulang dan
     * assignee-nya dipilih ulang lewat form. Ini perilaku yang disengaja,
     * bukan bug.
     *
     * Butuh composite index (groupId + assignedToUid + deadline) di Firestore;
     * kalau index belum dibuat, Firestore akan mengembalikan error yang berisi
     * link untuk membuat index otomatis lewat Console.
     */
    public ListenerRegistration listenTasksByGroupForAssignee(String groupId, String userId, OnTaskListResult callback) {
        return tasksRef()
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("assignedToUid", userId)
                .orderBy("deadline", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(toList(snapshots));
                });
    }

    /**
     * Listener realtime semua tugas dari daftar grup yang diikuti user (dashboard).
     * Firestore whereIn dibatasi maksimal 30 item per query, jadi kalau grup lebih
     * dari 30 perlu dipecah jadi beberapa query (jarang terjadi untuk tugas kuliah).
     */
    public ListenerRegistration listenTasksByGroupIds(List<String> groupIds, OnTaskListResult callback) {
        if (groupIds == null || groupIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return null;
        }
        List<String> limited = groupIds.size() > 30 ? groupIds.subList(0, 30) : groupIds;

        return tasksRef()
                .whereIn("groupId", limited)
                .orderBy("deadline", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(toList(snapshots));
                });
    }

    public void getTasksByGroupSync(String groupId, OnTaskListResult callback) {
        tasksRef().whereEqualTo("groupId", groupId).get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toList(snapshots)))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Listener realtime tugas dari daftar grup yang diikuti user, difilter
     * hanya assignedToUid == userId. Dipakai untuk statistik dashboard &
     * profil supaya angka "total tugas" / "selesai" merefleksikan tugas
     * milik user, bukan seluruh tugas semua grup.
     *
     * Firestore whereIn dibatasi 30 item per query (lihat listenTasksByGroupIds).
     * Task lama tanpa assignedToUid tidak akan ikut terhitung — sama seperti
     * listenTasksByGroupForAssignee.
     */
    public ListenerRegistration listenTasksByGroupIdsForAssignee(List<String> groupIds, String userId, OnTaskListResult callback) {
        if (groupIds == null || groupIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return null;
        }
        List<String> limited = groupIds.size() > 30 ? groupIds.subList(0, 30) : groupIds;

        return tasksRef()
                .whereIn("groupId", limited)
                .whereEqualTo("assignedToUid", userId)
                .orderBy("deadline", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(toList(snapshots));
                });
    }

    /**
     * Hapus semua task milik satu grup sekaligus, lewat WriteBatch supaya
     * atomik (semua berhasil atau semua gagal, tidak setengah-setengah).
     * Dipakai saat sebuah grup dihapus, supaya tidak ada task "yatim" yang
     * groupId-nya mengarah ke dokumen grup yang sudah tidak ada.
     */
    public void deleteTasksByGroupId(String groupId, OnSimpleResult callback) {
        tasksRef().whereEqualTo("groupId", groupId).get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private List<Task> toList(QuerySnapshot snapshots) {
        List<Task> tasks = new ArrayList<>();
        if (snapshots != null) {
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Task t = doc.toObject(Task.class);
                if (t != null) {
                    t.setId(doc.getId());
                    tasks.add(t);
                }
            }
        }
        return tasks;
    }
}
