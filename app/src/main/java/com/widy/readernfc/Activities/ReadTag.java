package com.widy.readernfc.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.widget.Toast;

import com.widy.readernfc.Common;
import com.widy.readernfc.MCReader;
import com.widy.readernfc.R;

import java.util.ArrayList;
import java.util.Collections;

public class ReadTag extends Activity {
    private final static int KEY_MAP_CREATOR = 1;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SparseArray<String[]> mRawDump;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_tag);

        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_read));
        startActivityForResult(intent, KEY_MAP_CREATOR);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == KEY_MAP_CREATOR) {
            if (resultCode != Activity.RESULT_OK) {
                if (resultCode == 4) {
                    Toast.makeText(this, R.string.info_strange_error,
                            Toast.LENGTH_LONG).show();
                }
                finish();
                return;
            } else {
                readTag();
            }
        }
    }
    private void readTag() {
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        new Thread(() -> {
            mRawDump = reader.readAsMuchAsPossible(
                    Common.getKeyMap());

            reader.close();

            mHandler.post(() -> createTagDump(mRawDump));
        }).start();
    }
    private void createTagDump(SparseArray<String[]> rawDump) {
        ArrayList<String> tmpDump = new ArrayList<>();
        if (rawDump != null) {
            if (rawDump.size() != 0) {
                for (int i = Common.getKeyMapRangeFrom();
                     i <= Common.getKeyMapRangeTo(); i++) {
                    String[] val = rawDump.get(i);
                    tmpDump.add("+Sector: " + i);
                    if (val != null ) {
                        Collections.addAll(tmpDump, val);
                    } else {
                        tmpDump.add("*No keys found or dead sector");
                    }
                }
                String[] dump = tmpDump.toArray(new String[0]);
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(DumpEditor.EXTRA_DUMP, dump);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.info_none_key_valid_for_reading,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.info_tag_removed_while_reading,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}

