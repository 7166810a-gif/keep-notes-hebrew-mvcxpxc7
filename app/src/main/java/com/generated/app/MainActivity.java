package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_EDIT_NOTE = 101;
    private static final int REQUEST_PICK_MODEL_FILE = 202;

    private NotesDbHelper dbHelper;
    private LinearLayout pinnedNotesContainer;
    private LinearLayout otherNotesContainer;
    private TextView tvPinnedHeader;
    private TextView tvOtherHeader;
    private LinearLayout emptyStateView;
    private LinearLayout folderChipsContainer;
    private EditText editSearch;

    private final String[] defaultFolders = new String[]{"הכל", "אישי", "עבודה", "רעיונות", "קניות"};
    private String selectedFolder = "הכל";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new NotesDbHelper(this);

        initViews();
        setupFolders();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotesList();
    }

    private void initViews() {
        pinnedNotesContainer = findViewById(R.id.pinned_notes_container);
        otherNotesContainer = findViewById(R.id.other_notes_container);
        tvPinnedHeader = findViewById(R.id.tv_pinned_header);
        tvOtherHeader = findViewById(R.id.tv_other_header);
        emptyStateView = findViewById(R.id.empty_state_view);
        folderChipsContainer = findViewById(R.id.folder_chips_container);
        editSearch = findViewById(R.id.edit_search);
    }

    private void setupFolders() {
        folderChipsContainer.removeAllViews();
        for (final String folder : defaultFolders) {
            final TextView chip = new TextView(this);
            chip.setText(folder);
            chip.setTextSize(13);
            chip.setPadding(32, 12, 32, 12);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 16, 0);
            chip.setLayoutParams(params);

            updateChipStyle(chip, folder.equals(selectedFolder));

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFolder = folder;
                    setupFolders();
                    refreshNotesList();
                }
            });

            folderChipsContainer.addView(chip);
        }
    }

    private void updateChipStyle(TextView chip, boolean isSelected) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setTextColor(getResources().getColor(R.color.primary_dark));
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_normal);
            chip.setTextColor(Color.parseColor("#555555"));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void setupListeners() {
        // Quick input / FAB
        findViewById(R.id.btn_take_note_bar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(-1, false, false, false);
            }
        });

        findViewById(R.id.fab_new_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(-1, false, false, false);
            }
        });

        findViewById(R.id.btn_quick_checklist).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(-1, true, false, false);
            }
        });

        findViewById(R.id.btn_quick_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(-1, false, true, false);
            }
        });

        findViewById(R.id.btn_quick_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(-1, false, false, true);
            }
        });

        // Search text watcher
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                refreshNotesList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Settings Button
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });
    }

    private void openNoteEditor(long noteId, boolean startChecklist, boolean startVoice, boolean startImage) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        if (noteId > 0) {
            intent.putExtra("note_id", noteId);
        } else {
            intent.putExtra("start_checklist", startChecklist);
            intent.putExtra("start_voice", startVoice);
            intent.putExtra("start_image", startImage);
            if (!selectedFolder.equals("הכל")) {
                intent.putExtra("default_folder", selectedFolder);
            }
        }
        startActivityForResult(intent, REQUEST_EDIT_NOTE);
    }

    public void refreshNotesList() {
        pinnedNotesContainer.removeAllViews();
        otherNotesContainer.removeAllViews();

        List<Note> notes = dbHelper.getNotes(selectedFolder, currentSearchQuery);

        if (notes.isEmpty()) {
            emptyStateView.setVisibility(View.VISIBLE);
            tvPinnedHeader.setVisibility(View.GONE);
            tvOtherHeader.setVisibility(View.GONE);
            return;
        }

        emptyStateView.setVisibility(View.GONE);

        int pinnedCount = 0;
        int otherCount = 0;

        for (final Note note : notes) {
            View card = createNoteCardView(note);
            if (note.isPinned()) {
                pinnedNotesContainer.addView(card);
                pinnedCount++;
            } else {
                otherNotesContainer.addView(card);
                otherCount++;
            }
        }

        if (pinnedCount > 0) {
            tvPinnedHeader.setVisibility(View.VISIBLE);
            tvOtherHeader.setVisibility(otherCount > 0 ? View.VISIBLE : View.GONE);
        } else {
            tvPinnedHeader.setVisibility(View.GONE);
            tvOtherHeader.setVisibility(View.GONE);
        }
    }

    private View createNoteCardView(final Note note) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View card = inflater.inflate(R.layout.item_note_card, null);

        View wrapper = card.findViewById(R.id.card_wrapper);
        TextView tvFolder = card.findViewById(R.id.item_folder_tag);
        ImageView ivPin = card.findViewById(R.id.item_pin_icon);
        ImageView ivThumb = card.findViewById(R.id.item_image_thumbnail);
        TextView tvTitle = card.findViewById(R.id.item_title);
        TextView tvBody = card.findViewById(R.id.item_body);
        ImageView badgeAudio = card.findViewById(R.id.item_badge_audio);
        ImageView badgeChecklist = card.findViewById(R.id.item_badge_checklist);
        TextView tvDate = card.findViewById(R.id.item_date);

        // Apply background color
        try {
            int cardColor = Color.parseColor(note.getColorHex());
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(cardColor);
            gd.setCornerRadius(28f);
            gd.setStroke(2, Color.parseColor("#E0E0E0"));
            wrapper.setBackground(gd);
        } catch (Exception e) {
            wrapper.setBackgroundResource(R.drawable.bg_card_rounded);
        }

        tvFolder.setText(note.getFolder());
        ivPin.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

        // Thumbnail
        if (!note.getImagePath().isEmpty() && new File(note.getImagePath()).exists()) {
            ivThumb.setVisibility(View.VISIBLE);
            Bitmap bitmap = BitmapFactory.decodeFile(note.getImagePath());
            if (bitmap != null) {
                ivThumb.setImageBitmap(bitmap);
            }
        } else {
            ivThumb.setVisibility(View.GONE);
        }

        // Title & Body
        if (note.getTitle().isEmpty()) {
            tvTitle.setVisibility(View.GONE);
        } else {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(note.getTitle());
        }

        if (note.isChecklist()) {
            StringBuilder sb = new StringBuilder();
            List<Note.ChecklistItem> items = note.getChecklistItems();
            for (int i = 0; i < Math.min(items.size(), 4); i++) {
                Note.ChecklistItem item = items.get(i);
                sb.append(item.isChecked ? "☑ " : "☐ ").append(item.text).append("\n");
            }
            if (items.size() > 4) {
                sb.append("... ועוד ").append(items.size() - 4).append(" פריטים");
            }
            tvBody.setText(sb.toString().trim());
        } else {
            tvBody.setText(note.getContent());
        }

        if (tvTitle.getVisibility() == View.GONE && tvBody.getText().toString().isEmpty()) {
            tvBody.setVisibility(View.VISIBLE);
            tvBody.setText("פתק ללא תוכן");
        }

        // Badges
        badgeAudio.setVisibility(note.getAudioPath().isEmpty() ? View.GONE : View.VISIBLE);
        badgeChecklist.setVisibility(note.isChecklist() ? View.VISIBLE : View.GONE);

        // Formatted Date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        tvDate.setText(sdf.format(new Date(note.getTimestamp())));

        // Click to open note
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNoteEditor(note.getId(), false, false, false);
            }
        });

        // Long click to quick pin/delete options
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showQuickCardOptions(note);
                return true;
            }
        });

        return card;
    }

    private void showQuickCardOptions(final Note note) {
        String pinAction = note.isPinned() ? "בטל הצמדה לראש הרשימה" : "הצמד לראש הרשימה";
        String[] options = new String[]{pinAction, "מחק פתק"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(note.getTitle().isEmpty() ? "אפשרויות פתק" : note.getTitle());
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                note.setPinned(!note.isPinned());
                dbHelper.updateNote(note);
                refreshNotesList();
            } else if (which == 1) {
                dbHelper.deleteNote(note.getId());
                Toast.makeText(MainActivity.this, "הפתק נמחק", Toast.LENGTH_SHORT).show();
                refreshNotesList();
            }
        });
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();

        final SharedPreferences prefs = getSharedPreferences("smart_notes_prefs", MODE_PRIVATE);
        final TextView tvCurrentModel = dialogView.findViewById(R.id.tv_current_model);
        String savedModel = prefs.getString("custom_model_path", "");
        if (!savedModel.isEmpty()) {
            tvCurrentModel.setText("מודל מקומי נבחר: " + new File(savedModel).getName());
        }

        dialogView.findViewById(R.id.btn_pick_model).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, REQUEST_PICK_MODEL_FILE);
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "לא נמצא מנהל קבצים במכשיר", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Export JSON
        dialogView.findViewById(R.id.btn_export_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportNotesToJson();
                dialog.dismiss();
            }
        });

        // Import JSON
        dialogView.findViewById(R.id.btn_import_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importNotesFromJson();
                dialog.dismiss();
            }
        });

        dialogView.findViewById(R.id.btn_close_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void exportNotesToJson() {
        try {
            List<Note> allNotes = dbHelper.getNotes(null, null);
            JSONArray array = new JSONArray();
            for (Note n : allNotes) {
                array.put(n.toJson());
            }

            File exportFile = new File(getFilesDir(), "notes_backup.json");
            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(array.toString(2).getBytes());
            fos.close();

            Toast.makeText(this, "גיבוי יוצא בהצלחה ל: " + exportFile.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בייצוא: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importNotesFromJson() {
        try {
            File backupFile = new File(getFilesDir(), "notes_backup.json");
            if (!backupFile.exists()) {
                Toast.makeText(this, "לא נמצא קובץ גיבוי קיים", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(backupFile)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Note note = Note.fromJson(obj);
                dbHelper.insertNote(note);
            }

            refreshNotesList();
            Toast.makeText(this, "הנתונים יובאו בהצלחה!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בייבוא: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_NOTE) {
            refreshNotesList();
        } else if (requestCode == REQUEST_PICK_MODEL_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                SharedPreferences.Editor editor = getSharedPreferences("smart_notes_prefs", MODE_PRIVATE).edit();
                editor.putString("custom_model_path", uri.getPath());
                editor.apply();
                Toast.makeText(this, "מודל מקומי הוגדר בהצלחה!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
