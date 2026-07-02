package com.tj90.prioritytodo;

import android.accounts.Account;
import android.content.Context;

import com.google.android.gms.auth.GoogleAuthUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SyncManager {
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata";
    private static final int SCHEMA = 1;

    interface Callback {
        void onSynced(List<TodoTask> merged);

        void onError(Exception error);
    }

    private final Context context;
    private final TaskStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    SyncManager(Context context, TaskStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    void sync(Account account, Callback callback) {
        executor.execute(() -> {
            try {
                String token = GoogleAuthUtil.getToken(context, account, SCOPE);
                DriveClient drive = new DriveClient(token);
                List<TodoTask> local = store.loadAll();
                String fileId = drive.findFileId();

                List<TodoTask> merged;
                if (fileId == null) {
                    merged = local;
                    drive.create(serialize(merged));
                } else {
                    List<TodoTask> remote = deserialize(drive.download(fileId));
                    merged = TaskStore.merge(local, remote);
                    drive.update(fileId, serialize(merged));
                }
                store.persist(merged);

                List<TodoTask> visible = new ArrayList<>();
                for (TodoTask task : merged) {
                    if (!task.deleted) {
                        visible.add(task);
                    }
                }
                post(() -> callback.onSynced(visible));
            } catch (Exception error) {
                post(() -> callback.onError(error));
            }
        });
    }

    private String serialize(List<TodoTask> tasks) throws JSONException {
        JSONArray array = new JSONArray();
        for (TodoTask task : tasks) {
            array.put(task.toJson());
        }
        JSONObject root = new JSONObject();
        root.put("schema", SCHEMA);
        root.put("updatedAt", System.currentTimeMillis());
        root.put("tasks", array);
        return root.toString();
    }

    private List<TodoTask> deserialize(String raw) {
        List<TodoTask> tasks = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("tasks");
            if (array == null) {
                return tasks;
            }
            for (int i = 0; i < array.length(); i++) {
                tasks.add(TodoTask.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt/foreign remote file: treat as empty, local wins this round.
        }
        return tasks;
    }

    private void post(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }
}
