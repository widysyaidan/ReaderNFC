package com.widy.readernfc;

import static com.widy.readernfc.Activities.Preferences.Preference.AutoCopyUID;
import static com.widy.readernfc.Activities.Preferences.Preference.UIDFormat;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.widy.readernfc.Activities.IActivityThatReactsToSave;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Locale;

public class Common extends Application {
    public static final boolean IS_DONATE_VERSION = false;
    public static final String HOME_DIR = "/MifareClassicTool";
    public static final String KEYS_DIR = "key-files";
    public static final String DUMPS_DIR = "dump-files";
    public static final String TMP_DIR = "tmp";
    public static final String STD_KEYS = "std.keys";
    public static final String STD_KEYS_EXTENDED = "extended-std.keys";
    public static final String UID_LOG_FILE = "uid-log-file.txt";
    public enum Operation {
        Read, Write, Increment, DecTransRest, ReadKeyA, ReadKeyB, ReadAC,
        WriteKeyA, WriteKeyB, WriteAC
    }
    private static final String LOG_TAG = Common.class.getSimpleName();
    private static Tag mTag = null;
    private static byte[] mUID = null;
    private static SparseArray<byte[][]> mKeyMap = null;
    private static int mKeyMapFrom = -1;
    private static int mKeyMapTo = -1;
    private static String mVersionCode;
    private static boolean mUseAsEditorOnly = false;
    private static int mHasMifareClassicSupport = 0;
    private static ComponentName mPendingComponentName = null;
    private static NfcAdapter mNfcAdapter;
    private static Context mAppContext;
    private static float mScale;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();
        mScale = getResources().getDisplayMetrics().density;

        try {
            mVersionCode = getPackageManager().getPackageInfo(
                    getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(LOG_TAG, "Version not found.");
        }
    }
    public static boolean isFirstInstall() {
        try {
            long firstInstallTime = mAppContext.getPackageManager()
                    .getPackageInfo(mAppContext.getPackageName(), 0).firstInstallTime;
            long lastUpdateTime = mAppContext.getPackageManager()
                    .getPackageInfo(mAppContext.getPackageName(), 0).lastUpdateTime;
            return firstInstallTime == lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }
    public static File getFile(String relativePath) {
        return new File(mAppContext.getFilesDir() + HOME_DIR + "/" + relativePath);
    }
    public static String[] readFileLineByLine(File file, boolean readAll, Context context) {
        if (file == null || !file.exists()) {
            return null;
        }
        String[] ret;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            ret = readLineByLine(reader, readAll, context);
        } catch (FileNotFoundException ex) {
            ret = null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error while closing file.", e);
                    ret = null;
                }
            }
        }
        return ret;
    }
    public static String[] readUriLineByLine(Uri uri, boolean readAll, Context context) {
        InputStream contentStream;
        String[] ret;
        if (uri == null || context == null) {
            return null;
        }
        try {
            contentStream = context.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException | SecurityException ex) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(contentStream));
        ret = readLineByLine(reader, readAll, context);
        try {
            reader.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while closing file.", e);
            return null;
        }
        return ret;
    }
    public static byte[] readUriRaw(Uri uri, Context context) {
        InputStream contentStream;
        if (uri == null || context == null) {
            return null;
        }
        try {
            contentStream = context.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException | SecurityException ex) {
            return null;
        }

        int len;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            while ((len = contentStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, len);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while reading from file.", e);
            return null;
        }

        return buffer.toByteArray();
    }
    private static String[] readLineByLine(BufferedReader reader,
                                           boolean readAll, Context context) {
        String[] ret;
        String line;
        ArrayList<String> linesArray = new ArrayList<>();
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!readAll) {
                    if (line.startsWith("#") || line.equals("")) {
                        continue;
                    }
                    line = line.split("#")[0];
                    line = line.trim();
                }
                try {
                    linesArray.add(line);
                } catch (OutOfMemoryError e) {
                    Toast.makeText(context, R.string.info_file_to_big,
                            Toast.LENGTH_LONG).show();
                    return null;
                }
            }
        } catch (IOException ex) {
            Log.e(LOG_TAG, "Error while reading from file.", ex);
            ret = null;
        }
        if (linesArray.size() > 0) {
            ret = linesArray.toArray(new String[0]);
        } else {
            ret = new String[]{""};
        }
        return ret;
    }
    public static String getFileName(Uri uri, Context context) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    public static void checkFileExistenceAndSave(final File file, final String[] lines, final boolean isDump, final Context context,
                                                 final IActivityThatReactsToSave activity) {
        if (file.exists()) {
            int message = R.string.dialog_save_conflict_keyfile;
            if (isDump) {
                message = R.string.dialog_save_conflict_dump;
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_save_conflict_title)
                    .setMessage(message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.action_replace,
                            (dialog, which) -> {
                                // Replace.
                                if (Common.saveFile(file, lines, false)) {
                                    Toast.makeText(context, R.string.info_save_successful,
                                            Toast.LENGTH_LONG).show();
                                    activity.onSaveSuccessful();
                                } else {
                                    Toast.makeText(context, R.string.info_save_error,
                                            Toast.LENGTH_LONG).show();
                                    activity.onSaveFailure();
                                }
                            })
                    .setNeutralButton(R.string.action_append,
                            (dialog, which) -> {
                                if (Common.saveFileAppend(file, lines, isDump)) {
                                    Toast.makeText(context, R.string.info_save_successful, Toast.LENGTH_LONG).show();
                                    activity.onSaveSuccessful();
                                } else {
                                    Toast.makeText(context, R.string.info_save_error, Toast.LENGTH_LONG).show();
                                    activity.onSaveFailure();
                                }
                            })
                    .setNegativeButton(R.string.action_cancel,
                            (dialog, id) -> {
                                activity.onSaveFailure();
                            }).show();
        } else {
            if (Common.saveFile(file, lines, false)) {
                Toast.makeText(context, R.string.info_save_successful, Toast.LENGTH_LONG).show();
                activity.onSaveSuccessful();
            } else {
                Toast.makeText(context, R.string.info_save_error, Toast.LENGTH_LONG).show();
                activity.onSaveFailure();
            }
        }
    }
    public static boolean saveFileAppend(File file, String[] lines,
                                         boolean comment) {
        if (comment) {
            String[] newLines = new String[lines.length + 4];
            System.arraycopy(lines, 0, newLines, 4, lines.length);
            newLines[1] = "";
            newLines[2] = "# Append #######################";
            newLines[3] = "";
            lines = newLines;
        }
        return saveFile(file, lines, true);
    }
    public static boolean saveFile(File file, String[] lines, boolean append) {
        boolean error = false;
        if (file != null && lines != null && lines.length > 0) {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(file, append));
                if (append) {
                    bw.newLine();
                }
                int i;
                for(i = 0; i < lines.length-1; i++) {
                    bw.write(lines[i]);
                    bw.newLine();
                }
                bw.write(lines[i]);
            } catch (IOException | NullPointerException ex) {
                Log.e(LOG_TAG, "Error while writing to '"
                        + file.getName() + "' file.", ex);
                error = true;

            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        error = true;
                    }
                }
            }
        } else {
            error = true;
        }
        return !error;
    }
    public static boolean saveFile(Uri contentUri, String[] lines, Context context) {
        if (contentUri == null || lines == null || context == null || lines.length == 0) {
            return false;
        }
        String concatenatedLines = TextUtils.join(System.getProperty("line.separator"), lines);
        byte[] bytes = concatenatedLines.getBytes();
        return saveFile(contentUri, bytes, context);
    }
    public static boolean saveFile(Uri contentUri, byte[] bytes, Context context) {
        OutputStream output;
        if (contentUri == null || bytes == null || context == null || bytes.length == 0) {
            return false;
        }
        try {
            output = context.getContentResolver().openOutputStream(contentUri, "rw");
        } catch (FileNotFoundException ex) {
            return false;
        }
        if (output != null) {
            try {
                output.write(bytes);
                output.flush();
                output.close();
            } catch (IOException ex) {
                return false;
            }
        }
        return true;
    }
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mAppContext);
    }
    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity,
                    targetActivity.getClass()).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    targetActivity, 0, intent, PendingIntent.FLAG_MUTABLE);
            try {
                mNfcAdapter.enableForegroundDispatch(
                        targetActivity, pendingIntent, null, new String[][]{
                                new String[]{NfcA.class.getName()}});
            } catch (IllegalStateException ex) {
                Log.d(LOG_TAG, "Error: Could not enable the NFC foreground" +
                        "dispatch system. The activity was not in foreground.");
            }
        }
    }
    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            try {
                mNfcAdapter.disableForegroundDispatch(targetActivity);
            } catch (IllegalStateException ex) {
                Log.d(LOG_TAG, "Error: Could not disable the NFC foreground" +
                        "dispatch system. The activity was not in foreground.");
            }
        }
    }

    public static void logUid(String uid) {
        File log = new File(mAppContext.getFilesDir(),
                HOME_DIR + File.separator + UID_LOG_FILE);
        GregorianCalendar calendar = new GregorianCalendar();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault());
        fmt.setCalendar(calendar);
        String dateFormatted = fmt.format(calendar.getTime());
        String[] logEntry = new String[1];
        logEntry[0] = dateFormatted + ": " + uid;
        saveFile(log, logEntry, true);
    }
    public static int treatAsNewTag(Intent intent, Context context) {
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            tag = MCReader.patchTag(tag);
            if (tag == null) {
                return -3;
            }
            setTag(tag);
            logUid(bytes2Hex(tag.getId()));

            boolean isCopyUID = getPreferences().getBoolean(
                    AutoCopyUID.toString(), false);
            if (isCopyUID) {
                int format = getPreferences().getInt(
                        UIDFormat.toString(), 0);
                String fmtUID = byte2FmtString(tag.getId(),format);
                // Show Toast with copy message.
                Toast.makeText(context,"UID " + context.getResources().getString(
                                        R.string.info_copied_to_clipboard).toLowerCase() + " (" + fmtUID + ")",
                        Toast.LENGTH_SHORT).show();
                copyToClipboard(fmtUID, context, false);
            } else {
                // Show Toast message with UID.
                String id = context.getResources().getString(
                        R.string.info_new_tag_found) + " (UID: ";
                id += bytes2Hex(tag.getId());
                id += ")";
                Toast.makeText(context, id, Toast.LENGTH_LONG).show();
            }
            return checkMifareClassicSupport(tag, context);
        }
        return -4;
    }
    public static boolean hasMifareClassicSupport() {
        if (mHasMifareClassicSupport != 0) {
            return mHasMifareClassicSupport == 1;
        }
        if (NfcAdapter.getDefaultAdapter(mAppContext) == null) {
            mUseAsEditorOnly = true;
            mHasMifareClassicSupport = -1;
            return false;
        }
        boolean isLenovoP2 = Build.MANUFACTURER.equals("LENOVO")
                && Build.MODEL.equals("Lenovo P2a42");
        File device = new File("/dev/bcm2079x-i2c");
        if (!isLenovoP2 && device.exists()) {
            mHasMifareClassicSupport = -1;
            return false;
        }
        device = new File("/dev/pn544");
        if (device.exists()) {
            mHasMifareClassicSupport = 1;
            return true;
        }
        File libsFolder = new File("/system/lib");
        File[] libs = libsFolder.listFiles();
        for (File lib : libs) {
            if (lib.isFile()
                    && lib.getName().startsWith("libnfc")
                    && lib.getName().contains("brcm")
                // Add here other non NXP NFC libraries.
            ) {
                mHasMifareClassicSupport = -1;
                return false;
            }
        }

        mHasMifareClassicSupport = 1;
        return true;
    }
    public static int checkMifareClassicSupport(Tag tag, Context context) {
        if (tag == null || context == null) {
            return -3;
        }
        if (Arrays.asList(tag.getTechList()).contains(
                MifareClassic.class.getName())) {
            try {
                MifareClassic.get(tag);
            } catch (RuntimeException ex) {
                return -2;
            }
            return 0;
        } else {
            NfcA nfca = NfcA.get(tag);
            byte sak = (byte)nfca.getSak();
            if ((sak>>1 & 1) == 1) {
                // RFU.
                return -2;
            } else {
                if ((sak>>3 & 1) == 1) { // SAK bit 4 = 1?
                    return -1;
                } else { // SAK bit 4 = 0
                    return -2;
                }
            }
        }
    }
    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static int isExternalNfcServiceRunning(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            ActivityManager manager =
                    (ActivityManager) context.getSystemService(
                            Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service
                    : manager.getRunningServices(Integer.MAX_VALUE)) {
                if ("eu.dedb.nfc.service.NfcService".equals(
                        service.service.getClassName())) {
                    return 1;
                }
            }
            return 0;
        }
        return -1;
    }
    public static boolean hasExternalNfcInstalled(Context context) {
        return Common.isAppInstalled("eu.dedb.nfc.service", context);
    }
    public static boolean isAppInstalled(String uri, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader;
        boolean tagLost = false;
        // Check for tag.
        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
            } catch (Exception e) {
                tagLost = true;
            }
            if (!tagLost && !reader.isConnected()) {
                reader.close();
                tagLost = true;
            }
            if (!tagLost) {
                return reader;
            }
        }
        Toast.makeText(context, R.string.info_no_tag_found,
                Toast.LENGTH_LONG).show();
        return null;
    }
    public static int getOperationRequirements (byte c1, byte c2, byte c3,
                                                Operation op, boolean isSectorTrailer, boolean isKeyBReadable) {
        if (isSectorTrailer) {
            if (op != Operation.ReadKeyA && op != Operation.ReadKeyB
                    && op != Operation.ReadAC
                    && op != Operation.WriteKeyA
                    && op != Operation.WriteKeyB
                    && op != Operation.WriteAC) {
                return 4;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                if (op == Operation.WriteKeyA
                        || op == Operation.WriteKeyB
                        || op == Operation.ReadKeyB
                        || op == Operation.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operation.ReadKeyB
                        || op == Operation.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operation.WriteKeyA
                        || op == Operation.WriteKeyB) {
                    return 2;
                }
                if (op == Operation.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operation.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operation.ReadKeyA) {
                    return 0;
                }
                return 1;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operation.ReadAC) {
                    return 3;
                }
                if (op == Operation.ReadKeyA
                        || op == Operation.ReadKeyB) {
                    return 0;
                }
                return 2;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operation.ReadAC) {
                    return 3;
                }
                if (op == Operation.WriteAC) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                if (op == Operation.ReadAC) {
                    return 3;
                }
                return 0;
            } else {
                return -1;
            }
        } else {
            // Data Block.
            if (op != Operation.Read && op != Operation.Write
                    && op != Operation.Increment
                    && op != Operation.DecTransRest) {
                // Error. Data block but no data block permissions.
                return -1;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                return (isKeyBReadable) ? 1 : 3;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operation.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operation.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                if (op == Operation.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operation.Read
                        || op == Operation.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 2;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operation.Read
                        || op == Operation.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operation.Read || op == Operation.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operation.Read) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                return 0;
            } else {
                // Error.
                return -1;
            }
        }
    }
    public static boolean isKeyBReadable(byte c1, byte c2, byte c3) {
        return c1 == 0
                && ((c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1));
    }
    public static byte[][] acBytesToACMatrix(byte[] acBytes) {
        if (acBytes == null) {
            return null;
        }
        byte[][] acMatrix = new byte[3][4];
        if (acBytes.length > 2 &&
                (byte)((acBytes[1]>>>4)&0x0F)  ==
                        (byte)((acBytes[0]^0xFF)&0x0F) &&
                (byte)(acBytes[2]&0x0F) ==
                        (byte)(((acBytes[0]^0xFF)>>>4)&0x0F) &&
                (byte)((acBytes[2]>>>4)&0x0F)  ==
                        (byte)((acBytes[1]^0xFF)&0x0F)) {
            // C1, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[0][i] = (byte)((acBytes[1]>>>4+i)&0x01);
            }
            // C2, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[1][i] = (byte)((acBytes[2]>>>i)&0x01);
            }
            // C3, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[2][i] = (byte)((acBytes[2]>>>4+i)&0x01);
            }
            return acMatrix;
        }
        return null;
    }
    public static byte[] acMatrixToACBytes(byte[][] acMatrix) {
        if (acMatrix != null && acMatrix.length == 3) {
            for (int i = 0; i < 3; i++) {
                if (acMatrix[i].length != 4)
                    // Error.
                    return null;
            }
        } else {
            // Error.
            return null;
        }
        byte[] acBytes = new byte[3];
        // Byte 6, Bit 0-3.
        acBytes[0] = (byte)((acMatrix[0][0]^0xFF)&0x01);
        acBytes[0] |= (byte)(((acMatrix[0][1]^0xFF)<<1)&0x02);
        acBytes[0] |= (byte)(((acMatrix[0][2]^0xFF)<<2)&0x04);
        acBytes[0] |= (byte)(((acMatrix[0][3]^0xFF)<<3)&0x08);
        // Byte 6, Bit 4-7.
        acBytes[0] |= (byte)(((acMatrix[1][0]^0xFF)<<4)&0x10);
        acBytes[0] |= (byte)(((acMatrix[1][1]^0xFF)<<5)&0x20);
        acBytes[0] |= (byte)(((acMatrix[1][2]^0xFF)<<6)&0x40);
        acBytes[0] |= (byte)(((acMatrix[1][3]^0xFF)<<7)&0x80);
        // Byte 7, Bit 0-3.
        acBytes[1] = (byte)((acMatrix[2][0]^0xFF)&0x01);
        acBytes[1] |= (byte)(((acMatrix[2][1]^0xFF)<<1)&0x02);
        acBytes[1] |= (byte)(((acMatrix[2][2]^0xFF)<<2)&0x04);
        acBytes[1] |= (byte)(((acMatrix[2][3]^0xFF)<<3)&0x08);
        // Byte 7, Bit 4-7.
        acBytes[1] |= (byte)((acMatrix[0][0]<<4)&0x10);
        acBytes[1] |= (byte)((acMatrix[0][1]<<5)&0x20);
        acBytes[1] |= (byte)((acMatrix[0][2]<<6)&0x40);
        acBytes[1] |= (byte)((acMatrix[0][3]<<7)&0x80);
        // Byte 8, Bit 0-3.
        acBytes[2] = (byte)(acMatrix[1][0]&0x01);
        acBytes[2] |= (byte)((acMatrix[1][1]<<1)&0x02);
        acBytes[2] |= (byte)((acMatrix[1][2]<<2)&0x04);
        acBytes[2] |= (byte)((acMatrix[1][3]<<3)&0x08);
        // Byte 8, Bit 4-7.
        acBytes[2] |= (byte)((acMatrix[2][0]<<4)&0x10);
        acBytes[2] |= (byte)((acMatrix[2][1]<<5)&0x20);
        acBytes[2] |= (byte)((acMatrix[2][2]<<6)&0x40);
        acBytes[2] |= (byte)((acMatrix[2][3]<<7)&0x80);

        return acBytes;
    }
    public static boolean isHexAnd16Byte(String hexString, Context context) {
        boolean isHex = isHex(hexString, context);
        if (!isHex) {
            return false;
        }
        if (hexString.length() != 32) {
            Toast.makeText(context, R.string.info_not_16_byte,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    public static boolean isHex(String hex, Context context) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            Toast.makeText(context, R.string.info_not_hex_data,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    public static boolean isValueBlock(String hexString) {
        byte[] b = Common.hex2Bytes(hexString);
        if (b != null && b.length == 16) {
            return (b[0] == b[8] && (byte) (b[0] ^ 0xFF) == b[4]) &&
                    (b[1] == b[9] && (byte) (b[1] ^ 0xFF) == b[5]) &&
                    (b[2] == b[10] && (byte) (b[2] ^ 0xFF) == b[6]) &&
                    (b[3] == b[11] && (byte) (b[3] ^ 0xFF) == b[7]) &&
                    (b[12] == b[14] && b[13] == b[15] &&
                            (byte) (b[12] ^ 0xFF) == b[13]);
        }
        return false;
    }
    public static int isValidDump(String[] lines, boolean ignoreAsterisk) {
        ArrayList<Integer> knownSectors = new ArrayList<>();
        int blocksSinceLastSectorHeader = 4;
        boolean is16BlockSector = false;
        if (lines == null || lines.length == 0) {
            return 6;
        }
        for(String line : lines) {
            if ((!is16BlockSector && blocksSinceLastSectorHeader == 4)
                    || (is16BlockSector && blocksSinceLastSectorHeader == 16)) {
                if (!line.matches("^\\+Sector: [0-9]{1,2}$")) {
                    return 1;
                }
                int sector;
                try {
                    sector = Integer.parseInt(line.split(": ")[1]);
                } catch (Exception ex) {
                    return 1;
                }
                if (sector < 0 || sector > 39) {
                    return 4;
                }
                if (knownSectors.contains(sector)) {
                    return 5;
                }
                knownSectors.add(sector);
                is16BlockSector = (sector >= 32);
                blocksSinceLastSectorHeader = 0;
                continue;
            }
            if (line.startsWith("*") && ignoreAsterisk) {
                is16BlockSector = false;
                blocksSinceLastSectorHeader = 4;
                continue;
            }
            if (!line.matches("[0-9A-Fa-f-]+")) {
                return 2;
            }
            if (line.length() != 32) {
                return 3;
            }
            blocksSinceLastSectorHeader++;
        }
        return 0;
    }
    public static int isValidKeyFile(String[] lines) {
        boolean keyFound = false;
        if (lines == null || lines.length == 0) {
            return 1;
        }
        for (String line : lines) {
            if (line.startsWith("#")) {
                continue;
            }
            line = line.split("#")[0];
            line = line.trim();
            if (line.equals("")) {
                continue;
            }
            if (!line.matches("[0-9A-Fa-f]+")) {
                return 2;
            }
            if (line.length() != 12) {
                return 3;
            }
            keyFound = true;
        }
        if (!keyFound) {
            // No key found.
            return 1;
        }
        return 0;
    }
    public static void isValidDumpErrorToast(int errorCode, Context context) {
        switch (errorCode) {
            case 1:
                Toast.makeText(context, R.string.info_valid_dump_not_4_or_16_lines,
                        Toast.LENGTH_LONG).show();
                break;
            case 2:
                Toast.makeText(context, R.string.info_valid_dump_not_hex,
                        Toast.LENGTH_LONG).show();
                break;
            case 3:
                Toast.makeText(context, R.string.info_valid_dump_not_16_bytes,
                        Toast.LENGTH_LONG).show();
                break;
            case 4:
                Toast.makeText(context, R.string.info_valid_dump_sector_range,
                        Toast.LENGTH_LONG).show();
                break;
            case 5:
                Toast.makeText(context, R.string.info_valid_dump_double_sector,
                        Toast.LENGTH_LONG).show();
                break;
            case 6:
                Toast.makeText(context, R.string.info_valid_dump_empty_dump,
                        Toast.LENGTH_LONG).show();
                break;
        }
    }
    public static boolean isValidKeyFileErrorToast(
            int errorCode, Context context) {
        switch (errorCode) {
            case 0:
                return true;
            case 1:
                Toast.makeText(context, R.string.info_valid_keys_no_keys,
                        Toast.LENGTH_LONG).show();
                break;
            case 2:
                Toast.makeText(context, R.string.info_valid_keys_not_hex,
                        Toast.LENGTH_LONG).show();
                break;
            case 3:
                Toast.makeText(context, R.string.info_valid_keys_not_6_byte,
                        Toast.LENGTH_LONG).show();
                break;
        }
        return false;
    }
    public static boolean isValidBlock0(String block0, int uidLen, int tagSize,
                                        boolean skipBccCheck) {
        if (block0 == null || block0.length() != 32
                || (uidLen != 4 && uidLen != 7 && uidLen != 10)) {
            return false;
        }
        block0 = block0.toUpperCase();
        String byte0 = block0.substring(0, 2);
        String bcc = block0.substring(8, 10);
        int sakStart = (uidLen == 4) ? uidLen * 2 + 2 : uidLen * 2;
        String sak = block0.substring(sakStart, sakStart + 2);
        String atqa = block0.substring(sakStart + 2, sakStart + 6);
        boolean valid = true;
        if (!skipBccCheck && valid && uidLen == 4) {
            byte byteBcc = hex2Bytes(bcc)[0];
            byte[] uid = hex2Bytes(block0.substring(0, 8));
            valid = isValidBcc(uid, byteBcc);
        }
        if (valid && uidLen == 4) {
            valid = !byte0.equals("88");
        }
        if (valid && uidLen == 4) {
            valid = !byte0.equals("F8");
        }
        if (valid && (uidLen == 7 || uidLen == 10)) {
            byte firstByte = hex2Bytes(byte0)[0];
            valid = (firstByte < 0x81 || firstByte > 0xFE);
        }
        if (valid && (uidLen == 7 || uidLen == 10)) {
            valid = !byte0.equals("00");
        }
        if (valid && (uidLen == 7 || uidLen == 10)) {
            valid = !block0.startsWith("88", 4);
        }
        if (valid && (atqa.matches("040[1-9A-F]") ||
                atqa.matches("020[1-9A-F]") ||
                atqa.matches("480.") ||
                atqa.matches("010F"))) {
        } else if (valid) {
            if (valid && uidLen == 4 && (tagSize == MifareClassic.SIZE_1K ||
                    tagSize == MifareClassic.SIZE_2K ||
                    tagSize == MifareClassic.SIZE_MINI)) {
                valid = atqa.equals("0400");
            } else if (valid && uidLen == 4 && tagSize == MifareClassic.SIZE_4K) {
                valid = atqa.equals("0200");
            } else if (valid && uidLen == 7 && (tagSize == MifareClassic.SIZE_1K ||
                    tagSize == MifareClassic.SIZE_2K ||
                    tagSize == MifareClassic.SIZE_MINI)) {
                valid = atqa.equals("4400");
            } else if (valid && uidLen == 7 && tagSize == MifareClassic.SIZE_4K) {
                valid = atqa.equals("4200");
            }
        }
        byte byteSak = hex2Bytes(sak)[0];
        boolean validSak = false;
        if (valid) {
            if ((byteSak >> 1 & 1) == 0) { // SAK bit 2 = 1?
                if ((byteSak >> 3 & 1) == 1) { // SAK bit 4 = 1?
                    if ((byteSak >> 4 & 1) == 1) { // SAK bit 5 = 1?
                        validSak =  (tagSize == MifareClassic.SIZE_2K ||
                                tagSize == MifareClassic.SIZE_4K);
                    } else {
                        if ((byteSak & 1) == 1) { // SAK bit 1 = 1?
                            validSak = tagSize == MifareClassic.SIZE_MINI;
                        } else {
                            validSak =  (tagSize == MifareClassic.SIZE_2K ||
                                    tagSize == MifareClassic.SIZE_1K);
                        }
                    }
                }
            }
        }
        valid = validSak;

        return valid;
    }
    public static void reverseByteArrayInPlace(byte[] array) {
        for(int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }
    public static String byte2FmtString(byte[] bytes, int fmt) {
        switch(fmt) {
            case 2:
                byte[] revBytes = bytes.clone();
                reverseByteArrayInPlace(revBytes);
                return hex2Dec(bytes2Hex(revBytes));
            case 1:
                return hex2Dec(bytes2Hex(bytes));
        }
        return bytes2Hex(bytes);
    }
    public static String hex2Dec(String hex) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            return null;
        }
        String ret;
        if (hex == null || hex.isEmpty()) {
            ret = "0";
        } else if (hex.length() <= 14) {
            ret = Long.toString(Long.parseLong(hex, 16));
        } else {
            BigInteger bigInteger = new BigInteger(hex , 16);
            ret = bigInteger.toString();
        }
        return ret;
    }
    public static String bytes2Hex(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }
    public static byte[] hex2Bytes(String hex) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }
    public static String hex2Ascii(String hex) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            return null;
        }
        byte[] bytes = hex2Bytes(hex);
        String ret;
        // Replace non printable ASCII with ".".
        for(int i = 0; i < bytes.length; i++) {
            if (bytes[i] < (byte)0x20 || bytes[i] == (byte)0x7F) {
                bytes[i] = (byte)0x2E;
            }
        }
        // Hex to ASCII.
        ret = new String(bytes, StandardCharsets.US_ASCII);
        return ret;
    }
    public static String ascii2Hex(String ascii) {
        if (!(ascii != null && !ascii.equals(""))) {
            return null;
        }
        char[] chars = ascii.toCharArray();
        StringBuilder hex = new StringBuilder();
        for (char aChar : chars) {
            hex.append(String.format("%02X", (int) aChar));
        }
        return hex.toString();
    }
    public static String hex2Bin(String hex) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            return null;
        }
        String bin = new BigInteger(hex, 16).toString(2);
        // Pad left with zeros (have not found a better way...).
        if(bin.length() < hex.length() * 4){
            int diff = hex.length() * 4 - bin.length();
            StringBuilder pad = new StringBuilder();
            for(int i = 0; i < diff; i++){
                pad.append("0");
            }
            pad.append(bin);
            bin = pad.toString();
        }
        return bin;
    }
    public static String bin2Hex(String bin) {
        if (!(bin != null && bin.length() % 8 == 0
                && bin.matches("[0-1]+"))) {
            return null;
        }
        String hex = new BigInteger(bin, 2).toString(16);
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        return hex;
    }
    public static SpannableString colorString(String data, int color) {
        SpannableString ret = new SpannableString(data);
        ret.setSpan(new ForegroundColorSpan(color),
                0, data.length(), 0);
        return ret;
    }
    public static void copyToClipboard(String text, Context context,
                                       boolean showMsg) {
        if (!text.equals("")) {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)
                            context.getSystemService(
                                    Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip =
                    android.content.ClipData.newPlainText(
                            "MIFARE Classic Tool data", text);
            clipboard.setPrimaryClip(clip);
            if (showMsg) {
                Toast.makeText(context, R.string.info_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        }
    }
    public static String getFromClipboard(Context context) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        context.getSystemService(
                                Context.CLIPBOARD_SERVICE);
        if (clipboard.getPrimaryClip() != null
                && clipboard.getPrimaryClip().getItemCount() > 0
                && clipboard.getPrimaryClipDescription().hasMimeType(
                android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                && clipboard.getPrimaryClip().getItemAt(0) != null
                && clipboard.getPrimaryClip().getItemAt(0)
                .getText() != null) {
            return clipboard.getPrimaryClip().getItemAt(0)
                    .getText().toString();
        }
        return null;
    }
    public static void shareTextFile(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(context, R.string.info_share_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        intent.setDataAndType(uri, "text/plain");
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        context.startActivity(Intent.createChooser(intent,
                context.getText(R.string.dialog_share_title)));
    }
    public static void copyFile(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
    public static int dpToPx(int dp) {
        return (int) (dp * mScale + 0.5f);
    }
    public static Tag getTag() {
        return mTag;
    }
    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }
    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }
    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }
    public static void setUseAsEditorOnly(boolean value) {
        mUseAsEditorOnly = value;
    }
    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }
    public static void setKeyMapRange (int from, int to){
        mKeyMapFrom = from;
        mKeyMapTo = to;
    }
    public static int getKeyMapRangeFrom() {
        return mKeyMapFrom;
    }
    public static int getKeyMapRangeTo() {
        return mKeyMapTo;
    }
    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }
    public static void setPendingComponentName(ComponentName pendingActivity) {
        mPendingComponentName = pendingActivity;
    }
    public static ComponentName getPendingComponentName() {
        return mPendingComponentName;
    }
    public static byte[] getUID() {
        return mUID;
    }
    public static boolean isValidBcc(byte[] uid, byte bcc) {
        return calcBcc(uid) == bcc;
    }
    public static byte calcBcc(byte[] uid) throws IllegalArgumentException {
        if (uid.length != 4) {
            throw new IllegalArgumentException("UID length is not 4 bytes.");
        }
        byte bcc = uid[0];
        for(int i = 1; i < uid.length; i++) {
            bcc = (byte)(bcc ^ uid[i]);
        }
        return bcc;
    }
    public static String getVersionCode() {
        return mVersionCode;
    }
    public static boolean useAsEditorOnly() {
        return mUseAsEditorOnly;
    }


}
