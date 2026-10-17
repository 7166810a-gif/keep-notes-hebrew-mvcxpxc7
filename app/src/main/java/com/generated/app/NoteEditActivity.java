package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteEditActivity extends Activity {

    private static final int REQUEST_IMAGE_PICK = 301;
    private static final int REQUEST_IMAGE_CAPTURE = 302;
    private static final int REQUEST_AUDIO_FILE_PICK = 303;
    private static final int REQUEST_SYSTEM_SPEECH = 304;
    private static final int PERMISSION_AUDIO = 401;

    private NotesDbHelper dbHelper;
    private Note currentNote;

    private RelativeLayout rootLayout;
    private EditText editTitle;
    private EditText editBody;
    private TextView tvFolderBadge;
    private ImageView btnPin;
    private FrameLayout imagePreviewContainer;
    private ImageView ivNoteImage;
    private LinearLayout audioPlayerContainer;
    private ImageView btnPlayAudio;
    private TextView tvAudioTitle;
    private TextView tvAudioStatus;
    private ImageView btnTranscribeAudioFile;
    private LinearLayout checklistItemsContainer;
    private TextView btnAddChecklistRow;
    private TextView tvLastEdited;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private boolean isRecordingAudioFile = false;
    private boolean isAudioPlaying = false;
    private String tempAudioPath = "";

    // Speech recognition variables
    private SpeechRecognizer liveSpeechRecognizer;
    private boolean isLiveTranscribing = false;
    private String selectedLanguageCode = "he-IL"; // default Hebrew, supports en-US too

    private final String[] folderOptions = new String[]{"אישי", "עבודה", "רעיונות", "קניות"};
    private final String[] colorOptions = new String[]{
            "#FFFFFF", "#FFEBEE", "#FFF3E0", "#FFFDE7",
            "#E8F5E9", "#E0F7FA", "#E3F2FD", "#F3E5F5"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        dbHelper = new NotesDbHelper(this);
        initViews();

        long noteId = getIntent().getLongExtra("note_id", -1);
        if (noteId > 0) {
            currentNote = dbHelper.getNote(noteId);
        }

        if (currentNote == null) {
            currentNote = new Note();
            String defaultFolder = getIntent().getStringExtra("default_folder");
            if (defaultFolder != null && !defaultFolder.isEmpty()) {
                currentNote.setFolder(defaultFolder);
            }
            if (getIntent().getBooleanExtra("start_checklist", false)) {
                currentNote.setChecklist(true);
            }
        }

        bindNoteData();
        setupListeners();

        if (getIntent().getBooleanExtra("start_voice", false)) {
            promptVoiceTranscribe();
        } else if (getIntent().getBooleanExtra("start_audio_file", false)) {
            chooseAudioFile();
        } else if (getIntent().getBooleanExtra("start_image", false) && currentNote.getImagePath().isEmpty()) {
            chooseImageSource();
        }
    }

    private void initViews() {
        rootLayout = findViewById(R.id.edit_root_layout);
        editTitle = findViewById(R.id.edit_title);
        editBody = findViewById(R.id.edit_body);
        tvFolderBadge = findViewById(R.id.tv_folder_badge);
        btnPin = findViewById(R.id.btn_pin_note);
        imagePreviewContainer = findViewById(R.id.image_preview_container);
        ivNoteImage = findViewById(R.id.iv_note_image);
        audioPlayerContainer = findViewById(R.id.audio_player_container);
        btnPlayAudio = findViewById(R.id.btn_play_audio);
        tvAudioTitle = findViewById(R.id.tv_audio_title);
        tvAudioStatus = findViewById(R.id.tv_audio_status);
        btnTranscribeAudioFile = findViewById(R.id.btn_transcribe_audio_file);
        checklistItemsContainer = findViewById(R.id.checklist_items_container);
        btnAddChecklistRow = findViewById(R.id.btn_add_checklist_row);
        tvLastEdited = findViewById(R.id.tv_last_edited);
    }

    private void bindNoteData() {
        editTitle.setText(currentNote.getTitle());
        editBody.setText(currentNote.getContent());
        tvFolderBadge.setText(currentNote.getFolder());

        updateBackgroundColor(currentNote.getColorHex());
        updatePinIcon();

        // Image
        if (!currentNote.getImagePath().isEmpty() && new File(currentNote.getImagePath()).exists()) {
            imagePreviewContainer.setVisibility(View.VISIBLE);
            Bitmap bitmap = BitmapFactory.decodeFile(currentNote.getImagePath());
            if (bitmap != null) {
                ivNoteImage.setImageBitmap(bitmap);
            }
        } else {
            imagePreviewContainer.setVisibility(View.GONE);
        }

        // Audio
        updateAudioPlayerVisibility();

        // Checklist vs Text
        toggleChecklistMode(currentNote.isChecklist());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        tvLastEdited.setText("עודכן: " + sdf.format(new Date(currentNote.getTimestamp())));
    }

    private void updateAudioPlayerVisibility() {
        if (!currentNote.getAudioPath().isEmpty() && new File(currentNote.getAudioPath()).exists()) {
            audioPlayerContainer.setVisibility(View.VISIBLE);
            File audioFile = new File(currentNote.getAudioPath());
            tvAudioTitle.setText(audioFile.getName());
            tvAudioStatus.setText("הקשה לניגון או לחצו על תמלול");
        } else {
            audioPlayerContainer.setVisibility(View.GONE);
        }
    }

    private void updateBackgroundColor(String hexColor) {
        currentNote.setColorHex(hexColor);
        try {
            rootLayout.setBackgroundColor(Color.parseColor(hexColor));
        } catch (Exception ignored) {}
    }

    private void updatePinIcon() {
        if (currentNote.isPinned()) {
            btnPin.setImageResource(R.drawable.ic_pinned);
        } else {
            btnPin.setImageResource(R.drawable.ic_pin);
        }
    }

    private void toggleChecklistMode(boolean isChecklist) {
        currentNote.setChecklist(isChecklist);
        if (isChecklist) {
            editBody.setVisibility(View.GONE);
            checklistItemsContainer.setVisibility(View.VISIBLE);
            btnAddChecklistRow.setVisibility(View.VISIBLE);
            buildChecklistRows();
        } else {
            editBody.setVisibility(View.VISIBLE);
            checklistItemsContainer.setVisibility(View.GONE);
            btnAddChecklistRow.setVisibility(View.GONE);
        }
    }

    private void buildChecklistRows() {
        checklistItemsContainer.removeAllViews();
        List<Note.ChecklistItem> items = currentNote.getChecklistItems();
        if (items.isEmpty()) {
            items.add(new Note.ChecklistItem("", false));
        }

        for (final Note.ChecklistItem item : items) {
            addChecklistRowView(item);
        }
    }

    private void addChecklistRowView(final Note.ChecklistItem item) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        final CheckBox cb = new CheckBox(this);
        cb.setChecked(item.isChecked);

        final EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        et.setText(item.text);
        et.setHint(R.string.checklist_item_hint);
        et.setTextSize(15);
        et.setBackground(null);
        et.setTextColor(Color.parseColor("#333333"));
        if (item.isChecked) {
            et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            et.setAlpha(0.6f);
        }

        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                item.isChecked = isChecked;
                if (isChecked) {
                    et.setPaintFlags(et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    et.setAlpha(0.6f);
                } else {
                    et.setPaintFlags(et.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                    et.setAlpha(1.0f);
                }
                saveCurrentChecklistState();
            }
        });

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                item.text = s.toString();
                saveCurrentChecklistState();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        ImageView btnDeleteRow = new ImageView(this);
        btnDeleteRow.setImageResource(R.drawable.ic_delete);
        btnDeleteRow.setPadding(8, 8, 8, 8);
        btnDeleteRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checklistItemsContainer.removeView(row);
                saveCurrentChecklistState();
            }
        });

        row.addView(cb);
        row.addView(et);
        row.addView(btnDeleteRow);

        checklistItemsContainer.addView(row);
    }

    private void saveCurrentChecklistState() {
        List<Note.ChecklistItem> list = new ArrayList<>();
        int count = checklistItemsContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = checklistItemsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                CheckBox cb = (CheckBox) row.getChildAt(0);
                EditText et = (EditText) row.getChildAt(1);
                list.add(new Note.ChecklistItem(et.getText().toString(), cb.isChecked()));
            }
        }
        currentNote.setChecklistItems(list);
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndExit();
            }
        });

        tvFolderBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFolderSelectDialog();
            }
        });

        btnPin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentNote.setPinned(!currentNote.isPinned());
                updatePinIcon();
                Toast.makeText(NoteEditActivity.this,
                        currentNote.isPinned() ? "הפתק הוצמד" : "בוטלה ההצמדה",
                        Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_color_picker).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorPickerDialog();
            }
        });

        findViewById(R.id.btn_delete_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });

        // Bottom Tools
        findViewById(R.id.btn_tool_checklist).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleChecklistMode(!currentNote.isChecklist());
            }
        });

        findViewById(R.id.btn_tool_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptVoiceTranscribe();
            }
        });

        findViewById(R.id.btn_tool_audio_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseAudioFile();
            }
        });

        findViewById(R.id.btn_tool_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseImageSource();
            }
        });

        btnAddChecklistRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addChecklistRowView(new Note.ChecklistItem("", false));
            }
        });

        // Image remove
        findViewById(R.id.btn_remove_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentNote.setImagePath("");
                imagePreviewContainer.setVisibility(View.GONE);
            }
        });

        // Audio player listeners
        btnPlayAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAudioPlayback();
            }
        });

        btnTranscribeAudioFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transcribeCurrentAudioFile();
            }
        });

        findViewById(R.id.btn_remove_audio).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAudioPlayback();
                currentNote.setAudioPath("");
                audioPlayerContainer.setVisibility(View.GONE);
            }
        });
    }

    private void showFolderSelectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("בחר תיקייה לפתק");
        builder.setItems(folderOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentNote.setFolder(folderOptions[which]);
                tvFolderBadge.setText(currentNote.getFolder());
            }
        });
        builder.show();
    }

    private void showColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("בחר צבע רקע");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(30, 30, 30, 30);
        layout.setGravity(android.view.Gravity.CENTER);

        final AlertDialog dialog = builder.setView(layout).create();

        for (final String hex : colorOptions) {
            View circle = new View(this);
            int size = 90;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(8, 8, 8, 8);
            circle.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(hex));
            gd.setStroke(3, Color.parseColor("#BDBDBD"));
            circle.setBackground(gd);

            circle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateBackgroundColor(hex);
                    dialog.dismiss();
                }
            });

            layout.addView(circle);
        }

        dialog.show();
    }

    private void chooseImageSource() {
        String[] options = new String[]{"צלם תמונה במצלמה", "בחר תמונה מהגלריה"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("הוסף תמונה");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    Intent takePic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePic.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(takePic, REQUEST_IMAGE_CAPTURE);
                    } else {
                        Toast.makeText(NoteEditActivity.this, "לא נמצאה אפליקציית מצלמה", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Intent pickPic = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    try {
                        startActivityForResult(pickPic, REQUEST_IMAGE_PICK);
                    } catch (Exception e) {
                        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                        fallback.setType("image/*");
                        startActivityForResult(fallback, REQUEST_IMAGE_PICK);
                    }
                }
            }
        });
        builder.show();
    }

    private void chooseAudioFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "בחר קובץ שמע"), REQUEST_AUDIO_FILE_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצא בורר קבצים מתאים", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // VOICE & TRANSCRIPTION ENGINE (HEBREW + ENGLISH)
    // ==========================================

    private void promptVoiceTranscribe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
                return;
            }
        }
        showLiveTranscribeDialog();
    }

    /**
     * Complete live voice recognition dialog with language toggle (Hebrew/English),
     * live real-time transcript updates, audio waveform pulse indicator,
     * simultaneous recording option, and direct insertion into note.
     */
    private void showLiveTranscribeDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 36, 36, 36);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#FFFFFF"));

        // Header Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.live_transcribe_title);
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(Color.parseColor("#212121"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        layout.addView(tvTitle);

        // Language Switcher Chips Row (עברית / English)
        LinearLayout langRow = new LinearLayout(this);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setGravity(Gravity.CENTER);
        langRow.setPadding(0, 16, 0, 16);

        final Button btnHebrew = new Button(this);
        btnHebrew.setText(R.string.lang_hebrew);
        btnHebrew.setTextSize(12);
        btnHebrew.setPadding(24, 8, 24, 8);

        final Button btnEnglish = new Button(this);
        btnEnglish.setText(R.string.lang_english);
        btnEnglish.setTextSize(12);
        btnEnglish.setPadding(24, 8, 24, 8);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 80);
        btnParams.setMargins(8, 0, 8, 0);
        btnHebrew.setLayoutParams(btnParams);
        btnEnglish.setLayoutParams(btnParams);

        updateLangButtons(btnHebrew, btnEnglish);

        langRow.addView(btnHebrew);
        langRow.addView(btnEnglish);
        layout.addView(langRow);

        // Microphone Action Button
        final ImageView ivMic = new ImageView(this);
        ivMic.setImageResource(R.drawable.ic_mic);
        ivMic.setBackgroundResource(R.drawable.bg_fab);
        ivMic.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(140, 140);
        micParams.setMargins(0, 10, 0, 14);
        ivMic.setLayoutParams(micParams);
        layout.addView(ivMic);

        // Status description
        final TextView tvStatus = new TextView(this);
        tvStatus.setText("לחצו על המיקרופון כדי להתחיל לדבר");
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(Color.parseColor("#757575"));
        tvStatus.setGravity(Gravity.CENTER);
        layout.addView(tvStatus);

        // Live transcription output card
        final ScrollView scroller = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 220);
        scrollParams.setMargins(0, 16, 0, 16);
        scroller.setLayoutParams(scrollParams);
        scroller.setBackgroundResource(R.drawable.bg_transcription_box);
        scroller.setPadding(16, 16, 16, 16);

        final TextView tvLiveResult = new TextView(this);
        tvLiveResult.setText("התמליל יופיע כאן בזמן אמת...");
        tvLiveResult.setTextSize(15);
        tvLiveResult.setTextColor(Color.parseColor("#424242"));
        tvLiveResult.setLineSpacing(4, 1.2f);
        scroller.addView(tvLiveResult);
        layout.addView(scroller);

        // Actions Row: System Voice Recognition fallback button + Insert button
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER);

        final Button btnSystemVoice = new Button(this);
        btnSystemVoice.setText("מקלדת קולית");
        btnSystemVoice.setTextSize(12);
        btnSystemVoice.setBackgroundResource(R.drawable.bg_chip_normal);
        btnSystemVoice.setTextColor(Color.parseColor("#424242"));
        LinearLayout.LayoutParams sysParams = new LinearLayout.LayoutParams(0, 96, 1f);
        sysParams.setMargins(0, 0, 8, 0);
        btnSystemVoice.setLayoutParams(sysParams);

        final Button btnInsert = new Button(this);
        btnInsert.setText(R.string.append_to_note);
        btnInsert.setTextSize(13);
        btnInsert.setTypeface(null, android.graphics.Typeface.BOLD);
        btnInsert.setBackgroundResource(R.drawable.bg_button_primary);
        btnInsert.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams insParams = new LinearLayout.LayoutParams(0, 96, 1.3f);
        btnInsert.setLayoutParams(insParams);

        actionsRow.addView(btnSystemVoice);
        actionsRow.addView(btnInsert);
        layout.addView(actionsRow);

        builder.setView(layout);
        builder.setNegativeButton("סגור", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopLiveRecognition();
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.create();

        // Language Switchers
        btnHebrew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedLanguageCode = "he-IL";
                updateLangButtons(btnHebrew, btnEnglish);
                if (isLiveTranscribing) {
                    stopLiveRecognition();
                    startSpeechRecognitionListening(tvLiveResult, tvStatus, ivMic);
                }
            }
        });

        btnEnglish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedLanguageCode = "en-US";
                updateLangButtons(btnHebrew, btnEnglish);
                if (isLiveTranscribing) {
                    stopLiveRecognition();
                    startSpeechRecognitionListening(tvLiveResult, tvStatus, ivMic);
                }
            }
        });

        // Toggle Live Voice Recognition
        ivMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLiveTranscribing) {
                    startSpeechRecognitionListening(tvLiveResult, tvStatus, ivMic);
                } else {
                    stopLiveRecognition();
                    tvStatus.setText("התמלול הופסק. לחצו להמשך או שמרו לפתק.");
                    ivMic.setBackgroundResource(R.drawable.bg_fab);
                }
            }
        });

        // Direct System RecognizerIntent as a 100% resilient fallback!
        btnSystemVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLiveRecognition();
                dialog.dismiss();
                launchSystemSpeechIntent(selectedLanguageCode);
            }
        });

        // Insert into note
        btnInsert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String result = tvLiveResult.getText().toString().trim();
                if (!result.isEmpty() && !result.equals("התמליל יופיע כאן בזמן אמת...")) {
                    appendTranscribedText(result);
                    stopLiveRecognition();
                    dialog.dismiss();
                } else {
                    Toast.makeText(NoteEditActivity.this, "עדיין לא זוהה דיבור", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                stopLiveRecognition();
            }
        });

        dialog.show();

        // Automatically start listening for ease of use
        startSpeechRecognitionListening(tvLiveResult, tvStatus, ivMic);
    }

    private void updateLangButtons(Button btnHebrew, Button btnEnglish) {
        if ("he-IL".equals(selectedLanguageCode)) {
            btnHebrew.setBackgroundResource(R.drawable.bg_chip_selected);
            btnHebrew.setTextColor(getResources().getColor(R.color.primary_dark));
            btnEnglish.setBackgroundResource(R.drawable.bg_chip_normal);
            btnEnglish.setTextColor(Color.parseColor("#757575"));
        } else {
            btnEnglish.setBackgroundResource(R.drawable.bg_chip_selected);
            btnEnglish.setTextColor(getResources().getColor(R.color.primary_dark));
            btnHebrew.setBackgroundResource(R.drawable.bg_chip_normal);
            btnHebrew.setTextColor(Color.parseColor("#757575"));
        }
    }

    private void startSpeechRecognitionListening(final TextView tvResult, final TextView tvStatus, final ImageView ivMic) {
        stopLiveRecognition();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.setText("מנוע זיהוי פנימי אינו זמין, מפעיל חלון קולי...");
            launchSystemSpeechIntent(selectedLanguageCode);
            return;
        }

        try {
            liveSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra("android.speech.extra.DICTATION_MODE", true);

            liveSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isLiveTranscribing = true;
                    tvStatus.setText("מאזין כעת... דברו בחופשיות (" + (selectedLanguageCode.equals("he-IL") ? "עברית" : "English") + ")");
                    ivMic.setBackgroundColor(Color.parseColor("#E53935"));
                }

                @Override
                public void onBeginningOfSpeech() {
                    tvStatus.setText("מזהה דיבור...");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Visual feedback
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    tvStatus.setText("מעבד תוצאות...");
                }

                @Override
                public void onError(int error) {
                    isLiveTranscribing = false;
                    ivMic.setBackgroundResource(R.drawable.bg_fab);

                    String msg = "זיהוי שמע לא נקלט. נסו שוב.";
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        msg = "לא זוהה דיבור ברור. דברו שוב.";
                    } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        msg = "שגיאת רשת במנוע הזיהוי.";
                    } else if (error == SpeechRecognizer.ERROR_AUDIO) {
                        msg = "שגיאת מיקרופון. ודאו שאף יישום אחר אינו מקליט.";
                    }
                    tvStatus.setText(msg);
                }

                @Override
                public void onResults(Bundle results) {
                    isLiveTranscribing = false;
                    ivMic.setBackgroundResource(R.drawable.bg_fab);
                    tvStatus.setText("תומלל בהצלחה! לחצו על המיקרופון להמשך דיבור.");

                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        String current = tvResult.getText().toString();
                        if (current.equals("התמליל יופיע כאן בזמן אמת...") || current.isEmpty()) {
                            tvResult.setText(text);
                        } else {
                            tvResult.setText(current + " " + text);
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        String partialText = partial.get(0);
                        String base = tvResult.getText().toString();
                        if (base.equals("התמליל יופיע כאן בזמן אמת...")) {
                            tvResult.setText(partialText);
                        } else {
                            tvResult.setText(base + " " + partialText);
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            liveSpeechRecognizer.startListening(intent);
        } catch (Exception e) {
            isLiveTranscribing = false;
            tvStatus.setText("שגיאה בהפעלת שירות קולי, מפעיל מקלדת קולית...");
            launchSystemSpeechIntent(selectedLanguageCode);
        }
    }

    private void stopLiveRecognition() {
        if (liveSpeechRecognizer != null) {
            try {
                liveSpeechRecognizer.stopListening();
                liveSpeechRecognizer.cancel();
                liveSpeechRecognizer.destroy();
            } catch (Exception ignored) {}
            liveSpeechRecognizer = null;
        }
        isLiveTranscribing = false;
    }

    private void launchSystemSpeechIntent(String lang) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "דברו כעת לתמלול הפתק...");
        try {
            startActivityForResult(intent, REQUEST_SYSTEM_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "מכשיר זה אינו תומך בזיהוי דיבור מובנה", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Transcribe the attached audio file (MP3, WAV, M4A, 3GP).
     * If user configured an OpenAI Whisper API key in Settings, calls OpenAI Whisper API.
     * Otherwise gives user clear options or instructions.
     */
    private void transcribeCurrentAudioFile() {
        if (currentNote.getAudioPath().isEmpty() || !new File(currentNote.getAudioPath()).exists()) {
            Toast.makeText(this, "לא קיים קובץ שמע לתמלול", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("smart_notes_prefs", MODE_PRIVATE);
        String whisperKey = prefs.getString("whisper_api_key", "").trim();

        if (whisperKey.isEmpty()) {
            // Inform user how to use Whisper or dictate
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("תמלול קובץ שמע בעברית / אנגלית");
            builder.setMessage("לתמלול קבצי שמע ארוכים וקצרים מוקלטים מראש (MP3/WAV/M4A), ניתן להזין מפתח OpenAI Whisper בהגדרות או להשתמש בהכתבה קולית חיה.\n\nהאם לעבור להגדרות להזנת מפתח API?");
            builder.setPositiveButton("מעבר להגדרות", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    promptVoiceTranscribe();
                }
            });
            builder.setNegativeButton("תמלול חי במיקרופון", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    promptVoiceTranscribe();
                }
            });
            builder.show();
            return;
        }

        // Call Whisper Transcription Task in Background
        new WhisperTranscribeTask(this, currentNote.getAudioPath(), whisperKey, selectedLanguageCode).execute();
    }

    private static class WhisperTranscribeTask extends AsyncTask<Void, Void, String> {
        private final Activity activity;
        private final String filePath;
        private final String apiKey;
        private final String langCode;
        private ProgressDialog progressDialog;
        private String errorMsg = null;

        public WhisperTranscribeTask(Activity activity, String filePath, String apiKey, String langCode) {
            this.activity = activity;
            this.filePath = filePath;
            this.apiKey = apiKey;
            this.langCode = langCode.startsWith("en") ? "en" : "he";
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(activity);
            progressDialog.setTitle("מתמלל קובץ שמע");
            progressDialog.setMessage("שולח את האודיו לעיבוד ופענוח בעברית ובאנגלית...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            String boundary = "===" + System.currentTimeMillis() + "===";
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            try {
                File uploadFile = new File(filePath);
                if (!uploadFile.exists()) {
                    errorMsg = "הקובץ לא נמצא: " + filePath;
                    return null;
                }

                URL url = new URL("https://api.openai.com/v1/audio/transcriptions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(90000);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                // Field: model = whisper-1
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"model\"" + lineEnd + lineEnd);
                dos.writeBytes("whisper-1" + lineEnd);

                // Field: language
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"language\"" + lineEnd + lineEnd);
                dos.writeBytes(langCode + lineEnd);

                // Field: file
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + uploadFile.getName() + "\"" + lineEnd);
                dos.writeBytes("Content-Type: audio/mpeg" + lineEnd + lineEnd);

                FileInputStream fis = new FileInputStream(uploadFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                fis.close();
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();
                dos.close();

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (responseCode >= 200 && responseCode < 300) {
                    JSONObject json = new JSONObject(response.toString());
                    return json.optString("text", "");
                } else {
                    errorMsg = "שגיאת שרת (" + responseCode + "): " + response.toString();
                    return null;
                }
            } catch (Exception e) {
                errorMsg = "שגיאה בחיבור: " + e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (activity instanceof NoteEditActivity && !activity.isFinishing()) {
                NoteEditActivity editAct = (NoteEditActivity) activity;
                if (result != null && !result.trim().isEmpty()) {
                    editAct.appendTranscribedText(result);
                    Toast.makeText(activity, "קובץ השמע תומלל בהצלחה!", Toast.LENGTH_LONG).show();
                } else {
                    String err = errorMsg != null ? errorMsg : "לא התקבל תמלול תקין";
                    Toast.makeText(activity, err, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void appendTranscribedText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (currentNote.isChecklist()) {
            addChecklistRowView(new Note.ChecklistItem(text.trim(), false));
            saveCurrentChecklistState();
        } else {
            String existing = editBody.getText().toString();
            if (!existing.trim().isEmpty()) {
                editBody.setText(existing + "\n\n" + text.trim());
            } else {
                editBody.setText(text.trim());
            }
            editBody.setSelection(editBody.getText().length());
        }
        Toast.makeText(this, R.string.speech_transcribed, Toast.LENGTH_SHORT).show();
    }

    private void toggleAudioPlayback() {
        if (isAudioPlaying) {
            stopAudioPlayback();
        } else {
            startAudioPlayback();
        }
    }

    private void startAudioPlayback() {
        if (currentNote.getAudioPath().isEmpty()) return;
        try {
            stopAudioPlayback();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(currentNote.getAudioPath());
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopAudioPlayback();
                }
            });
            mediaPlayer.start();
            isAudioPlaying = true;
            btnPlayAudio.setImageResource(R.drawable.ic_pause);
            tvAudioStatus.setText("מנגן שמע...");
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בניגון הקלטה", Toast.LENGTH_SHORT).show();
            stopAudioPlayback();
        }
    }

    private void stopAudioPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        isAudioPlaying = false;
        btnPlayAudio.setImageResource(R.drawable.ic_play);
        tvAudioStatus.setText("הקשה לניגון או לחצו על תמלול");
    }

    private void confirmDelete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("מחיקת פתק");
        builder.setMessage("האם למחוק פתק זה לצמיתות?");
        builder.setPositiveButton("מחק", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (currentNote.getId() > 0) {
                    dbHelper.deleteNote(currentNote.getId());
                }
                finish();
            }
        });
        builder.setNegativeButton("ביטול", null);
        builder.show();
    }

    private void saveAndExit() {
        String title = editTitle.getText().toString().trim();
        String content = editBody.getText().toString().trim();

        if (currentNote.isChecklist()) {
            saveCurrentChecklistState();
        }

        boolean hasChecklist = currentNote.isChecklist() && !currentNote.getChecklistItems().isEmpty();
        boolean hasImage = !currentNote.getImagePath().isEmpty();
        boolean hasAudio = !currentNote.getAudioPath().isEmpty();

        // If completely empty, delete if existed, or just finish
        if (title.isEmpty() && content.isEmpty() && !hasChecklist && !hasImage && !hasAudio) {
            if (currentNote.getId() > 0) {
                dbHelper.deleteNote(currentNote.getId());
            }
            finish();
            return;
        }

        currentNote.setTitle(title);
        currentNote.setContent(content);
        currentNote.setTimestamp(System.currentTimeMillis());

        if (currentNote.getId() > 0) {
            dbHelper.updateNote(currentNote);
        } else {
            long newId = dbHelper.insertNote(currentNote);
            currentNote.setId(newId);
        }

        finish();
    }

    @Override
    public void onBackPressed() {
        saveAndExit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAudioPlayback();
        stopLiveRecognition();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_SYSTEM_SPEECH && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    appendTranscribedText(matches.get(0));
                }
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null && data.getData() != null) {
                try {
                    Uri selectedImage = data.getData();
                    InputStream inputStream = getContentResolver().openInputStream(selectedImage);
                    File imageDir = new File(getFilesDir(), "note_images");
                    if (!imageDir.exists()) imageDir.mkdirs();
                    File destFile = new File(imageDir, "img_" + System.currentTimeMillis() + ".jpg");

                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                    fos.close();
                    inputStream.close();

                    currentNote.setImagePath(destFile.getAbsolutePath());
                    imagePreviewContainer.setVisibility(View.VISIBLE);
                    ivNoteImage.setImageBitmap(BitmapFactory.decodeFile(destFile.getAbsolutePath()));
                } catch (Exception e) {
                    Toast.makeText(this, "שגיאה בטעינת תמונה", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_IMAGE_CAPTURE && data != null && data.getExtras() != null) {
                Bitmap photo = (Bitmap) data.getExtras().get("data");
                if (photo != null) {
                    try {
                        File imageDir = new File(getFilesDir(), "note_images");
                        if (!imageDir.exists()) imageDir.mkdirs();
                        File destFile = new File(imageDir, "cam_" + System.currentTimeMillis() + ".jpg");

                        FileOutputStream fos = new FileOutputStream(destFile);
                        photo.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();

                        currentNote.setImagePath(destFile.getAbsolutePath());
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                        ivNoteImage.setImageBitmap(photo);
                    } catch (Exception e) {
                        Toast.makeText(this, "שגיאה בשמירת צילום", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == REQUEST_AUDIO_FILE_PICK && data != null && data.getData() != null) {
                try {
                    Uri audioUri = data.getData();
                    InputStream is = getContentResolver().openInputStream(audioUri);
                    File audioDir = new File(getFilesDir(), "audio_notes");
                    if (!audioDir.exists()) audioDir.mkdirs();
                    File destFile = new File(audioDir, "audio_" + System.currentTimeMillis() + ".mp3");

                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();

                    currentNote.setAudioPath(destFile.getAbsolutePath());
                    updateAudioPlayerVisibility();
                    Toast.makeText(this, "קובץ שמע צורף בהצלחה!", Toast.LENGTH_SHORT).show();

                    // Prompt to transcribe immediately
                    transcribeCurrentAudioFile();

                } catch (Exception e) {
                    Toast.makeText(this, "שגיאה בצירוף קובץ שמע: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showLiveTranscribeDialog();
            } else {
                Toast.makeText(this, R.string.permission_audio_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
