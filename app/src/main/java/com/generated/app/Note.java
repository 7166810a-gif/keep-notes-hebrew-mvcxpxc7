package com.generated.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Note {
    private long id;
    private String title;
    private String content;
    private String folder;
    private String colorHex;
    private boolean isPinned;
    private String imagePath;
    private String audioPath;
    private boolean isChecklist;
    private String checklistJson;
    private long timestamp;

    public static class ChecklistItem {
        public String text;
        public boolean isChecked;

        public ChecklistItem(String text, boolean isChecked) {
            this.text = text;
            this.isChecked = isChecked;
        }
    }

    public Note() {
        this.title = "";
        this.content = "";
        this.folder = "אישי";
        this.colorHex = "#FFFFFF";
        this.isPinned = false;
        this.imagePath = "";
        this.audioPath = "";
        this.isChecklist = false;
        this.checklistJson = "[]";
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content == null ? "" : content; }
    public void setContent(String content) { this.content = content; }

    public String getFolder() { return folder == null ? "אישי" : folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public String getColorHex() { return (colorHex == null || colorHex.isEmpty()) ? "#FFFFFF" : colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public String getImagePath() { return imagePath == null ? "" : imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getAudioPath() { return audioPath == null ? "" : audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }

    public boolean isChecklist() { return isChecklist; }
    public void setChecklist(boolean checklist) { isChecklist = checklist; }

    public String getChecklistJson() { return checklistJson == null ? "[]" : checklistJson; }
    public void setChecklistJson(String checklistJson) { this.checklistJson = checklistJson; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public List<ChecklistItem> getChecklistItems() {
        List<ChecklistItem> list = new ArrayList<>();
        if (checklistJson == null || checklistJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(checklistJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new ChecklistItem(obj.optString("text", ""), obj.optBoolean("checked", false)));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    public void setChecklistItems(List<ChecklistItem> list) {
        JSONArray arr = new JSONArray();
        for (ChecklistItem item : list) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("checked", item.isChecked);
                arr.put(obj);
            } catch (JSONException ignored) {}
        }
        this.checklistJson = arr.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("content", content);
        obj.put("folder", folder);
        obj.put("colorHex", colorHex);
        obj.put("isPinned", isPinned);
        obj.put("imagePath", imagePath);
        obj.put("audioPath", audioPath);
        obj.put("isChecklist", isChecklist);
        obj.put("checklistJson", checklistJson);
        obj.put("timestamp", timestamp);
        return obj;
    }

    public static Note fromJson(JSONObject obj) {
        Note note = new Note();
        note.setId(obj.optLong("id", 0));
        note.setTitle(obj.optString("title", ""));
        note.setContent(obj.optString("content", ""));
        note.setFolder(obj.optString("folder", "אישי"));
        note.setColorHex(obj.optString("colorHex", "#FFFFFF"));
        note.setPinned(obj.optBoolean("isPinned", false));
        note.setImagePath(obj.optString("imagePath", ""));
        note.setAudioPath(obj.optString("audioPath", ""));
        note.setChecklist(obj.optBoolean("isChecklist", false));
        note.setChecklistJson(obj.optString("checklistJson", "[]"));
        note.setTimestamp(obj.optLong("timestamp", System.currentTimeMillis()));
        return note;
    }
}
