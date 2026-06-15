package com.test.cutipdo2026;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class QueueManager {
    private static final String PREF_NAME = "LeaveRequestPrefs";
    private static final String KEY_QUEUE = "batchQueue";
    private SharedPreferences prefs;
    private Gson gson;

    public QueueManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveQueue(ArrayList<QueuedRequest> queue) {
        String json = gson.toJson(queue);
        prefs.edit().putString(KEY_QUEUE, json).apply();
    }

    public ArrayList<QueuedRequest> loadQueue() {
        String json = prefs.getString(KEY_QUEUE, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<QueuedRequest>>() {}.getType();
        return gson.fromJson(json, type);
    }
}