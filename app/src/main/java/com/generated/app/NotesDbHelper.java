package com.generated.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class NotesDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "smart_notes.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NOTES = "notes";
    public static final String COL_ID = "_id";
    public static final String COL_TITLE = "title";
    public static final String COL_CONTENT = "content";
    public static final String COL_FOLDER = "folder";
    public static final String COL_COLOR = "color";
    public static final String COL_PINNED = "is_pinned";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_AUDIO_PATH = "audio_path";
    public static final String COL_IS_CHECKLIST = "is_checklist";
    public static final String COL_CHECKLIST_JSON = "checklist_json";
    public static final String COL_TIMESTAMP = "timestamp";

    public NotesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NOTES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT, "
                + COL_CONTENT + " TEXT, "
                + COL_FOLDER + " TEXT, "
                + COL_COLOR + " TEXT, "
                + COL_PINNED + " INTEGER DEFAULT 0, "
                + COL_IMAGE_PATH + " TEXT, "
                + COL_AUDIO_PATH + " TEXT, "
                + COL_IS_CHECKLIST + " INTEGER DEFAULT 0, "
                + COL_CHECKLIST_JSON + " TEXT, "
                + COL_TIMESTAMP + " INTEGER"
                + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }

    public long insertNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = noteToValues(note);
        long id = db.insert(TABLE_NOTES, null, cv);
        note.setId(id);
        return id;
    }

    public int updateNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = noteToValues(note);
        return db.update(TABLE_NOTES, cv, COL_ID + "=?", new String[]{String.valueOf(note.getId())});
    }

    public void deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOTES, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Note getNote(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NOTES, null, COL_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        Note note = null;
        if (cursor != null && cursor.moveToFirst()) {
            note = cursorToNote(cursor);
            cursor.close();
        }
        return note;
    }

    public List<Note> getNotes(String filterFolder, String searchQuery) {
        List<Note> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        StringBuilder selection = new StringBuilder();
        List<String> argsList = new ArrayList<>();

        if (filterFolder != null && !filterFolder.isEmpty() && !filterFolder.equals("הכל")) {
            selection.append(COL_FOLDER).append(" = ? ");
            argsList.add(filterFolder);
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("(").append(COL_TITLE).append(" LIKE ? OR ")
                    .append(COL_CONTENT).append(" LIKE ? OR ")
                    .append(COL_CHECKLIST_JSON).append(" LIKE ?)");
            String queryArg = "%" + searchQuery.trim() + "%";
            argsList.add(queryArg);
            argsList.add(queryArg);
            argsList.add(queryArg);
        }

        String whereClause = selection.length() > 0 ? selection.toString() : null;
        String[] whereArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);

        String orderBy = COL_PINNED + " DESC, " + COL_TIMESTAMP + " DESC";
        Cursor cursor = db.query(TABLE_NOTES, null, whereClause, whereArgs, null, null, orderBy);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToNote(cursor));
            }
            cursor.close();
        }
        return list;
    }

    private ContentValues noteToValues(Note note) {
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, note.getTitle());
        cv.put(COL_CONTENT, note.getContent());
        cv.put(COL_FOLDER, note.getFolder());
        cv.put(COL_COLOR, note.getColorHex());
        cv.put(COL_PINNED, note.isPinned() ? 1 : 0);
        cv.put(COL_IMAGE_PATH, note.getImagePath());
        cv.put(COL_AUDIO_PATH, note.getAudioPath());
        cv.put(COL_IS_CHECKLIST, note.isChecklist() ? 1 : 0);
        cv.put(COL_CHECKLIST_JSON, note.getChecklistJson());
        cv.put(COL_TIMESTAMP, note.getTimestamp());
        return cv;
    }

    private Note cursorToNote(Cursor cursor) {
        Note note = new Note();
        note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
        note.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)));
        note.setFolder(cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER)));
        note.setColorHex(cursor.getString(cursor.getColumnIndexOrThrow(COL_COLOR)));
        note.setPinned(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PINNED)) == 1);
        note.setImagePath(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)));
        note.setAudioPath(cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO_PATH)));
        note.setChecklist(cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_CHECKLIST)) == 1);
        note.setChecklistJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_CHECKLIST_JSON)));
        note.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)));
        return note;
    }
}
