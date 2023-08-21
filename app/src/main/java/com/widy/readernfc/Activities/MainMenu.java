package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;

import com.widy.readernfc.Common;
import com.widy.readernfc.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainMenu extends Activity {
    private static final String LOG_TAG = MainMenu.class.getSimpleName();
    private final static int FILE_CHOOSER_DUMP_FILE = 1;
    private final static int FILE_CHOOSER_KEY_FILE = 2;
    private boolean mDonateDialogWasShown = false;
    private boolean mInfoExternalNfcDialogWasShown = false;
    private boolean mHasNoNfc = false;
    private Button mReadTag;
    private Button mWriteTag;
    private Button mInfoTag;
    private Intent mOldIntent = null;

    private enum StartUpNode {
        FirstUseDialog, DonateDialog, HasNfc, HasMifareClassicSupport,
        HasNfcEnabled, HasExternalNfc, ExternalNfcServiceRunning,
        HandleNewIntent
    }
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        TextView tv = findViewById(R.id.textViewMainFooter);
        tv.setText(getString(R.string.app_version) + ": " + Common.getVersionCode());
        if (savedInstanceState != null) {
            mDonateDialogWasShown = savedInstanceState.getBoolean(
                    "donate_dialog_was_shown");
            mInfoExternalNfcDialogWasShown = savedInstanceState.getBoolean(
                    "info_external_nfc_dialog_was_shown");
            mHasNoNfc = savedInstanceState.getBoolean("has_no_nfc");
            mOldIntent = savedInstanceState.getParcelable("old_intent");
        }
        mReadTag = findViewById(R.id.buttonMainReadTag);
        mWriteTag = findViewById(R.id.buttonMainWriteTag);
        mInfoTag = findViewById(R.id.buttonMainInfoTag);

        initFolders();
        copyStdKeysFiles();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("donate_dialog_was_shown", mDonateDialogWasShown);
        outState.putBoolean("info_external_nfc_dialog_was_shown", mInfoExternalNfcDialogWasShown);
        outState.putBoolean("has_no_nfc", mHasNoNfc);
        outState.putParcelable("old_intent", mOldIntent);
    }
    private void runStartUpNode(StartUpNode startUpNode) {
        SharedPreferences sharedPref =
                getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedEditor = sharedPref.edit();
        switch (startUpNode) {
            case FirstUseDialog:
                boolean isFirstRun = sharedPref.getBoolean("is_first_run", true);
                if (isFirstRun) {
                    createFirstUseDialog().show();
                } else {
                    runStartUpNode(StartUpNode.HasNfc);
                }
                break;
            case HasNfc:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (Common.getNfcAdapter() == null) {
                    mHasNoNfc = true;
                    runStartUpNode(StartUpNode.HasExternalNfc);
                } else {
                    runStartUpNode(StartUpNode.HasMifareClassicSupport);
                }
                break;
            case HasMifareClassicSupport:
                if (!Common.hasMifareClassicSupport()
                        && !Common.useAsEditorOnly()) {
                    runStartUpNode(StartUpNode.HasExternalNfc);
                } else {
                    runStartUpNode(StartUpNode.HasNfcEnabled);
                }
                break;
            case HasNfcEnabled:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (!Common.getNfcAdapter().isEnabled()) {
                    if (!Common.useAsEditorOnly()) {
                        createNfcEnableDialog().show();
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                } else {
                    useAsEditorOnly(false);
                    Common.enableNfcForegroundDispatch(this);
                    runStartUpNode(StartUpNode.DonateDialog);
                }
                break;
            case HasExternalNfc:
                if (!Common.hasExternalNfcInstalled(this)
                        && !Common.useAsEditorOnly()) {
                    if (mHasNoNfc) {
                        createInstallExternalNfcDialog().show();
                    } else {
                        AlertDialog ad = createHasNoMifareClassicSupportDialog();
                        ad.show();
                        ((TextView) ad.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                    }
                } else {
                    runStartUpNode(StartUpNode.ExternalNfcServiceRunning);
                }
                break;
            case ExternalNfcServiceRunning:
                int isExternalNfcRunning = Common.isExternalNfcServiceRunning(this);
                if (isExternalNfcRunning == 0) {
                    if (!Common.useAsEditorOnly()) {
                        createStartExternalNfcServiceDialog().show();
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                } else if (isExternalNfcRunning == 1) {
                    useAsEditorOnly(false);
                    runStartUpNode(StartUpNode.DonateDialog);
                } else {
                    if (!Common.useAsEditorOnly()
                            && !mInfoExternalNfcDialogWasShown) {
                        createInfoExternalNfcServiceDialog().show();
                        mInfoExternalNfcDialogWasShown = true;
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                }
                break;
            case HandleNewIntent:
                Common.setPendingComponentName(null);
                Intent intent = getIntent();
                if (intent != null) {
                    boolean isIntentWithTag = intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED);
                    if (isIntentWithTag && intent != mOldIntent) {
                        mOldIntent = intent;
                        onNewIntent(getIntent());
                    } else {
                        break;
                    }
                }
                break;
        }
    }
    private void useAsEditorOnly(boolean useAsEditorOnly) {
        Common.setUseAsEditorOnly(useAsEditorOnly);
        mReadTag.setEnabled(!useAsEditorOnly);
        mWriteTag.setEnabled(!useAsEditorOnly);
    }
    private AlertDialog createFirstUseDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_first_run_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.dialog_first_run)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> dialog.cancel())
                .setOnCancelListener(
                        dialog -> {
                            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                            SharedPreferences.Editor sharedEditor = sharedPref.edit();
                            sharedEditor.putBoolean("is_first_run", false);
                            sharedEditor.apply();
                            runStartUpNode(StartUpNode.HasNfc);
                        })
                .create();
    }
    private AlertDialog createHasNoMifareClassicSupportDialog() {
        CharSequence styledText = HtmlCompat.fromHtml(getString(R.string.dialog_no_mfc_support_device), HtmlCompat.FROM_HTML_MODE_LEGACY);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_no_mfc_support_device_title)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_install_external_nfc, (dialog, which) -> {
                            Uri uri = Uri.parse("market://details?id=eu.dedb.nfc.service");
                            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                            try {
                                startActivity(goToMarket);
                            } catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"
                                                + "/apps/details?id=eu.dedb.nfc" + ".service")));
                            }
                        })
                .setNeutralButton(R.string.action_editor_only, (dialog, which) -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            finish();
                        })
                .setOnCancelListener(dialog -> finish())
                .create();
    }
    private AlertDialog createNfcEnableDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_nfc_not_enabled_title)
                .setMessage(R.string.dialog_nfc_not_enabled)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_nfc,
                        (dialog, which) -> {
                            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }
    private AlertDialog createInstallExternalNfcDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_no_nfc_support_title)
                .setMessage(R.string.dialog_no_nfc_support)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_install_external_nfc,
                        (dialog, which) -> {
                            Uri uri = Uri.parse("market://details?id=com.widy.nfc.service");
                            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                            try {
                                startActivity(goToMarket);
                            } catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store" + "/apps/details?id=eu.dedb.nfc" + ".service")));
                            }
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }
    private AlertDialog createStartExternalNfcServiceDialog() {
        final Context context = this;
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_start_external_nfc_title)
                .setMessage(R.string.dialog_start_external_nfc)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_start_external_nfc,
                        (dialog, which) -> {
                            useAsEditorOnly(true);
                            Common.openApp(context, "eu.dedb.nfc.service");
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }
    private AlertDialog createInfoExternalNfcServiceDialog() {
        final Context context = this;
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_info_external_nfc_title)
                .setMessage(R.string.dialog_info_external_nfc)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_external_nfc_is_running,
                        (dialog, which) -> {
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNeutralButton(R.string.action_start_external_nfc,
                        (dialog, which) -> Common.openApp(context, "eu.dedb.nfc.service"))
                .setNegativeButton(R.string.action_editor_only,
                        (dialog, id) -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setOnCancelListener(
                        dialog -> {
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .create();
    }
    @SuppressLint("ApplySharedPref")
    private void initFolders() {
        File path = Common.getFile(Common.KEYS_DIR);
        if (!path.exists() && !path.mkdirs()) {
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR + "/" + Common.KEYS_DIR + "' directory.");
            return;
        }
        path = Common.getFile(Common.DUMPS_DIR);
        if (!path.exists() && !path.mkdirs()) {
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR + "/" + Common.DUMPS_DIR + "' directory.");
            return;
        }
        path = Common.getFile(Common.TMP_DIR);
        if (!path.exists() && !path.mkdirs()) {
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR + Common.TMP_DIR + "' directory.");
            return;
        }
        File[] tmpFiles = path.listFiles();
        if (tmpFiles != null) {
            for (File file : tmpFiles) {
                file.delete();
            }
        }
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        menu.setHeaderTitle(R.string.dialog_tools_menu_title);
        menu.setHeaderIcon(android.R.drawable.ic_menu_preferences);
        inflater.inflate(R.menu.tools, menu);
        menu.findItem(R.id.menuMainTagInfo).setEnabled(!Common.useAsEditorOnly());
        menu.findItem(R.id.menuMainCloneUidTool).setEnabled(!Common.useAsEditorOnly());
    }
    @Override
    public void onResume() {
        super.onResume();
        useAsEditorOnly(Common.useAsEditorOnly());
        runStartUpNode(StartUpNode.FirstUseDialog);
    }
    @Override
    public void onPause() {
        super.onPause();
        Common.disableNfcForegroundDispatch(this);
    }
    @Override
    public void onNewIntent(Intent intent) {
        if(Common.getPendingComponentName() != null) {
            intent.setComponent(Common.getPendingComponentName());
            startActivity(intent);
        } else {
            int typeCheck = Common.treatAsNewTag(intent, this);
            if (typeCheck == -1 || typeCheck == -2) {
                Intent i = new Intent(this, TagInfoTool.class);
                startActivity(i);
            }
        }
    }
    public void onShowReadTag(View view) {
        Intent intent = new Intent(this, ReadTag.class);
        startActivity(intent);
    }
    public void onShowWriteTag(View view) {
        Intent intent = new Intent(this, WriteTag.class);
        startActivity(intent);
    }
    public void onShowInfoTag(View view) {
        Intent intent = new Intent(this, InfoTag.class);
        startActivity(intent);
    }
    public void onShowTulisTag(View view){
        Intent intent = new Intent(this, Writing.class);
        startActivity(intent);
    }

    public void onShowTools(View view) {
        openContextMenu(view);
    }
    public void onOpenTagDumpEditor(View view) {
        File file = Common.getFile(Common.DUMPS_DIR);
        if (file.isDirectory() && (file.listFiles() == null
                || file.listFiles().length == 0)) {
            Toast.makeText(this, R.string.info_no_dumps,
                    Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, file.getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE, getString(R.string.text_open_dump_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT, getString(R.string.action_open_dump_file));
        startActivityForResult(intent, FILE_CHOOSER_DUMP_FILE);
    }
    public void onOpenKeyEditor(View view) {
        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE, getString(R.string.text_open_key_file_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT, getString(R.string.action_open_key_file));
        intent.putExtra(FileChooser.EXTRA_ALLOW_NEW_FILE, true);
        startActivityForResult(intent, FILE_CHOOSER_KEY_FILE);
    }
    private void onShowAboutDialog() {
        CharSequence styledText = HtmlCompat.fromHtml(
                getString(R.string.dialog_about_mct, Common.getVersionCode()), HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_about_mct_title)
                .setMessage(styledText)
                .setIcon(R.mipmap.ic_launcher)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                        }).create();
        ad.show();
        ((TextView)ad.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        if (id == R.id.menuMainTagInfo) {
            intent = new Intent(this, TagInfoTool.class);
            startActivity(intent);
            return true;
        }
        return super.onContextItemSelected(item);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILE_CHOOSER_DUMP_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    Intent intent = new Intent(this, DumpEditor.class);
                    intent.putExtra(FileChooser.EXTRA_CHOSEN_FILE, data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
                    startActivity(intent);
                }
                break;
        }
    }
    private void copyStdKeysFiles() {
        File std = Common.getFile(Common.KEYS_DIR + "/" + Common.STD_KEYS);
        File extended = Common.getFile(Common.KEYS_DIR + "/" + Common.STD_KEYS_EXTENDED);
        AssetManager assetManager = getAssets();
        try {
            InputStream in = assetManager.open(Common.KEYS_DIR + "/" + Common.STD_KEYS);
            OutputStream out = new FileOutputStream(std);
            Common.copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch(IOException e) {
            Log.e(LOG_TAG, "Error while copying 'std.keys' from assets " + "to internal storage.");
        }
        try {
            InputStream in = assetManager.open(Common.KEYS_DIR + "/" + Common.STD_KEYS_EXTENDED);
            OutputStream out = new FileOutputStream(extended);
            Common.copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch(IOException e) {
            Log.e(LOG_TAG, "Error while copying 'extended-std.keys' " + "from assets to internal storage.");
        }

    }
}
