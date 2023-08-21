package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.widy.readernfc.Common;
import com.widy.readernfc.R;

import java.io.File;
import java.util.Arrays;

public class FileChooser extends BasicActivity{
    public final static String EXTRA_DIR =
            "com.widy.readernfc.Activity.FileChooser.DIR";
    public final static String EXTRA_TITLE =
            "com.widy.readernfc.Activity.FileChooser.TITLE";
    public final static String EXTRA_CHOOSER_TEXT =
            "com.widy.readernfc.Activity.FileChooser.CHOOSER_TEXT";
    public final static String EXTRA_BUTTON_TEXT =
            "com.widy.readernfc.Activity.FileChooser.BUTTON_TEXT";
    public final static String EXTRA_ALLOW_NEW_FILE =
            "com.widy.readernfc.Activity.FileChooser.ALLOW_NEW_FILE";
    public final static String EXTRA_CHOSEN_FILE =
            "com.widy.readernfc.Activity.CHOSEN_FILE";
    public final static String EXTRA_CHOSEN_FILENAME =
            "com.widy.readernfc.Activity.EXTRA_CHOSEN_FILENAME";
    private static final String LOG_TAG =
            FileChooser.class.getSimpleName();
    private RadioGroup mGroupOfFiles;
    private Button mChooserButton;
    private TextView mChooserText;
    private MenuItem mDeleteFile;
    private File mDir;
    private boolean mIsDirEmpty;
    private boolean mIsAllowNewFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_chooser);
        mGroupOfFiles = findViewById(R.id.radioGroupFileChooser);
    }

    @Override
    public void onStart() {
        super.onStart();

        mChooserText = findViewById(R.id.textViewFileChooser);
        mChooserButton = findViewById(R.id.buttonFileChooserChoose);
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_TITLE)) {
            setTitle(intent.getStringExtra(EXTRA_TITLE));
        }
        if (intent.hasExtra(EXTRA_CHOOSER_TEXT)) {
            mChooserText.setText(intent.getStringExtra(EXTRA_CHOOSER_TEXT));
        }
        if (intent.hasExtra(EXTRA_BUTTON_TEXT)) {
            mChooserButton.setText(intent.getStringExtra(EXTRA_BUTTON_TEXT));
        }
        if (intent.hasExtra(EXTRA_ALLOW_NEW_FILE)) {
            mIsAllowNewFile = intent.getBooleanExtra(EXTRA_ALLOW_NEW_FILE, false);
        }
        if (intent.hasExtra(EXTRA_DIR)) {
            File path = new File(intent.getStringExtra(EXTRA_DIR));
            if (path.exists()) {
                if (!path.isDirectory()) {
                    setResult(4);
                    finish();
                    return;
                }
                mDir = path;
                mIsDirEmpty = updateFileIndex(path);
            } else {
                Log.e(LOG_TAG, "Directory for FileChooser does not exist.");
                setResult(1);
                finish();
            }
        } else {
            Log.d(LOG_TAG, "Directory for FileChooser was not in intent.");
            setResult(2);
            finish();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_chooser_functions, menu);
        mDeleteFile = menu.findItem(R.id.menuFileChooserDeleteFile);
        MenuItem newFile = menu.findItem(R.id.menuFileChooserNewFile);
        mDeleteFile.setEnabled(!mIsDirEmpty);
        newFile.setEnabled(mIsAllowNewFile);
        newFile.setVisible(mIsAllowNewFile);

        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menuFileChooserNewFile) {
            onNewFile();
            return true;
        } else if (itemId == R.id.menuFileChooserDeleteFile) {
            onDeleteFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public void onFileChosen(View view) {
        RadioButton selected = findViewById(mGroupOfFiles.getCheckedRadioButtonId());
        Intent intent = new Intent();
        File file = new File(mDir.getPath(), selected.getText().toString());
        intent.putExtra(EXTRA_CHOSEN_FILE, file.getPath());
        intent.putExtra(EXTRA_CHOSEN_FILENAME, file.getName());
        setResult(Activity.RESULT_OK, intent);
        finish();
    }
    @SuppressLint("SetTextI18n")
    private boolean updateFileIndex(File path) {
        boolean isEmpty = true;
        File[] files = null;
        String chooserText = "";
        if (path != null) {
            files = path.listFiles();
        }
        mGroupOfFiles.removeAllViews();
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile()) {
                    RadioButton r = new RadioButton(this);
                    r.setText(f.getName());
                    mGroupOfFiles.addView(r);
                }
            }
            if (mGroupOfFiles.getChildCount() > 0) {
                isEmpty = false;
                ((RadioButton) mGroupOfFiles.getChildAt(0)).setChecked(true);
            }
        } else {
            isEmpty = true;
        }
        if ((!Common.isFirstInstall() && isEmpty) ||
                (!Common.isFirstInstall() && files != null && files.length == 2
                        && files[0].getName().equals(Common.STD_KEYS_EXTENDED)
                        && files[1].getName().equals(Common.STD_KEYS))) {
            chooserText += getString(R.string.text_missing_files_update) + "\n\n";
        }
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_CHOOSER_TEXT)) {
            chooserText += intent.getStringExtra(EXTRA_CHOOSER_TEXT);
        } else {
            chooserText += getString(R.string.text_chooser_info_text);
        }
        if (isEmpty) {
            chooserText += "\n\n   --- " + getString(R.string.text_no_files_in_chooser) + " ---";
        }
        mChooserText.setText(chooserText);
        mChooserButton.setEnabled(!isEmpty);
        if (mDeleteFile != null) {
            mDeleteFile.setEnabled(!isEmpty);
        }
        return isEmpty;
    }
    private void onNewFile() {
        final Context cont = this;
        String prefill = "";
        if (mDir.getName().equals(Common.KEYS_DIR)) {
            prefill = ".keys";
        }
        View dialogLayout = getLayoutInflater().inflate(
                R.layout.dialog_save_file,
                findViewById(android.R.id.content), false);
        TextView message = dialogLayout.findViewById(
                R.id.textViewDialogSaveFileMessage);
        final EditText input = dialogLayout.findViewById(
                R.id.editTextDialogSaveFileName);
        message.setText(R.string.dialog_new_file);
        input.setText(prefill);
        input.requestFocus();
        input.setSelection(0);
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        input.postDelayed(() -> {
            input.requestFocus();
            imm.showSoftInput(input, 0);
        }, 100);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_new_file_title)
                .setIcon(android.R.drawable.ic_menu_add)
                .setView(dialogLayout)
                .setPositiveButton(R.string.action_ok,
                        (dialog, whichButton) -> {
                            if (input.getText() != null
                                    && !input.getText().toString().equals("")
                                    && !input.getText().toString().contains("/")) {
                                File file = new File(mDir.getPath(),
                                        input.getText().toString());
                                if (file.exists()) {
                                    Toast.makeText(cont,
                                            R.string.info_file_already_exists,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Intent intent = new Intent();
                                intent.putExtra(EXTRA_CHOSEN_FILE, file.getPath());
                                setResult(Activity.RESULT_OK, intent);
                                finish();
                            } else {
                                Toast.makeText(cont, R.string.info_invalid_file_name,
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                .setNegativeButton(R.string.action_cancel,
                        (dialog, whichButton) -> {
                        })
                .show();
    }
    private void onDeleteFile() {
        RadioButton selected = findViewById(
                mGroupOfFiles.getCheckedRadioButtonId());
        File file = new File(mDir.getPath(), selected.getText().toString());
        file.delete();
        mIsDirEmpty = updateFileIndex(mDir);
    }
}
