package com.widy.readernfc;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.widy.readernfc.Activities.Preferences.Preference;
import com.widy.readernfc.Common.Operation;

public class MCReader {

    private static final String LOG_TAG = MCReader.class.getSimpleName();
    public static final String NO_KEY = "------------";
    public static final String NO_DATA = "--------------------------------";
    public static final String DEFAULT_KEY = "FFFFFFFFFFFF";
    private final MifareClassic mMFC;
    private SparseArray<byte[][]> mKeyMap = new SparseArray<>();
    private int mKeyMapStatus = 0;
    private int mLastSector = -1;
    private int mFirstSector = 0;
    private ArrayList<String> mKeysWithOrder;
    private boolean mHasAllZeroKey = false;
    private MCReader(Tag tag) {
        MifareClassic tmpMFC;
        try {
            tmpMFC = MifareClassic.get(tag);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not create MIFARE Classic reader for the"
                    + "provided tag (even after patching it).");
            throw e;
        }
        mMFC = tmpMFC;
    }
    public static Tag patchTag(Tag tag) {
        if (tag == null) {
            return null;
        }
        String[] techList = tag.getTechList();

        Parcel oldParcel = Parcel.obtain();
        tag.writeToParcel(oldParcel, 0);
        oldParcel.setDataPosition(0);

        int len = oldParcel.readInt();
        byte[] id = new byte[0];
        if (len >= 0) {
            id = new byte[len];
            oldParcel.readByteArray(id);
        }
        int[] oldTechList = new int[oldParcel.readInt()];
        oldParcel.readIntArray(oldTechList);
        Bundle[] oldTechExtras = oldParcel.createTypedArray(Bundle.CREATOR);
        int serviceHandle = oldParcel.readInt();
        int isMock = oldParcel.readInt();
        IBinder tagService;
        if (isMock == 0) {
            tagService = oldParcel.readStrongBinder();
        } else {
            tagService = null;
        }
        oldParcel.recycle();

        int nfcaIdx = -1;
        int mcIdx = -1;
        short sak = 0;
        boolean isFirstSak = true;

        for (int i = 0; i < techList.length; i++) {
            if (techList[i].equals(NfcA.class.getName())) {
                if (nfcaIdx == -1) {
                    nfcaIdx = i;
                }
                if (oldTechExtras[i] != null
                        && oldTechExtras[i].containsKey("sak")) {
                    sak = (short) (sak
                            | oldTechExtras[i].getShort("sak"));
                    isFirstSak = nfcaIdx == i;
                }
            } else if (techList[i].equals(MifareClassic.class.getName())) {
                mcIdx = i;
            }
        }

        boolean modified = false;
        if (!isFirstSak) {
            oldTechExtras[nfcaIdx].putShort("sak", sak);
            modified = true;
        }
        if (nfcaIdx != -1 && mcIdx != -1 && oldTechExtras[mcIdx] == null) {
            oldTechExtras[mcIdx] = oldTechExtras[nfcaIdx];
            modified = true;
        }

        if (!modified) {
            return tag;
        }
        Parcel newParcel = Parcel.obtain();
        newParcel.writeInt(id.length);
        newParcel.writeByteArray(id);
        newParcel.writeInt(oldTechList.length);
        newParcel.writeIntArray(oldTechList);
        newParcel.writeTypedArray(oldTechExtras, 0);
        newParcel.writeInt(serviceHandle);
        newParcel.writeInt(isMock);
        if (isMock == 0) {
            newParcel.writeStrongBinder(tagService);
        }
        newParcel.setDataPosition(0);
        Tag newTag = Tag.CREATOR.createFromParcel(newParcel);
        newParcel.recycle();

        return newTag;
    }
    public static MCReader get(Tag tag) {
        MCReader mcr = null;
        if (tag != null) {
            try {
                mcr = new MCReader(tag);
                if (!mcr.isMifareClassic()) {
                    return null;
                }
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return mcr;
    }
    public SparseArray<String[]> readAsMuchAsPossible(
            SparseArray<byte[][]> keyMap) {
        SparseArray<String[]> resultSparseArray;
        if (keyMap != null && keyMap.size() > 0) {
            resultSparseArray = new SparseArray<>(keyMap.size());
            // For all entries in map do:
            for (int i = 0; i < keyMap.size(); i++) {
                String[][] results = new String[2][];
                try {
                    if (keyMap.valueAt(i)[0] != null) {
                        // Read with key A.
                        results[0] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[0], false);
                    }
                    if (keyMap.valueAt(i)[1] != null) {
                        // Read with key B.
                        results[1] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[1], true);
                    }
                } catch (TagLostException e) {
                    return null;
                }
                // Merge results.
                if (results[0] != null || results[1] != null) {
                    resultSparseArray.put(keyMap.keyAt(i), mergeSectorData(
                            results[0], results[1]));
                }
            }
            return resultSparseArray;
        }
        return null;
    }
    public SparseArray<String[]> readAsMuchAsPossible() {
        mKeyMapStatus = getSectorCount();
        while (buildNextKeyMapPart() < getSectorCount()-1);
        return readAsMuchAsPossible(mKeyMap);
    }
    public String[] readSector(int sectorIndex, byte[] key,
                               boolean useAsKeyB) throws TagLostException {
        boolean auth = authenticate(sectorIndex, key, useAsKeyB);
        String[] ret = null;
        // Read sector.
        if (auth) {
            // Read all blocks.
            ArrayList<String> blocks = new ArrayList<>();
            int firstBlock = mMFC.sectorToBlock(sectorIndex);
            int lastBlock = firstBlock + 4;
            if (mMFC.getSize() == MifareClassic.SIZE_4K
                    && sectorIndex > 31) {
                lastBlock = firstBlock + 16;
            }
            for (int i = firstBlock; i < lastBlock; i++) {
                try {
                    byte[] blockBytes = mMFC.readBlock(i);
                    if (blockBytes.length < 16) {
                        throw new IOException();
                    }
                    if (blockBytes.length > 16) {
                        blockBytes = Arrays.copyOf(blockBytes,16);
                    }

                    blocks.add(Common.bytes2Hex(blockBytes));
                } catch (TagLostException e) {
                    throw e;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "(Recoverable) Error while reading block "
                            + i + " from tag.");
                    blocks.add(NO_DATA);
                    if (!mMFC.isConnected()) {
                        throw new TagLostException(
                                "Tag removed during readSector(...)");
                    }
                    authenticate(sectorIndex, key, useAsKeyB);
                }
            }
            ret = blocks.toArray(new String[0]);
            int last = ret.length -1;
            // Validate if it was possible to read any data.
            boolean noData = true;
            for (String s : ret) {
                if (!s.equals(NO_DATA)) {
                    noData = false;
                    break;
                }
            }
            if (noData) {
                ret = null;
            } else {
                if (!useAsKeyB) {
                    if (isKeyBReadable(Common.hex2Bytes(
                            ret[last].substring(12, 20)))) {
                        ret[last] = Common.bytes2Hex(key)
                                + ret[last].substring(12, 32);
                    } else {
                        ret[last] = Common.bytes2Hex(key)
                                + ret[last].substring(12, 20) + NO_KEY;
                    }
                } else {
                    ret[last] = NO_KEY + ret[last].substring(12, 20)
                            + Common.bytes2Hex(key);
                }
            }
        }
        return ret;
    }
    public int writeBlock(int sectorIndex, int blockIndex, byte[] data,
                          byte[] key, boolean useAsKeyB) {
        if (getSectorCount()-1 < sectorIndex) {
            return 1;
        }
        if (mMFC.getBlockCountInSector(sectorIndex)-1 < blockIndex) {
            return 2;
        }
        if (data.length != 16) {
            return 3;
        }
        if (!authenticate(sectorIndex, key, useAsKeyB)) {
            return 4;
        }
        // Write block.
        int block = mMFC.sectorToBlock(sectorIndex) + blockIndex;
        try {
            // Normal write (also feasible for block 0 of gen2 cards).
            mMFC.writeBlock(block, data);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing block to tag.", e);
            return -1;
        }
        return 0;
    }
    public int writeBlock0Gen3(byte[] data) {
        if (data.length != 16) {
            return 1;
        }
        // Write block.
        byte[] writeCommand = {(byte)0x90, (byte)0xF0, (byte)0xCC, (byte)0xCC, (byte)0x10};
        byte[] fullCommand = new byte[writeCommand.length + data.length];
        System.arraycopy(writeCommand, 0, fullCommand, 0, writeCommand.length);
        System.arraycopy(data, 0, fullCommand, writeCommand.length, data.length);
        try {
            NfcA gen3Tag = NfcA.get(mMFC.getTag());
            if (gen3Tag == null) {
                throw new IOException("Tag is not IsoDep compatible.");
            }
            mMFC.close();
            gen3Tag.connect();
            byte[] response = gen3Tag.transceive(fullCommand);
            // TODO: check response for success.
            gen3Tag.close();
            mMFC.connect();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing block to tag.", e);
            return -1;
        }
        return 0;
    }
    public int writeValueBlock(int sectorIndex, int blockIndex, int value,
                               boolean increment, byte[] key, boolean useAsKeyB) {
        if (getSectorCount()-1 < sectorIndex) {
            return 1;
        }
        if (mMFC.getBlockCountInSector(sectorIndex)-1 < blockIndex) {
            return 2;
        }
        if (!authenticate(sectorIndex, key, useAsKeyB)) {
            return 3;
        }
        // Write Value Block.
        int block = mMFC.sectorToBlock(sectorIndex) + blockIndex;
        try {
            if (increment) {
                mMFC.increment(block, value);
            } else {
                mMFC.decrement(block, value);
            }
            mMFC.transfer(block);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing Value Block to tag.", e);
            return -1;
        }
        return 0;
    }
    public int buildNextKeyMapPart() {
        // Clear status and key map before new walk through sectors.
        boolean error = false;
        if (mKeysWithOrder != null && mLastSector != -1) {
            if (mKeyMapStatus == mLastSector+1) {
                mKeyMapStatus = mFirstSector;
                mKeyMap = new SparseArray<>();
            }
            // Get auto reconnect setting.
            boolean autoReconnect = Common.getPreferences().getBoolean(
                    Preference.AutoReconnect.toString(), false);
            // Get retry authentication option.
            boolean retryAuth = Common.getPreferences().getBoolean(
                    Preference.UseRetryAuthentication.toString(), false);
            int retryAuthCount = Common.getPreferences().getInt(
                    Preference.RetryAuthenticationCount.toString(), 1);
            String[] keys = new String[2];
            boolean[] foundKeys = new boolean[] {false, false};
            boolean auth;
            // Check next sector against all keys (lines) with
            // authentication method A and B.
            keysloop:
            for (int i = 0; i < mKeysWithOrder.size(); i++) {
                String key = mKeysWithOrder.get(i);
                byte[] bytesKey = Common.hex2Bytes(key);
                for (int j = 0; j < retryAuthCount+1;) {
                    try {
                        if (!foundKeys[0]) {
                            auth = mMFC.authenticateSectorWithKeyA(
                                    mKeyMapStatus, bytesKey);
                            if (auth) {
                                keys[0] = key;
                                foundKeys[0] = true;
                            }
                        }
                        if (!foundKeys[1]) {
                            auth = mMFC.authenticateSectorWithKeyB(
                                    mKeyMapStatus, bytesKey);
                            if (auth) {
                                keys[1] = key;
                                foundKeys[1] = true;
                            }
                        }
                    } catch (Exception e) {
                        Log.d(LOG_TAG,
                                "Error while building next key map part");
                        if (autoReconnect) {
                            // Is the tag still in range?
                            if (isConnectedButTagLost()) {
                                close();
                            }
                            while (!isConnected()) {
                                // Sleep for 500ms.
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ex) {
                                    // Do nothing.
                                }
                                // Try to reconnect.
                                try {
                                    connect();
                                } catch (Exception ex) {
                                    // Do nothing.
                                }
                            }
                            // Repeat last loop (do not incr. j).
                            continue;
                        } else {
                            error = true;
                            break keysloop;
                        }
                    }
                    if((foundKeys[0] && foundKeys[1]) || !retryAuth) {
                        // Both keys found or no retry wanted. Stop retrying.
                        break;
                    }
                    j++;
                }
                if ((foundKeys[0] && foundKeys[1])) {
                    // Both keys found. Stop searching for keys.
                    break;
                }
            }
            if (!error && (foundKeys[0] || foundKeys[1])) {
                // At least one key found. Add key(s).
                byte[][] bytesKeys = new byte[2][];
                bytesKeys[0] = Common.hex2Bytes(keys[0]);
                bytesKeys[1] = Common.hex2Bytes(keys[1]);
                mKeyMap.put(mKeyMapStatus, bytesKeys);
                if (mKeysWithOrder.size() > 2) {
                    if (foundKeys[0]) {
                        mKeysWithOrder.remove(keys[0]);
                        if (mHasAllZeroKey && !keys[0].equals(DEFAULT_KEY)) {
                            mKeysWithOrder.add(1, keys[0]);
                        } else {
                            mKeysWithOrder.add(0, keys[0]);
                        }
                    }
                    if (foundKeys[1]) {
                        mKeysWithOrder.remove(keys[1]);
                        if (mHasAllZeroKey && !keys[1].equals(DEFAULT_KEY)) {
                            mKeysWithOrder.add(1, keys[1]);
                        } else {
                            mKeysWithOrder.add(0, keys[1]);
                        }
                    }
                }
            }
            mKeyMapStatus++;
        } else {
            error = true;
        }

        if (error) {
            mKeyMapStatus = 0;
            mKeyMap = null;
            return -1;
        }
        return mKeyMapStatus - 1;
    }
    public String[] mergeSectorData(String[] firstResult,
                                    String[] secondResult) {
        String[] ret = null;
        if (firstResult != null || secondResult != null) {
            if ((firstResult != null && secondResult != null)
                    && firstResult.length != secondResult.length) {
                return null;
            }
            int length  = (firstResult != null)
                    ? firstResult.length : secondResult.length;
            ArrayList<String> blocks = new ArrayList<>();
            // Merge data blocks.
            for (int i = 0; i < length -1 ; i++) {
                if (firstResult != null && firstResult[i] != null
                        && !firstResult[i].equals(NO_DATA)) {
                    blocks.add(firstResult[i]);
                } else if (secondResult != null && secondResult[i] != null
                        && !secondResult[i].equals(NO_DATA)) {
                    blocks.add(secondResult[i]);
                } else {
                    // None of the results got the data form the block.
                    blocks.add(NO_DATA);
                }
            }
            ret = blocks.toArray(new String[blocks.size() + 1]);
            int last = length - 1;
            // Merge sector trailer.
            if (firstResult != null && firstResult[last] != null
                    && !firstResult[last].equals(NO_DATA)) {
                // Take first for sector trailer.
                ret[last] = firstResult[last];
                if (secondResult != null && secondResult[last] != null
                        && !secondResult[last].equals(NO_DATA)) {
                    // Merge key form second result to sector trailer.
                    ret[last] = ret[last].substring(0, 20)
                            + secondResult[last].substring(20);
                }
            } else if (secondResult != null && secondResult[last] != null
                    && !secondResult[last].equals(NO_DATA)) {
                // No first result. Take second result as sector trailer.
                ret[last] = secondResult[last];
            } else {
                // No sector trailer at all.
                ret[last] = NO_DATA;
            }
        }
        return ret;
    }
    public HashMap<Integer, HashMap<Integer, Integer>> isWritableOnPositions(
            HashMap<Integer, int[]> pos,
            SparseArray<byte[][]> keyMap) {
        HashMap<Integer, HashMap<Integer, Integer>> ret =
                new HashMap<>();
        for (int i = 0; i < keyMap.size(); i++) {
            int sector = keyMap.keyAt(i);
            if (pos.containsKey(sector)) {
                byte[][] keys = keyMap.get(sector);
                byte[] ac;
                // Authenticate.
                if (keys[0] != null) {
                    if (!authenticate(sector, keys[0], false)) {
                        return null;
                    }
                } else if (keys[1] != null) {
                    if (!authenticate(sector, keys[1], true)) {
                        return null;
                    }
                } else {
                    return null;
                }
                // Read MIFARE Access Conditions.
                int acBlock = mMFC.sectorToBlock(sector)
                        + mMFC.getBlockCountInSector(sector) -1;
                try {
                    ac = mMFC.readBlock(acBlock);
                } catch (Exception e) {
                    ret.put(sector, null);
                    continue;
                }
                if (ac.length < 16) {
                    ret.put(sector, null);
                    continue;
                }
                ac = Arrays.copyOfRange(ac, 6, 9);
                byte[][] acMatrix = Common.acBytesToACMatrix(ac);
                if (acMatrix == null) {
                    ret.put(sector, null);
                    continue;
                }
                boolean isKeyBReadable = Common.isKeyBReadable(
                        acMatrix[0][3], acMatrix[1][3], acMatrix[2][3]);
                // Check all Blocks with data (!= null).
                HashMap<Integer, Integer> blockWithWriteInfo =
                        new HashMap<>();
                for (int block : pos.get(sector)) {
                    if ((block == 3 && sector <= 31)
                            || (block == 15 && sector >= 32)) {
                        // Sector Trailer.
                        // Are the Access Bits writable?
                        int acValue = Common.getOperationRequirements(
                                acMatrix[0][3],
                                acMatrix[1][3],
                                acMatrix[2][3],
                                Operation.WriteAC,
                                true, isKeyBReadable);
                        // Is key A writable? (If so, key B will be writable
                        // with the same key.)
                        int keyABValue = Common.getOperationRequirements(
                                acMatrix[0][3],
                                acMatrix[1][3],
                                acMatrix[2][3],
                                Operation.WriteKeyA,
                                true, isKeyBReadable);

                        int result = keyABValue;
                        if (acValue == 0 && keyABValue != 0) {
                            // Write key found, but AC-bits are not writable.
                            result += 3;
                        } else if (acValue == 2 && keyABValue == 0) {
                            // Access Bits are writable with key B,
                            // but keys are not writable.
                            result = 6;
                        }
                        blockWithWriteInfo.put(block, result);
                    } else {
                        // Data block.
                        int acBitsForBlock = block;
                        // Handle MIFARE Classic 4k Tags.
                        if (sector >= 32) {
                            if (block >= 0 && block <= 4) {
                                acBitsForBlock = 0;
                            } else if (block >= 5 && block <= 9) {
                                acBitsForBlock = 1;
                            } else if (block >= 10 && block <= 14) {
                                acBitsForBlock = 2;
                            }
                        }
                        blockWithWriteInfo.put(
                                block, Common.getOperationRequirements(
                                        acMatrix[0][acBitsForBlock],
                                        acMatrix[1][acBitsForBlock],
                                        acMatrix[2][acBitsForBlock],
                                        Operation.Write,
                                        false, isKeyBReadable));
                    }
                }
                if (blockWithWriteInfo.size() > 0) {
                    ret.put(sector, blockWithWriteInfo);
                }
            }
        }
        return ret;
    }
    public int setKeyFile(File[] keyFiles, Context context) {
        if (keyFiles == null || keyFiles.length == 0 || context == null) {
            return -1;
        }
        HashSet<String> keys = new HashSet<>();
        for (File file : keyFiles) {
            String[] lines = Common.readFileLineByLine(file, false, context);
            if (lines != null) {
                for (String line : lines) {
                    if (!line.equals("") && line.length() == 12
                            && line.matches("[0-9A-Fa-f]+")) {
                        try {
                            keys.add(line);
                        } catch (OutOfMemoryError e) {
                            // Error. Too many keys (out of memory).
                            Toast.makeText(context, R.string.info_to_many_keys,
                                    Toast.LENGTH_LONG).show();
                            return -1;
                        }
                    }
                }
            }
        }
        if (keys.size() > 0) {
            mHasAllZeroKey = keys.contains("000000000000");
            mKeysWithOrder = new ArrayList<>(keys);
            if (mHasAllZeroKey) {
                mKeysWithOrder.remove(DEFAULT_KEY);
                mKeysWithOrder.add(0, DEFAULT_KEY);
            }
            return keys.size();
        }
        return 0;
    }
    public boolean setMappingRange(int firstSector, int lastSector) {
        if (firstSector >= 0 && lastSector < getSectorCount()
                && firstSector <= lastSector) {
            mFirstSector = firstSector;
            mLastSector = lastSector;
            // Init. status of buildNextKeyMapPart to create a new key map.
            mKeyMapStatus = lastSector+1;
            return true;
        }
        return false;
    }
    private boolean authenticate(int sectorIndex, byte[] key,
                                 boolean useAsKeyB) {
        boolean retryAuth = Common.getPreferences().getBoolean(
                Preference.UseRetryAuthentication.toString(), false);
        int retryCount = Common.getPreferences().getInt(
                Preference.RetryAuthenticationCount.toString(), 1);
        if (key == null) {
            return false;
        }
        boolean ret = false;
        for (int i = 0; i < retryCount+1; i++) {
            try {
                if (!useAsKeyB) {
                    // Key A.
                    ret = mMFC.authenticateSectorWithKeyA(sectorIndex, key);
                } else {
                    // Key B.
                    ret = mMFC.authenticateSectorWithKeyB(sectorIndex, key);
                }
            } catch (IOException | ArrayIndexOutOfBoundsException e) {
                Log.d(LOG_TAG, "Error authenticating with tag.");
                return false;
            }
            // Retry?
            if (ret || !retryAuth) {
                break;
            }
        }
        return ret;
    }
    private boolean isKeyBReadable(byte[] ac) {
        if (ac == null) {
            return false;
        }
        byte c1 = (byte) ((ac[1] & 0x80) >>> 7);
        byte c2 = (byte) ((ac[2] & 0x08) >>> 3);
        byte c3 = (byte) ((ac[2] & 0x80) >>> 7);
        return c1 == 0
                && (c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1);
    }
    public SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }
    public boolean isMifareClassic() {
        return mMFC != null;
    }
    public int getSize() {
        return mMFC.getSize();
    }
    public int getSectorCount() {
        boolean useCustomSectorCount = Common.getPreferences().getBoolean(
                Preference.UseCustomSectorCount.toString(), false);
        if (useCustomSectorCount) {
            return Common.getPreferences().getInt(
                    Preference.CustomSectorCount.toString(), 16);
        }
        return mMFC.getSectorCount();
    }
    public int getBlockCount() {
        return mMFC.getBlockCount();
    }
    public int getBlockCountInSector(int sectorIndex) {
        return mMFC.getBlockCountInSector(sectorIndex);
    }
    public static int blockToSector(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= 256) {
            throw new IndexOutOfBoundsException(
                    "Block out of bounds: " + blockIndex);
        }
        if (blockIndex < 32 * 4) {
            return blockIndex / 4;
        } else {
            return 32 + (blockIndex - 32 * 4) / 16;
        }
    }
    public boolean isConnected() {
        return mMFC.isConnected();
    }
    public boolean isConnectedButTagLost() {
        if (isConnected()) {
            try {
                mMFC.readBlock(0);
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }
    public void connect() throws Exception {
        final AtomicBoolean error = new AtomicBoolean(false);
        // Do not connect if already connected.
        if (isConnected()) {
            return;
        }
        // Connect in a worker thread. (connect() might be blocking).
        Thread t = new Thread(() -> {
            try {
                mMFC.connect();
            } catch (IOException | IllegalStateException ex) {
                error.set(true);
            }
        });
        t.start();
        try {
            t.join(500);
        } catch (InterruptedException ex) {
            error.set(true);
        }
        if (error.get()) {
            Log.d(LOG_TAG, "Error while connecting to tag.");
            throw new Exception("Error while connecting to tag.");
        }
    }
    public void close() {
        try {
            mMFC.close();
        }
        catch (IOException e) {
            Log.d(LOG_TAG, "Error on closing tag.");
        }
    }
}

