package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import com.widy.readernfc.Common;
import com.widy.readernfc.MCReader;
import com.widy.readernfc.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;

public class DumpEditor extends BasicActivity implements IActivityThatReactsToSave{
    public final static String EXTRA_DUMP =
            "com.widy.readernfc.Activity.DUMP";
    private static final String LOG_TAG =
            DumpEditor.class.getSimpleName();
    private LinearLayout mLayout;
    private String mDumpName;
    private String mKeysName;
    private String mUID;
    private String[] mLines;
    private boolean mDumpChanged;
    private boolean mCloseAfterSuccessfulSave;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_editor);
        mLayout = findViewById(
                R.id.linearLayoutDumpEditor);
        SpannableString keyA = Common.colorString(
                getString(R.string.text_keya),
                ContextCompat.getColor(this, R.color.light_green));
        SpannableString keyB =  Common.colorString(
                getString(R.string.text_keyb),
                ContextCompat.getColor(this, R.color.dark_green));
        SpannableString ac = Common.colorString(
                getString(R.string.text_ac),
                ContextCompat.getColor(this, R.color.orange));
        SpannableString uidAndManuf = Common.colorString(
                getString(R.string.text_uid_and_manuf),
                ContextCompat.getColor(this, R.color.purple));
        SpannableString vb = Common.colorString(
                getString(R.string.text_valueblock),
                ContextCompat.getColor(this, R.color.yellow));
        TextView caption = findViewById(
                R.id.textViewDumpEditorCaption);
        caption.setText(TextUtils.concat(uidAndManuf, " | ", vb, " | ", keyA, " | ", keyB, " | ", ac), TextView.BufferType.SPANNABLE);
        TextView captionTitle = findViewById(R.id.textViewDumpEditorCaptionTitle);
        SpannableString updateText = Common.colorString(getString(R.string.text_update_colors),
                ContextCompat.getColor(this, R.color.white));
        updateText.setSpan(new UnderlineSpan(), 0, updateText.length(), 0);
        captionTitle.setText(TextUtils.concat(getString(R.string.text_caption_title), ": (", updateText, ")"));
        if (getIntent().hasExtra(EXTRA_DUMP)) {
            String[] dump = getIntent().getStringArrayExtra(EXTRA_DUMP);
            if (Common.getUID() != null) {
                mUID = Common.bytes2Hex(Common.getUID());
                setTitle(getTitle() + " (UID: " + mUID+ ")");
            }
            initEditor(dump);
            setIntent(null);
        } else if (getIntent().hasExtra(
                FileChooser.EXTRA_CHOSEN_FILE)) {
            File file = new File(getIntent().getStringExtra(
                    FileChooser.EXTRA_CHOSEN_FILE));
            mDumpName = file.getName();
            setTitle(getTitle() + " (" + mDumpName + ")");
            initEditor(Common.readFileLineByLine(file, false, this));
            setIntent(null);
        } else if (savedInstanceState != null) {
            mCloseAfterSuccessfulSave = savedInstanceState.getBoolean("close_after_successful_save");
            mDumpChanged = savedInstanceState.getBoolean("dump_changed");
            mKeysName = savedInstanceState.getString("keys_name");
            mUID = savedInstanceState.getString("uid");
            if (mUID != null) {
                setTitle(getTitle() + " (" + mUID + ")");
            }
            mDumpName = savedInstanceState.getString("dump_name");
            if (mDumpName != null) {
                setTitle(getTitle() + " (" + mDumpName + ")");
            }
            mLines = savedInstanceState.getStringArray("lines");
            if (mLines != null) {
                initEditor(mLines);
            }
        }
    }
    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putBoolean("dump_changed", mDumpChanged);
        outState.putBoolean("close_after_successful_save", mCloseAfterSuccessfulSave);
        outState.putString("keys_name", mKeysName);
        outState.putString("dump_name", mDumpName);
        outState.putString("uid", mUID);
        outState.putStringArray("lines", mLines);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dump_editor_functions, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
         int itemId = item.getItemId();
        if (itemId == R.id.menuDumpEditorAscii) {
            showAscii();
            return true;
        } else if (itemId == R.id.menuDumpEditorSaveKeys) {
            saveKeys();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public void onUpdateColors(View view) {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        View focused = mLayout.getFocusedChild();
        int focusIndex = -1;
        if (focused != null) {
            focusIndex = mLayout.indexOfChild(focused);
        }
        initEditor(mLines);
        if (focusIndex != -1) {
            // Restore focused view.
            while (focusIndex >= 0
                    && mLayout.getChildAt(focusIndex) == null) {
                focusIndex--;
            }
            if (focusIndex >= 0) {
                mLayout.getChildAt(focusIndex).requestFocus();
            }
        }
    }
    @Override
    public void onBackPressed() {
        if (mDumpChanged) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_save_before_quitting_title)
                    .setMessage(R.string.dialog_save_before_quitting)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton(R.string.action_save,
                            (dialog, which) -> {
                                mCloseAfterSuccessfulSave = true;
                                saveDump();
                    })
                    .setNeutralButton(R.string.action_cancel,
                            (dialog, which) -> {})
                    .setNegativeButton(R.string.action_dont_save,
                            (dialog, id) -> {
                                finish();
                            }).show();
        } else {
            super.onBackPressed();
        }
    }
    @Override
    public void onSaveSuccessful() {
        if (mCloseAfterSuccessfulSave) {
            finish();
        }
        mDumpChanged = false;
    }
    @Override
    public void onSaveFailure() {
        mCloseAfterSuccessfulSave = false;
    }
    private void saveDump() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        if (mDumpName == null) {
            GregorianCalendar calendar = new GregorianCalendar();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            fmt.setCalendar(calendar);
            String dateFormatted = fmt.format(calendar.getTime());
            mDumpName = "UID_" + mUID + "_" + dateFormatted + ".mct";
        }

        saveFile(mLines, mDumpName, true, R.string.dialog_save_dump_title, R.string.dialog_save_dump);
    }
    private void saveFile(final String[] data, final String fileName, final boolean isDump, int titleId, int messageId) {
        String targetDir = (isDump) ? Common.DUMPS_DIR : Common.KEYS_DIR;
        final File path = Common.getFile(targetDir);
        final Context context = this;
        final IActivityThatReactsToSave activity = this;
        View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_save_file, findViewById(android.R.id.content), false);
        TextView message = dialogLayout.findViewById(R.id.textViewDialogSaveFileMessage);
        final EditText input = dialogLayout.findViewById(R.id.editTextDialogSaveFileName);
        message.setText(messageId);
        input.setText(fileName);
        input.requestFocus();
        input.setSelection(0);
        new AlertDialog.Builder(this)
                .setTitle(titleId)
                .setIcon(android.R.drawable.ic_menu_save)
                .setView(dialogLayout)
                .setPositiveButton(R.string.action_save,
                        (dialog, whichButton) -> {
                            if (input.getText() != null
                                    && !input.getText().toString().equals("")
                                    && !input.getText().toString().contains("/")) {
                                File file = new File(path.getPath(),
                                        input.getText().toString());
                                Common.checkFileExistenceAndSave(file, data,
                                        isDump, context, activity);
                                if (isDump) {
                                    mDumpName = file.getName();
                                } else {
                                    mKeysName = file.getName();
                                }
                            } else {
                                Toast.makeText(context, R.string.info_invalid_file_name,
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                .setNegativeButton(R.string.action_cancel,
                        (dialog, whichButton) -> mCloseAfterSuccessfulSave = false)
                .show();
        onUpdateColors(null);
    }
    private int checkDumpAndUpdateLines() {
        ArrayList<String> checkedLines = new ArrayList<>();
        for(int i = 0; i < mLayout.getChildCount(); i++) {
            View child = mLayout.getChildAt(i);
            if (child instanceof EditText) {
                String[] lines = ((EditText)child).getText().toString()
                        .split(System.getProperty("line.separator"));
                if (lines.length != 4 && lines.length != 16) {
                    return 1;
                }
                for (int j = 0; j < lines.length; j++) {
                    if (!lines[j].matches("[0-9A-Fa-f-]+")) {
                        return 2;
                    }
                    if (lines[j].length() != 32) {
                        return 3;
                    }
                    lines[j] = lines[j].toUpperCase(Locale.getDefault());
                    checkedLines.add(lines[j]);
                }
            } else if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String tag = (String) tv.getTag();
                if (tag != null && tag.equals("real_header")) {
                    checkedLines.add("+Sector: " + tv.getText().toString().split(": ")[1]);
                }
            }
        }
        mLines = checkedLines.toArray(new String[0]);
        return 0;
    }
    @SuppressLint("SetTextI18n")
    private void initEditor(String[] lines) {
        int err = Common.isValidDump(lines, true);
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            Toast.makeText(this, R.string.info_editor_init_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        boolean tmpDumpChanged = mDumpChanged;
        mLayout.removeAllViews();
        boolean isFirstBlock = false;
        EditText et = null;
        ArrayList<SpannableString> blocks =
                new ArrayList<>(4);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("+")) {
                isFirstBlock = lines[i].endsWith(" 0");
                String sectorNumber = lines[i].split(": ")[1];
                TextView tv = new TextView(this);
                tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                tv.setText(getString(R.string.text_sector) + ": " + sectorNumber);
                mLayout.addView(tv);
                if (i+1 != lines.length && !lines[i+1].startsWith("*")) {
                    et = new EditText(this);
                    et.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    et.setFilters(new InputFilter[] {new InputFilter.AllCaps()});
                    et.setInputType(et.getInputType()
                            |InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            |InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                            |InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    et.setTypeface(Typeface.MONOSPACE);
                    et.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                            new TextView(this).getTextSize());
                    et.addTextChangedListener(new TextWatcher(){
                        @Override
                        public void afterTextChanged(Editable s) {
                            mDumpChanged = true;
                        }
                        @Override
                        public void beforeTextChanged(CharSequence s,
                                                      int start, int count, int after) {}
                        @Override
                        public void onTextChanged(CharSequence s,
                                                  int start, int before, int count) {}
                    });
                    mLayout.addView(et);
                    tv.setTag("real_header");
                }
            } else if (lines[i].startsWith("*")){
                TextView tv = new TextView(this);
                tv.setTextColor(
                        ContextCompat.getColor(this, R.color.red));
                tv.setText("   " +  getString(
                        R.string.text_no_key_io_error));
                tv.setTag("error");
                mLayout.addView(tv);
            } else {
                if (i+1 == lines.length || lines[i+1].startsWith("+")) {
                    blocks.add(colorSectorTrailer(lines[i]));
                    CharSequence text = "";
                    int j;
                    for (j = 0; j < blocks.size()-1; j++) {
                        text = TextUtils.concat(text, blocks.get(j), "\n");
                    }
                    text = TextUtils.concat(text, blocks.get(j));
                    et.setText(text, TextView.BufferType.SPANNABLE);
                    blocks = new ArrayList<>(4);
                } else {
                    blocks.add(colorDataBlock(lines[i], isFirstBlock));
                    isFirstBlock = false;
                }
            }
        }
        mDumpChanged = tmpDumpChanged;
    }
    private void showAscii() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        ArrayList<String> tmpDump = new ArrayList<>();
        for (int i = 0; i < mLines.length-1; i++) {
            if (i+1 != mLines.length
                    && !mLines[i+1].startsWith("+")) {
                tmpDump.add(mLines[i]);
            }
        }
        String[] dump = tmpDump.toArray(new String[0]);
        Intent intent = new Intent(this, HexToAscii.class);
        intent.putExtra(EXTRA_DUMP, dump);
        startActivity(intent);
    }
    private void showAC() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        ArrayList<String> tmpACs = new ArrayList<>();
        int lastSectorHeader = 0;
        for (int i = 0; i < mLines.length; i++) {
            if (mLines[i].startsWith("+")) {
                tmpACs.add(mLines[i]);
                lastSectorHeader = i;
            } else if (i+1 == mLines.length
                    || mLines[i+1].startsWith("+")) {
                if (i - lastSectorHeader > 4) {
                    tmpACs.add("*" + mLines[i].substring(12, 20));
                } else {
                    tmpACs.add(mLines[i].substring(12, 20));
                }
            }
        }
        String[] ac = tmpACs.toArray(new String[0]);
    }
    private void decodeValueBlocks() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        ArrayList<String> tmpVBs = new ArrayList<>();
        String header = "";
        int blockCounter = 0;
        for (String line : mLines) {
            if (line.startsWith("+")) {
                header = line;
                blockCounter = 0;
            } else {
                if (Common.isValueBlock(line)) {
                    tmpVBs.add(header + ", Block: " + blockCounter);
                    tmpVBs.add(line);
                }
                blockCounter++;
            }
        }
    }
    private void decodeDateOfManuf() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        if (mLines[0].equals("+Sector: 0") && !mLines[1].contains("-")) {
            int year;
            int week;
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yy", Locale.getDefault());
            CharSequence styledText;
            try {
                year = Integer.parseInt(mLines[1].substring(30, 32));
                week = Integer.parseInt(mLines[1].substring(28, 30));
                int now = Integer.parseInt(sdf.format(new Date()));
                if (year >= 0 && year <= now && week >= 1 && week <= 53) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.clear();
                    calendar.set(Calendar.WEEK_OF_YEAR, week);
                    calendar.set(Calendar.YEAR, year + 2000);
                    sdf.applyPattern("dd.MM.yyyy");
                    String startDate = sdf.format(calendar.getTime());
                    calendar.add(Calendar.DATE, 6);
                    String endDate = sdf.format(calendar.getTime());
                    styledText = HtmlCompat.fromHtml(
                            getString(R.string.dialog_date_of_manuf, startDate, endDate),
                            HtmlCompat.FROM_HTML_MODE_LEGACY);
                } else {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                styledText = getText(R.string.dialog_date_of_manuf_error);
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_date_of_manuf_title)
                    .setMessage(styledText)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton(R.string.action_ok,
                            (dialog, which) -> {}).show();
        } else {
            Toast.makeText(this, R.string.info_block0_missing,
                    Toast.LENGTH_LONG).show();
        }
    }
    private void shareDump() {
        File file = saveDumpToTemp();
        if (file == null || !file.exists() && file.isDirectory()) {
            return;
        }
        Common.shareTextFile(this, file);
    }
    private File saveDumpToTemp() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return null;
        }
        String fileName;
        if (mDumpName == null) {
            GregorianCalendar calendar = new GregorianCalendar();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            fmt.setCalendar(calendar);
            fileName = fmt.format(calendar.getTime());
        } else {
            fileName = mDumpName;
        }
        File file = Common.getFile(Common.TMP_DIR + "/" + fileName);
        if (!Common.saveFile(file, mLines, false)) {
            Toast.makeText(this, R.string.info_save_error, Toast.LENGTH_LONG).show();
            return null;
        }
        return file;
    }
    private void saveKeys() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        HashSet<String> tmpKeys = new HashSet<>();
        for (int i = 0; i < mLines.length; i++) {
            if (i+1 == mLines.length || mLines[i+1].startsWith("+")) {
                String keyA = mLines[i].substring(0,12).toUpperCase();
                String keyB = mLines[i].substring(20).toUpperCase();
                if (!keyA.equals(MCReader.NO_KEY)) {
                    tmpKeys.add(keyA);
                }
                if (!keyB.equals(MCReader.NO_KEY)) {
                    tmpKeys.add(keyB);
                }
            }
        }
        String[] keys = tmpKeys.toArray(new String[0]);
        if (mKeysName == null) {
            if (mDumpName == null) {
                mKeysName = "UID_" + mUID;
            } else {
                mKeysName = mDumpName;
            }
        }
        mKeysName += ".keys";
        saveFile(keys, mKeysName, false, R.string.dialog_save_keys_title, R.string.dialog_save_keys);
    }
    private SpannableString colorDataBlock(String data, boolean hasUID) {
        SpannableString ret;
        if (hasUID) {
            ret = new SpannableString(TextUtils.concat(Common.colorString(data, ContextCompat.getColor(this, R.color.purple))));
        } else {
            if (Common.isValueBlock(data)) {
                ret = Common.colorString(data,
                        ContextCompat.getColor(this, R.color.yellow));
            } else {
                ret = new SpannableString(data);
            }
        }
        return ret;
    }
    private SpannableString colorSectorTrailer(String data) {
        int colorKeyA = ContextCompat.getColor(this, R.color.light_green);
        int colorKeyB = ContextCompat.getColor(this, R.color.dark_green);
        int colorAC = ContextCompat.getColor(this, R.color.orange);
        try {
            SpannableString keyA = Common.colorString(data.substring(0, 12), colorKeyA);
            SpannableString keyB = Common.colorString(data.substring(20), colorKeyB);
            SpannableString ac = Common.colorString(data.substring(12, 18), colorAC);
            return new SpannableString(TextUtils.concat(keyA, ac, data.substring(18, 20), keyB));
        } catch (IndexOutOfBoundsException e) {
            Log.d(LOG_TAG, "Error while coloring " + "sector trailer");
        }
        return new SpannableString(data);
    }
}
