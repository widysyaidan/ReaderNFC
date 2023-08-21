package com.widy.readernfc.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.widy.readernfc.Common;
import com.widy.readernfc.R;

public class HexToAscii extends BasicActivity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_to_ascii);
        if (getIntent().hasExtra(DumpEditor.EXTRA_DUMP)) {
            String[] dump = getIntent().getStringArrayExtra(
                    DumpEditor.EXTRA_DUMP);
            if (dump != null && dump.length != 0) {
                String s = System.getProperty("line.separator");
                CharSequence ascii = "";
                for (String line : dump) {
                    if (line.startsWith("+")) {
                        String sectorNumber = line.split(": ")[1];
                        ascii = TextUtils.concat(ascii, Common.colorString(
                                getString(R.string.text_sector)
                                        + ": " + sectorNumber,
                                ContextCompat.getColor(this, R.color.white)), s);
                    } else {
                        String converted = Common.hex2Ascii(line);
                        if (converted == null) {
                            converted = getString(R.string.text_invalid_data);
                        }
                        ascii = TextUtils.concat(ascii, " ", converted, s);
                    }
                }
                TextView tv = findViewById(R.id.textViewHexToAscii);
                tv.setText(ascii);
            }
            setIntent(null);
        }
    }
}

