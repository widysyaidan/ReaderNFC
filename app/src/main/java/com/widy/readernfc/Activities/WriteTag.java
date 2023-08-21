package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.TextViewCompat;

import com.widy.readernfc.Common;
import com.widy.readernfc.MCReader;
import com.widy.readernfc.R;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WriteTag extends BasicActivity{
    public final static String EXTRA_DUMP =
            "com.widy.readernfc.Activity.DUMP";

    private static final int FC_WRITE_DUMP = 1;
    private static final int CKM_WRITE_DUMP = 2;
    private static final int CKM_WRITE_BLOCK = 3;
    private static final int CKM_FACTORY_FORMAT = 4;
    private static final int CKM_WRITE_NEW_VALUE = 5;

    private EditText mSectorTextBlock;
    private EditText mBlockTextBlock;
    private EditText mDataText;
    private EditText mSectorTextVB;
    private EditText mBlockTextVB;
    private EditText mNewValueTextVB;
    private RadioButton mIncreaseVB;
    private EditText mStaticAC;
    private ArrayList<View> mWriteModeLayouts;
    private CheckBox mWriteManufBlock;
    private CheckBox mEnableStaticAC;
    private HashMap<Integer, HashMap<Integer, byte[]>> mDumpWithPos;
    private boolean mWriteDumpFromEditor = false;
    private String[] mDumpFromEditor;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_tag);

        mSectorTextBlock = findViewById(R.id.editTextWriteTagSector);
        mBlockTextBlock = findViewById(R.id.editTextWriteTagBlock);
        mDataText = findViewById(R.id.editTextWriteTagData);
        mSectorTextVB = findViewById(R.id.editTextWriteTagValueBlockSector);
        mBlockTextVB = findViewById(R.id.editTextWriteTagValueBlockBlock);
        mNewValueTextVB = findViewById(R.id.editTextWriteTagValueBlockValue);
        mIncreaseVB = findViewById(R.id.radioButtonWriteTagWriteValueBlockIncr);
        mStaticAC = findViewById(R.id.editTextWriteTagDumpStaticAC);
        mEnableStaticAC = findViewById(R.id.checkBoxWriteTagDumpStaticAC);
        mWriteManufBlock = findViewById(R.id.checkBoxWriteTagDumpWriteManuf);

        mWriteModeLayouts = new ArrayList<>();
        mWriteModeLayouts.add(findViewById(R.id.relativeLayoutWriteTagWriteBlock));
        mWriteModeLayouts.add(findViewById(R.id.linearLayoutWriteTagDump));
        mWriteModeLayouts.add(findViewById(R.id.linearLayoutWriteTagCloneUid));
        mWriteModeLayouts.add(findViewById(R.id.linearLayoutWriteTagFactoryFormat));
        mWriteModeLayouts.add(findViewById(R.id.relativeLayoutWriteTagValueBlock));

        if (savedInstanceState != null) {
            mWriteManufBlock.setChecked(
                    savedInstanceState.getBoolean("write_manuf_block", false));
            Serializable s = savedInstanceState
                    .getSerializable("dump_with_pos");
            if (s instanceof HashMap<?, ?>) {
                mDumpWithPos = (HashMap<Integer, HashMap<Integer, byte[]>>) s;
            }
        }

        Intent i = getIntent();
        if (i.hasExtra(EXTRA_DUMP)) {
            mDumpFromEditor = i.getStringArrayExtra(EXTRA_DUMP);
            mWriteDumpFromEditor = true;
            Button writeBlock = findViewById(R.id.radioButtonWriteTagWriteBlock);
            writeBlock.setEnabled(false);
            Button writeDumpButton = findViewById(R.id.buttonWriteTagDump);
            writeDumpButton.setText(R.string.action_write_dump);
        }
    }
    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("write_manuf_block", mWriteManufBlock.isChecked());
        outState.putSerializable("dump_with_pos", mDumpWithPos);
    }
    public void onChangeWriteMode(View view) {
        for (View layout : mWriteModeLayouts) {
            layout.setVisibility(View.GONE);
        }
        View parent = findViewById(R.id.linearLayoutWriteTag);
        parent.findViewWithTag(view.getTag() + "_layout").setVisibility(View.VISIBLE);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int ckmError = -1;

        switch(requestCode) {
            case FC_WRITE_DUMP:
                if (resultCode == Activity.RESULT_OK) {
                    readDumpFromFile(data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
                }
                break;
            case CKM_WRITE_DUMP:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    checkDumpAgainstTag();
                }
                break;
            case CKM_FACTORY_FORMAT:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    createFactoryFormattedDump();
                }
                break;
            case CKM_WRITE_BLOCK:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    writeBlock();
                }
                break;
            case CKM_WRITE_NEW_VALUE:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    writeValueBlock();
                }
                break;

        }
        if (ckmError == 4) {
            Toast.makeText(this, R.string.info_strange_error, Toast.LENGTH_LONG).show();
        }
    }

    public void onWriteBlock(View view) {
        if (!checkSectorAndBlock(mSectorTextBlock, mBlockTextBlock)) {
            return;
        }
        String data = mDataText.getText().toString();
        if (!Common.isHexAnd16Byte(data, this)) {
            return;
        }
        final int sector = Integer.parseInt(mSectorTextBlock.getText().toString());
        final int block = Integer.parseInt(mBlockTextBlock.getText().toString());
        if (!isSectorInRage(this, true)) {
            return;
        }
        if (block == 3 || block == 15) {
            int acCheck = checkAccessConditions(data, true);
            if (acCheck == 1) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_sector_trailer_warning_title)
                    .setMessage(R.string.dialog_sector_trailer_warning)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.action_i_know_what_i_am_doing,
                            (dialog, which) -> {
                                createKeyMapForBlock(sector, false);
                            })
                    .setNegativeButton(R.string.action_cancel,
                            (dialog, id) -> {
                            }).show();
        } else if (sector == 0 && block == 0) {
            int block0Check = checkBlock0(data, true);
            if (block0Check == 1 || block0Check == 2) {
                return;
            }
            showWriteManufInfo(true);
        } else {
            createKeyMapForBlock(sector, false);
        }
    }
    private boolean checkSectorAndBlock(EditText sector, EditText block) {
        if (sector.getText().toString().equals("")
                || block.getText().toString().equals("")) {
            Toast.makeText(this, R.string.info_data_location_not_set, Toast.LENGTH_LONG).show();
            return false;
        }
        int sectorNr = Integer.parseInt(sector.getText().toString());
        int blockNr = Integer.parseInt(block.getText().toString());
        if (sectorNr > KeyMapCreator.MAX_SECTOR_COUNT-1 || sectorNr < 0) {
            Toast.makeText(this, R.string.info_sector_out_of_range, Toast.LENGTH_LONG).show();
            return false;
        }
        if (blockNr > KeyMapCreator.MAX_BLOCK_COUNT_PER_SECTOR-1 || blockNr < 0) {
            Toast.makeText(this, R.string.info_block_out_of_range, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    public void onShowOptions(View view) {
        LinearLayout ll = findViewById(R.id.linearLayoutWriteTagDumpOptions);
        CheckBox cb = findViewById(R.id.checkBoxWriteTagDumpOptions);
        if (cb.isChecked()) {
            ll.setVisibility(View.VISIBLE);
        } else {
            ll.setVisibility(View.GONE);
        }
    }
    public void onShowWriteManufInfo(View view) {
        showWriteManufInfo(false);
    }
    private void showWriteManufInfo(final boolean createKeyMap) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.dialog_block0_writing_title);
        dialog.setMessage(R.string.dialog_block0_writing);
        dialog.setIcon(android.R.drawable.ic_dialog_info);

        int buttonID = R.string.action_ok;
        if (createKeyMap) {
            buttonID = R.string.action_i_know_what_i_am_doing;
            dialog.setNegativeButton(R.string.action_cancel,
                    (dialog12, which) -> {
                    });
        }
        dialog.setPositiveButton(buttonID,
                (dialog1, which) -> {
                    if (createKeyMap) {
                        createKeyMapForBlock(0, false);
                    }
                });
        dialog.show();
    }
    private int checkBlock0(String block0, boolean showToasts) {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return 1;
        }
        reader.close();
        int uidLen = Common.getUID().length;
        if (uidLen == 4 ) {
            byte bcc = Common.hex2Bytes(block0.substring(8, 10))[0];
            byte[] uid = Common.hex2Bytes(block0.substring(0, 8));
            boolean isValidBcc = Common.isValidBcc(uid, bcc);
            if (!isValidBcc) {
                if (showToasts) {
                    Toast.makeText(this, R.string.info_bcc_not_valid,
                            Toast.LENGTH_LONG).show();
                }
                return 2;
            }
        }
        boolean isValidBlock0 = Common.isValidBlock0(block0, uidLen, reader.getSize(), true);
        if (!isValidBlock0) {
            if (showToasts) {
                Toast.makeText(this, R.string.text_block0_warning, Toast.LENGTH_LONG).show();
            }
            return 3;
        }
        return 0;
    }
    private int checkAccessConditions(String sectorTrailer, boolean showToasts) {
        byte[] acBytes = Common.hex2Bytes(sectorTrailer.substring(12, 18));
        byte[][] acMatrix = Common.acBytesToACMatrix(acBytes);
        if (acMatrix == null) {
            if (showToasts) {
                Toast.makeText(this, R.string.info_ac_format_error,
                        Toast.LENGTH_LONG).show();
            }
            return 1;
        }
        boolean keyBReadable = Common.isKeyBReadable(acMatrix[0][3], acMatrix[1][3], acMatrix[2][3]);
        int writeAC = Common.getOperationRequirements(acMatrix[0][3], acMatrix[1][3], acMatrix[2][3],
                Common.Operation.WriteAC, true, keyBReadable);
        if (writeAC == 0) {
            if (showToasts) {
                Toast.makeText(this, R.string.info_irreversible_acs, Toast.LENGTH_LONG).show();
            }
            return 2;
        }
        return 0;
    }
    public void onShowStaticACInfo(View view) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_static_ac_title)
                .setMessage(R.string.dialog_static_ac)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                        }).show();
    }
    private void createKeyMapForBlock(int sector, boolean isValueBlock) {
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_FROM, sector);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_TO, sector);
        if (isValueBlock) {
            intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT, getString(
                    R.string.action_create_key_map_and_write_value_block));
            startActivityForResult(intent, CKM_WRITE_NEW_VALUE);
        } else {
            intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT, getString(
                    R.string.action_create_key_map_and_write_block));
            startActivityForResult(intent, CKM_WRITE_BLOCK);
        }
    }
    private void writeBlock() {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        int sector = Integer.parseInt(mSectorTextBlock.getText().toString());
        int block = Integer.parseInt(mBlockTextBlock.getText().toString());
        String data = mDataText.getText().toString();
        byte[][] keys = Common.getKeyMap().get(sector);
        int result = -1;

        if (sector == 0 && block == 0) {
            int resultKeyA = -1;
            int resultKeyB = -1;
            if (keys[1] != null) {
                resultKeyB = reader.writeBlock(sector, block, Common.hex2Bytes(data), keys[1], true);
            }
            if (keys[0] != null) {
                resultKeyA = reader.writeBlock(sector, block,
                        Common.hex2Bytes(data),
                        keys[0], false);
            }
            if (resultKeyA == 0 || resultKeyB == 0) {
                result = 0;
            }
        } else {
            if (keys[1] != null) {
                result = reader.writeBlock(sector, block, Common.hex2Bytes(data), keys[1], true);
            }
            if ((result == -1 || result == 4) && keys[0] != null) {
                result = reader.writeBlock(sector, block, Common.hex2Bytes(data), keys[0], false);
            }
        }
        reader.close();
        switch (result) {
            case 2:
                Toast.makeText(this, R.string.info_block_not_in_sector, Toast.LENGTH_LONG).show();
                return;
            case -1:
                Toast.makeText(this, R.string.info_error_writing_block, Toast.LENGTH_LONG).show();
                return;
        }
        Toast.makeText(this, R.string.info_write_successful, Toast.LENGTH_LONG).show();
        finish();
    }
    public void onWriteDump(View view) {
        if (mEnableStaticAC.isChecked()) {
            String ac = mStaticAC.getText().toString();
            if (!ac.matches("[0-9A-Fa-f]+")) {
                Toast.makeText(this, R.string.info_ac_not_hex,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (ac.length() != 6) {
                Toast.makeText(this, R.string.info_ac_not_3_byte, Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (mWriteDumpFromEditor) {
            checkDumpAndShowSectorChooserDialog(mDumpFromEditor);
        } else {
            Intent intent = new Intent(this, FileChooser.class);
            intent.putExtra(FileChooser.EXTRA_DIR,
                    Common.getFile(Common.DUMPS_DIR).getAbsolutePath());
            intent.putExtra(FileChooser.EXTRA_TITLE,
                    getString(R.string.text_open_dump_title));
            intent.putExtra(FileChooser.EXTRA_CHOOSER_TEXT,
                    getString(R.string.text_choose_dump_to_write));
            intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT,
                    getString(R.string.action_write_full_dump));
            startActivityForResult(intent, FC_WRITE_DUMP);
        }
    }
    private void readDumpFromFile(String pathToDump) {
        File file = new File(pathToDump);
        String[] dump = Common.readFileLineByLine(file, false, this);
        checkDumpAndShowSectorChooserDialog(dump);
    }
    @SuppressLint("SetTextI18n")
    private void checkDumpAndShowSectorChooserDialog(final String[] dump) {
        int err = Common.isValidDump(dump, false);
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }

        initDumpWithPosFromDump(dump);
        Integer[] sectors = mDumpWithPos.keySet().toArray(
                new Integer[0]);
        Arrays.sort(sectors);
        final Context context = this;
        final CheckBox[] sectorBoxes = new CheckBox[mDumpWithPos.size()];
        for (int i = 0; i< sectors.length; i++) {
            sectorBoxes[i] = new CheckBox(this);
            sectorBoxes[i].setChecked(true);
            sectorBoxes[i].setTag(sectors[i]);
            sectorBoxes[i].setText(getString(R.string.text_sector) + " " + sectors[i]);
        }
        View.OnClickListener listener = v -> {
            String tag = v.getTag().toString();
            for (CheckBox box : sectorBoxes) {
                box.setChecked(tag.equals("all"));
            }
        };
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_write_sectors_title)
                .setIcon(android.R.drawable.ic_menu_edit)
                .setPositiveButton(R.string.action_ok,
                        (dialog12, which) -> {
                        })
                .setNegativeButton(R.string.action_cancel,
                        (dialog1, which) -> {
                        })
                .create();
        dialog.show();
        final Context con = this;

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                v -> {
                    initDumpWithPosFromDump(dump);
                    for (CheckBox box : sectorBoxes) {
                        int sector = Integer.parseInt(box.getTag().toString());
                        if (!box.isChecked()) {
                            mDumpWithPos.remove(sector);
                        }
                    }
                    if (mDumpWithPos.size() == 0) {
                        Toast.makeText(context, R.string.info_nothing_to_write,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!isSectorInRage(con, false)) {
                        return;
                    }
                    createKeyMapForDump();
                    dialog.dismiss();
                });
    }
    private boolean isSectorInRage(Context context, boolean isWriteBlock) {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return false;
        }
        int lastValidSector = reader.getSectorCount() - 1;
        int lastSector;
        reader.close();
        if (isWriteBlock) {
            lastSector = Integer.parseInt(
                    mSectorTextBlock.getText().toString());
        } else {
            lastSector = Collections.max(mDumpWithPos.keySet());
        }
        if (lastSector > lastValidSector) {
            Toast.makeText(context, R.string.info_tag_too_small,
                    Toast.LENGTH_LONG).show();
            reader.close();
            return false;
        }
        return true;
    }
    private void initDumpWithPosFromDump(String[] dump) {
        mDumpWithPos = new HashMap<>();
        int sector = 0;
        int block = 0;
        for (int i = 0; i < dump.length; i++) {
            if (dump[i].startsWith("+")) {
                String[] tmp = dump[i].split(": ");
                sector = Integer.parseInt(tmp[tmp.length-1]);
                block = 0;
                mDumpWithPos.put(sector, new HashMap<>());
            } else if (!dump[i].contains("-")) {
                if (mEnableStaticAC.isChecked()
                        && (i+1 == dump.length || dump[i+1].startsWith("+"))) {
                    String newBlock = dump[i].substring(0, 12)
                            + mStaticAC.getText().toString()
                            + dump[i].substring(18);
                    dump[i] = newBlock;
                }
                mDumpWithPos.get(sector).put(block++,
                        Common.hex2Bytes(dump[i]));
            } else {
                block++;
            }
        }
    }
    private void createKeyMapForDump() {
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_FROM,
                (int) Collections.min(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_TO,
                (int) Collections.max(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_write_dump));
        startActivityForResult(intent, CKM_WRITE_DUMP);
    }
    private void checkDumpAgainstTag() {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            Toast.makeText(this, R.string.info_tag_lost_check_dump,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (reader.getSectorCount()-1 < Collections.max(
                mDumpWithPos.keySet())) {
            Toast.makeText(this, R.string.info_tag_too_small,
                    Toast.LENGTH_LONG).show();
            reader.close();
            return;
        }
        final SparseArray<byte[][]> keyMap  =
                Common.getKeyMap();
        HashMap<Integer, int[]> dataPos =
                new HashMap<>(mDumpWithPos.size());
        for (int sector : mDumpWithPos.keySet()) {
            int i = 0;
            int[] blocks = new int[mDumpWithPos.get(sector).size()];
            for (int block : mDumpWithPos.get(sector).keySet()) {
                blocks[i++] = block;
            }
            dataPos.put(sector, blocks);
        }
        HashMap<Integer, HashMap<Integer, Integer>> writeOnPos =
                reader.isWritableOnPositions(dataPos, keyMap);
        reader.close();

        if (writeOnPos == null) {
            Toast.makeText(this, R.string.info_tag_lost_check_dump,
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<HashMap<String, String>> list = new
                ArrayList<>();
        final HashMap<Integer, HashMap<Integer, Integer>> writeOnPosSafe =
                new HashMap<>(
                        mDumpWithPos.size());
        HashSet<Integer> sectors = new HashSet<>();
        for (int sector : mDumpWithPos.keySet()) {
            if (keyMap.indexOfKey(sector) < 0) {
                addToList(list, getString(R.string.text_sector) + ": " + sector,
                        getString(R.string.text_keys_not_known));
            } else {
                sectors.add(sector);
            }
        }
        for (int sector : sectors) {
            if (writeOnPos.get(sector) == null) {
                addToList(list, getString(R.string.text_sector) + ": " + sector,
                        getString(R.string.text_invalid_ac_or_sector_dead));
                continue;
            }
            byte[][] keys = keyMap.get(sector);
            Set<Integer> blocks = mDumpWithPos.get(sector).keySet();
            for (int block : blocks) {
                boolean isSafeForWriting = true;
                String position = getString(R.string.text_sector) + ": " + sector + ", " + getString(R.string.text_block) + ": " + block;
                if (!mWriteManufBlock.isChecked()
                        && sector == 0 && block == 0) {
                    continue;
                } else if (mWriteManufBlock.isChecked()
                        && sector == 0 && block == 0) {
                    String block0 = Common.bytes2Hex(mDumpWithPos.get(0).get(0));
                    int block0Check = checkBlock0(block0, false);
                    switch (block0Check) {
                        case 1:
                            Toast.makeText(this, R.string.info_tag_lost_check_dump,
                                    Toast.LENGTH_LONG).show();
                            return;
                        case 2:
                            Toast.makeText(this, R.string.info_bcc_not_valid,
                                    Toast.LENGTH_LONG).show();
                            return;
                        case 3:
                            addToList(list, position, getString(
                                    R.string.text_block0_warning));
                            break;
                    }
                }
                if ((sector < 31 && block == 3) || sector >= 31 && block == 15) {
                    String sectorTrailer = Common.bytes2Hex(
                            mDumpWithPos.get(sector).get(block));
                    int acCheck = checkAccessConditions(sectorTrailer, false);
                    switch (acCheck) {
                        case 1:
                            Toast.makeText(this, R.string.info_ac_format_error,
                                    Toast.LENGTH_LONG).show();
                            return;
                        case 2:
                            addToList(list, position, getString(
                                    R.string.info_irreversible_acs));
                            break;
                    }
                }
                int writeInfo = writeOnPos.get(sector).get(block);
                switch (writeInfo) {
                    case 0:
                        addToList(list, position, getString(
                                R.string.text_block_read_only));
                        isSafeForWriting = false;
                        break;
                    case 1:
                        if (keys[0] == null) {
                            addToList(list, position, getString(
                                    R.string.text_write_key_a_not_known));
                            isSafeForWriting = false;
                        }
                        break;
                    case 2:
                        if (keys[1] == null) {
                            addToList(list, position, getString(
                                    R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        }
                        break;
                    case 3:
                        writeInfo = (keys[0] != null) ? 1 : 2;
                        break;
                    case 4:
                        if (keys[0] == null) {
                            addToList(list, position, getString(
                                    R.string.text_write_key_a_not_known));
                            isSafeForWriting = false;
                        } else {
                            addToList(list, position, getString(
                                    R.string.text_ac_read_only));
                        }
                        break;
                    case 5:
                        if (keys[1] == null) {
                            addToList(list, position, getString(
                                    R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        } else {
                            addToList(list, position, getString(
                                    R.string.text_ac_read_only));
                        }
                        break;
                    case 6:
                        if (keys[1] == null) {
                            addToList(list, position, getString(
                                    R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        } else {
                            addToList(list, position, getString(
                                    R.string.text_keys_read_only));
                        }
                        break;
                    case -1:
                        addToList(list, position, getString(
                                R.string.text_strange_error));
                        isSafeForWriting = false;
                }
                if (isSafeForWriting) {
                    if (writeOnPosSafe.get(sector) == null) {
                        HashMap<Integer, Integer> blockInfo =
                                new HashMap<>();
                        blockInfo.put(block, writeInfo);
                        writeOnPosSafe.put(sector, blockInfo);
                    } else {
                        writeOnPosSafe.get(sector).put(block, writeInfo);
                    }
                }
            }
        }
        if (list.size() != 0) {
            LinearLayout ll = new LinearLayout(this);
            int pad = Common.dpToPx(5);
            ll.setPadding(pad, pad, pad, pad);
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView textView = new TextView(this);
            textView.setText(R.string.dialog_write_issues);
            textView.setPadding(0,0,0, Common.dpToPx(5));
            TextViewCompat.setTextAppearance(textView,
                    android.R.style.TextAppearance_Medium);
            ListView listView = new ListView(this);
            ll.addView(textView);
            ll.addView(listView);
            String[] from = new String[] {"position", "reason"};
            int[] to = new int[] {android.R.id.text1, android.R.id.text2};
            ListAdapter adapter = new SimpleAdapter(this, list,
                    android.R.layout.two_line_list_item, from, to);
            listView.setAdapter(adapter);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_write_issues_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setView(ll)
                    .setPositiveButton(R.string.action_skip_blocks,
                            (dialog, which) -> {
                                writeDump(writeOnPosSafe, keyMap);
                            })
                    .setNegativeButton(R.string.action_cancel_all,
                            (dialog, which) -> {
                            })
                    .show();
        } else {
            writeDump(writeOnPosSafe, keyMap);
        }
    }
    private void addToList(List<HashMap<String, String>> list,
                           String position, String reason) {
        HashMap<String, String> item = new HashMap<>();
        item.put( "position", position);
        item.put( "reason", reason);
        list.add(item);
    }
    private void writeDump(
            final HashMap<Integer, HashMap<Integer, Integer>> writeOnPos,
            final SparseArray<byte[][]> keyMap) {
        if (writeOnPos.size() == 0) {
            Toast.makeText(this, R.string.info_nothing_to_write,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        LinearLayout ll = new LinearLayout(this);
        int pad = Common.dpToPx(20);
        ll.setPadding(pad, pad, pad, pad);
        ll.setGravity(Gravity.CENTER);
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        pad = Common.dpToPx(20);
        progressBar.setPadding(0, 0, pad, 0);
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.dialog_wait_write_tag));
        tv.setTextSize(18);
        ll.addView(progressBar);
        ll.addView(tv);
        final AlertDialog warning = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_wait_write_tag_title)
                .setView(ll)
                .create();
        warning.show();

        final Activity a = this;
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            for (int sector : writeOnPos.keySet()) {
                byte[][] keys = keyMap.get(sector);
                for (int block : writeOnPos.get(sector).keySet()) {
                    byte[] writeKey = null;
                    boolean useAsKeyB = true;
                    int wi = writeOnPos.get(sector).get(block);
                    if (wi == 1 || wi == 4) {
                        writeKey = keys[0]; // Write with key A.
                        useAsKeyB = false;
                    } else if (wi == 2 || wi == 5 || wi == 6) {
                        writeKey = keys[1]; // Write with key B.
                    }
                    int result = reader.writeBlock(sector, block,
                            mDumpWithPos.get(sector).get(block),
                            writeKey, useAsKeyB);

                    if (result != 0) {
                        handler.post(() -> Toast.makeText(a,
                                R.string.info_write_error,
                                Toast.LENGTH_LONG).show());
                        reader.close();
                        warning.cancel();
                        return;
                    }
                }
            }
            reader.close();
            warning.cancel();
            handler.post(() -> Toast.makeText(a, R.string.info_write_successful,
                    Toast.LENGTH_LONG).show());
            a.finish();
        }).start();
    }

    public void onFactoryFormat(View view) {
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_factory_format));
        startActivityForResult(intent, CKM_FACTORY_FORMAT);
    }
    private void createFactoryFormattedDump() {
        mDumpWithPos = new HashMap<>();
        int sectors = MifareClassic.get(Common.getTag()).getSectorCount();
        byte[] emptyBlock = new byte[]
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] normalSectorTrailer = new byte[] {-1, -1, -1, -1, -1, -1,
                -1, 7, -128, 105, -1, -1, -1, -1, -1, -1};
        byte[] lastSectorTrailer = new byte[] {-1, -1, -1, -1, -1, -1,
                -1, 7, -128, -68, -1, -1, -1, -1, -1, -1};
        // Empty 4 block sector.
        HashMap<Integer, byte[]> empty4BlockSector =
                new HashMap<>(4);
        for (int i = 0; i < 3; i++) {
            empty4BlockSector.put(i, emptyBlock);
        }
        empty4BlockSector.put(3, normalSectorTrailer);
        HashMap<Integer, byte[]> empty16BlockSector =
                new HashMap<>(16);
        for (int i = 0; i < 15; i++) {
            empty16BlockSector.put(i, emptyBlock);
        }
        empty16BlockSector.put(15, normalSectorTrailer);
        HashMap<Integer, byte[]> lastSector;
        HashMap<Integer, byte[]> firstSector = new HashMap<>(4);
        firstSector.put(1, emptyBlock);
        firstSector.put(2, emptyBlock);
        firstSector.put(3, normalSectorTrailer);
        mDumpWithPos.put(0, firstSector);
        for (int i = 1; i < sectors && i < 32; i++) {
            mDumpWithPos.put(i, empty4BlockSector);
        }
        if (sectors == 40) {
            for (int i = 32; i < sectors && i < 39; i++) {
                mDumpWithPos.put(i, empty16BlockSector);
            }
            lastSector = new HashMap<>(empty16BlockSector);
            lastSector.put(15, lastSectorTrailer);
        } else {
            lastSector = new HashMap<>(empty4BlockSector);
            lastSector.put(3, lastSectorTrailer);
        }
        mDumpWithPos.put(sectors - 1, lastSector);
        checkDumpAgainstTag();
    }
    public void onWriteValue(View view) {
        if (!checkSectorAndBlock(mSectorTextVB, mBlockTextVB)) {
            return;
        }
        int sector = Integer.parseInt(mSectorTextVB.getText().toString());
        int block = Integer.parseInt(mBlockTextVB.getText().toString());
        if (block == 3 || block == 15 || (sector == 0 && block == 0)) {
            Toast.makeText(this, R.string.info_not_vb,
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Integer.parseInt(mNewValueTextVB.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, R.string.info_value_too_big,
                    Toast.LENGTH_LONG).show();
            return;
        }

        createKeyMapForBlock(sector, true);
    }
    private void writeValueBlock() {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        int value = Integer.parseInt(mNewValueTextVB.getText().toString());
        int sector = Integer.parseInt(mSectorTextVB.getText().toString());
        int block = Integer.parseInt(mBlockTextVB.getText().toString());
        byte[][] keys = Common.getKeyMap().get(sector);
        int result = -1;

        if (keys[1] != null) {
            result = reader.writeValueBlock(sector, block, value,
                    mIncreaseVB.isChecked(),
                    keys[1], true);
        }
        if (result == -1 && keys[0] != null) {
            result = reader.writeValueBlock(sector, block, value,
                    mIncreaseVB.isChecked(),
                    keys[0], false);
        }
        reader.close();
        switch (result) {
            case 2:
                Toast.makeText(this, R.string.info_block_not_in_sector, Toast.LENGTH_LONG).show();
                return;
            case -1:
                Toast.makeText(this, R.string.info_error_writing_value_block, Toast.LENGTH_LONG).show();
                return;
        }
        Toast.makeText(this, R.string.info_write_successful, Toast.LENGTH_LONG).show();
        finish();
    }
}

