package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.widy.readernfc.Common;
import com.widy.readernfc.R;

public class Preferences extends BasicActivity{
    public enum Preference {
        AutoReconnect("auto_reconnect"),
        AutoCopyUID("auto_copy_uid"),
        UIDFormat("uid_format"),
        SaveLastUsedKeyFiles("save_last_used_key_files"),
        UseCustomSectorCount("use_custom_sector_count"),
        CustomSectorCount("custom_sector_count"),
        UseRetryAuthentication("use_retry_authentication"),
        RetryAuthenticationCount("retry_authentication_count");
        private final String text;
        Preference(final String text) {
            this.text = text;
        }

        @NonNull
        @Override
        public String toString() {
            return text;
        }
    }

    private CheckBox mPrefAutoReconnect;
    private CheckBox mPrefAutoCopyUID;
    private CheckBox mPrefSaveLastUsedKeyFiles;
    private CheckBox mUseCustomSectorCount;
    private CheckBox mUseRetryAuthentication;
    private CheckBox mPrefAutostartIfCardDetected;
    private EditText mCustomSectorCount;
    private EditText mRetryAuthenticationCount;
    private RadioGroup mUIDFormatRadioGroup;
    private PackageManager mPackageManager;
    private ComponentName mComponentName;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        mPackageManager = getApplicationContext().getPackageManager();
        mComponentName = new ComponentName(getPackageName(), getPackageName() + ".MainMenuAlias");

        mPrefAutoReconnect = findViewById(R.id.checkBoxPreferencesAutoReconnect);
        mPrefAutoCopyUID = findViewById(R.id.checkBoxPreferencesCopyUID);
        mPrefSaveLastUsedKeyFiles = findViewById(R.id.checkBoxPreferencesSaveLastUsedKeyFiles);
        mUseCustomSectorCount = findViewById(R.id.checkBoxPreferencesUseCustomSectorCount);
        mCustomSectorCount = findViewById(R.id.editTextPreferencesCustomSectorCount);
        mPrefAutostartIfCardDetected = findViewById(R.id.checkBoxPreferencesAutostartIfCardDetected);
        mUseRetryAuthentication = findViewById(R.id.checkBoxPreferencesUseRetryAuthentication);
        mRetryAuthenticationCount = findViewById(R.id.editTextPreferencesRetryAuthenticationCount);

        SharedPreferences pref = Common.getPreferences();
        mPrefAutoReconnect.setChecked(pref.getBoolean(Preference.AutoReconnect.toString(), false));
        mPrefAutoCopyUID.setChecked(pref.getBoolean(Preference.AutoCopyUID.toString(), false));
        setUIDFormatBySequence(pref.getInt(Preference.UIDFormat.toString(),0));
        mPrefSaveLastUsedKeyFiles.setChecked(pref.getBoolean(Preference.SaveLastUsedKeyFiles.toString(), true));
        mUseCustomSectorCount.setChecked(pref.getBoolean(Preference.UseCustomSectorCount.toString(), false));
        mCustomSectorCount.setEnabled(mUseCustomSectorCount.isChecked());
        mCustomSectorCount.setText("" + pref.getInt(Preference.CustomSectorCount.toString(), 16));
        mUseRetryAuthentication.setChecked(pref.getBoolean(Preference.UseRetryAuthentication.toString(), false));
        mRetryAuthenticationCount.setEnabled(mUseRetryAuthentication.isChecked());
        mRetryAuthenticationCount.setText("" + pref.getInt(Preference.RetryAuthenticationCount.toString(), 1));
        detectAutostartIfCardDetectedState();
        mUIDFormatRadioGroup = findViewById(R.id.radioGroupUIDFormat);
        toggleUIDFormat(null);
    }
    @SuppressLint("SwitchIntDef")
    private void detectAutostartIfCardDetectedState() {
        int enabledSetting = mPackageManager.getComponentEnabledSetting(mComponentName);
        switch (enabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                mPrefAutostartIfCardDetected.setChecked(true);
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                mPrefAutostartIfCardDetected.setChecked(false);
                break;
            default:
                break;
        }
    }
    public void onShowAutoReconnectInfo(View view) {new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_auto_reconnect_title)
                .setMessage(R.string.dialog_auto_reconnect)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {}).show();
    }
    public void toggleUIDFormat(View view) {
        for (int i = 0; i < mUIDFormatRadioGroup.getChildCount(); i++) {
            mUIDFormatRadioGroup.getChildAt(i).setEnabled(
                    mPrefAutoCopyUID.isChecked());
        }
    }
    private int getUIDFormatSequence() {
        int id = mUIDFormatRadioGroup.getCheckedRadioButtonId();
        if (id == R.id.radioButtonHex) {
            return 0;
        } else if (id == R.id.radioButtonDecBE) {
            return 1;
        } else if (id == R.id.radioButtonDecLE) {
            return 2;
        }
        return 0;
    }
    private void setUIDFormatBySequence(int seq) {
        RadioButton selectRadioButton;
        int rBID;
        switch(seq) {
            case 2:
                rBID = R.id.radioButtonDecLE;
                break;
            case 1:
                rBID = R.id.radioButtonDecBE;
                break;
            default:
                rBID = R.id.radioButtonHex;
        }
        selectRadioButton = findViewById(rBID);
        selectRadioButton.toggle();
    }
    public void onUseCustomSectorCountChanged(View view) {
        mCustomSectorCount.setEnabled(mUseCustomSectorCount.isChecked());
    }
    public void onUseRetryAuthenticationChanged(View view) {
        mRetryAuthenticationCount.setEnabled(mUseRetryAuthentication.isChecked());
    }
    public void onShowCustomSectorCountInfo(View view) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_custom_sector_count_title)
                .setMessage(R.string.dialog_custom_sector_count)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                        }).show();
    }
    public void onShowRetryAuthenticationInfo(View view) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_retry_authentication_title)
                .setMessage(R.string.dialog_retry_authentication)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {}).show();
    }
    public void onSave(View view) {
        boolean error = false;
        int customSectorCount = 16;
        if (mUseCustomSectorCount.isChecked()) {
            try {
                customSectorCount = Integer.parseInt(mCustomSectorCount.getText().toString());
            } catch (NumberFormatException ex) {
                error = true;
            }
            if (!error && customSectorCount > 40 || customSectorCount <= 0) {
                error = true;
            }
            if (error) {
                Toast.makeText(this, R.string.info_sector_count_error,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        error = false;
        int retryAuthenticationCount = 1;
        if (mUseRetryAuthentication.isChecked()) {
            try {
                retryAuthenticationCount = Integer.parseInt(
                        mRetryAuthenticationCount.getText().toString());
            } catch (NumberFormatException ex) {
                error = true;
            }
            if (!error && retryAuthenticationCount > 1000 || retryAuthenticationCount <= 0) {
                error = true;
            }
            if (error) {
                Toast.makeText(this,
                        R.string.info_retry_authentication_count_error,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        SharedPreferences.Editor edit = Common.getPreferences().edit();
        edit.putBoolean(Preference.AutoReconnect.toString(), mPrefAutoReconnect.isChecked());
        edit.putBoolean(Preference.AutoCopyUID.toString(), mPrefAutoCopyUID.isChecked());
        edit.putInt(Preference.UIDFormat.toString(),getUIDFormatSequence());
        edit.putBoolean(Preference.SaveLastUsedKeyFiles.toString(), mPrefSaveLastUsedKeyFiles.isChecked());
        edit.putBoolean(Preference.UseCustomSectorCount.toString(), mUseCustomSectorCount.isChecked());
        edit.putBoolean(Preference.UseRetryAuthentication.toString(), mUseRetryAuthentication.isChecked());
        edit.putInt(Preference.CustomSectorCount.toString(), customSectorCount);
        edit.putInt(Preference.RetryAuthenticationCount.toString(), retryAuthenticationCount);
        edit.apply();

        int newState;
        if (mPrefAutostartIfCardDetected.isChecked()) {
            newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } else {
            newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }
        mPackageManager.setComponentEnabledSetting(mComponentName, newState, PackageManager.DONT_KILL_APP);
        finish();
    }
    public void onCancel(View view) {
        finish();
    }
}

