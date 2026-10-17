package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteEditActivity extends Activity {

    private static final int REQUEST_IMAGE_PICK = 301;
    private static final int REQUEST_IMAGE_CAPTURE = 302;
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
    private TextView tvAudioStatus;
    private LinearLayout checklistItemsContainer;
    private TextView btnAddChecklistRow;
    private TextView tvLastEdited;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private boolean isRecording = false;
    private boolean isAudioPlaying = false;
    private String tempAudioPath = "";
    private SpeechRecognizer speechRecognizer;

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

        if (getIntent().getBooleanExtra("start_voice", false) && currentNote.getAudioPath().isEmpty()) {
            promptVoiceRecording();
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
        tvAudioStatus = findViewById(R.id.tv_audio_status);
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
        if (!currentNote.getAudioPath().isEmpty() && new File(currentNote.getAudioPath()).exists()) {
            audioPlayerContainer.setVisibility(View.VISIBLE);
        } else {
            audioPlayerContainer.setVisibility(View.GONE);
        }

        // Checklist vs Text
        toggleChecklistMode(currentNote.isChecklist());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        tvLastEdited.setText("עודכן: " + sdf.format(new Date(currentNote.getTimestamp())));
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
                promptVoiceRecording();
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

    private void promptVoiceRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
                return;
            }
        }
        showVoiceRecordDialog();
    }

    private void showVoiceRecordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_settings, null); // reuse dialog container or programmatically build
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        layout.setGravity(android.view.Gravity.CENTER);

        final TextView tvStatus = new TextView(this);
        tvStatus.setText("הקלט הודעה קולית עם תמלול אוטומטי");
        tvStatus.setTextSize(16);
        tvStatus.setTextColor(Color.parseColor("#212121"));
        tvStatus.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvStatus);

        final ImageView btnMicToggle = new ImageView(this);
        btnMicToggle.setImageResource(R.drawable.ic_mic);
        btnMicToggle.setBackgroundResource(R.drawable.bg_fab);
        btnMicToggle.setPadding(28, 28, 28, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, 160);
        lp.setMargins(0, 36, 0, 36);
        btnMicToggle.setLayoutParams(lp);
        layout.addView(btnMicToggle);

        final TextView tvHint = new TextView(this);
        tvHint.setText("לחצו על המיקרופון כדי להתחיל להקליט");
        tvHint.setTextSize(13);
        tvHint.setTextColor(Color.parseColor("#757575"));
        layout.addView(tvHint);

        builder.setView(layout);
        builder.setNegativeButton("ביטול", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopRecordingAudio(false);
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.create();

        btnMicToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecordingAudio();
                    tvStatus.setText("מקליט כעת... דברו בחופשיות");
                    tvHint.setText("לחצו שוב לסיום ושמירת ההקלטה");
                    btnMicToggle.setBackgroundColor(Color.parseColor("#E53935"));
                } else {
                    stopRecordingAudio(true);
                    dialog.dismiss();
                }
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (isRecording) {
                    stopRecordingAudio(false);
                }
            }
        });

        dialog.show();
    }

    private void startRecordingAudio() {
        try {
            File audioDir = new File(getFilesDir(), "audio_notes");
            if (!audioDir.exists()) audioDir.mkdirs();

            File recordFile = new File(audioDir, "rec_" + System.currentTimeMillis() + ".3gp");
            tempAudioPath = recordFile.getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(tempAudioPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;

            // Also start SpeechRecognizer if available for Hebrew transcription!
            startSpeechToText();

        } catch (Exception e) {
            isRecording = false;
            Toast.makeText(this, "שגיאה בהפעלת המקליט: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAudio(boolean save) {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
            isRecording = false;

            if (save && tempAudioPath != null && !tempAudioPath.isEmpty()) {
                currentNote.setAudioPath(tempAudioPath);
                audioPlayerContainer.setVisibility(View.VISIBLE);
                tvAudioStatus.setText("הקלטה שמורה - לחצו לניגון");
                Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show();
            }
        }
        stopSpeechToText();
    }

    private void startSpeechToText() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}
                @Override
                public void onBeginningOfSpeech() {}
                @Override
                public void onRmsChanged(float rmsdB) {}
                @Override
                public void onBufferReceived(byte[] buffer) {}
                @Override
                public void onEndOfSpeech() {}
                @Override
                public void onError(int error) {}

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String transcribed = matches.get(0);
                        appendTranscribedText(transcribed);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}
                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            speechRecognizer.startListening(intent);
        }
    }

    private void stopSpeechToText() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private void appendTranscribedText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (currentNote.isChecklist()) {
            addChecklistRowView(new Note.ChecklistItem(text, false));
            saveCurrentChecklistState();
        } else {
            String existing = editBody.getText().toString();
            if (!existing.isEmpty()) {
                editBody.setText(existing + "\n" + text);
            } else {
                editBody.setText(text);
            }
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
        tvAudioStatus.setText("לחצו לניגון שמע");
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
        if (isRecording) {
            stopRecordingAudio(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_PICK && data != null && data.getData() != null) {
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
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showVoiceRecordDialog();
            } else {
                Toast.makeText(this, R.string.permission_audio_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
