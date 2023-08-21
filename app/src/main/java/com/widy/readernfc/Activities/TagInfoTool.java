package com.widy.readernfc.Activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.widget.TextViewCompat;

import com.widy.readernfc.Common;
import com.widy.readernfc.R;

public class TagInfoTool extends BasicActivity{
    private LinearLayout mLayout;
    private TextView mErrorMessage;
    private int mMFCSupport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_info_tool);

        mLayout = findViewById(R.id.linearLayoutTagInfoTool);
        mErrorMessage = findViewById(R.id.textTagInfoToolErrorMessage);
        updateTagInfo(Common.getTag());
    }
    @Override
    public void onNewIntent(Intent intent) {
        Common.treatAsNewTag(intent, this);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            updateTagInfo(Common.getTag());
        }
    }
    public void onReadMore(View view) {
        int titleID = 0;
        int messageID = 0;
        if (mMFCSupport == -1) {
            titleID = R.string.dialog_no_mfc_support_device_title;
            messageID = R.string.dialog_no_mfc_support_device;
        } else if (mMFCSupport == -2) {
            titleID = R.string.dialog_no_mfc_support_tag_title;
            messageID = R.string.dialog_no_mfc_support_tag;
        }
        if (messageID == 0) {
            return;
        }
        CharSequence styledText = HtmlCompat.fromHtml(
                getString(messageID), HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(titleID)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                        })
                .show();
        ((TextView)ad.findViewById(android.R.id.message)).setMovementMethod(
                LinkMovementMethod.getInstance());
    }
    @SuppressLint("SetTextI18n")
    private void updateTagInfo(Tag tag) {
        if (tag != null) {
            mMFCSupport = Common.checkMifareClassicSupport(tag, this);
            mLayout.removeAllViews();
            TextView headerGenericInfo = new TextView(this);
            headerGenericInfo.setText(Common.colorString(getString(R.string.text_generic_info), ContextCompat.getColor(this, R.color.blue)));
            TextViewCompat.setTextAppearance(headerGenericInfo, android.R.style.TextAppearance_Large);
            headerGenericInfo.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Common.dpToPx(5);
            headerGenericInfo.setPadding(pad, pad, pad, pad);
            mLayout.addView(headerGenericInfo);
            TextView genericInfo = new TextView(this);
            genericInfo.setPadding(pad, pad, pad, pad);
            TextViewCompat.setTextAppearance(genericInfo, android.R.style.TextAppearance_Medium);
            mLayout.addView(genericInfo);
            String uid = Common.bytes2Hex(tag.getId());
            int uidLen = tag.getId().length;
            uid += " (" + uidLen + " byte";
            if (uidLen == 7) {
                uid += ", CL2";
            } else if (uidLen == 10) {
                uid += ", CL3";
            }
            uid += ")";
            NfcA nfca = NfcA.get(tag);
            byte[] atqaBytes = nfca.getAtqa();
            atqaBytes = new byte[] {atqaBytes[1], atqaBytes[0]};
            String atqa = Common.bytes2Hex(atqaBytes);
            byte[] sakBytes = new byte[] {
                    (byte)((nfca.getSak() >> 8) & 0xFF),
                    (byte)(nfca.getSak() & 0xFF)};
            String sak;
            if (sakBytes[0] != 0) {
                sak = Common.bytes2Hex(sakBytes);
            } else {
                sak = Common.bytes2Hex(new byte[] {sakBytes[1]});
            }
            String ats = "-";
            IsoDep iso = IsoDep.get(tag);
            if (iso != null ) {
                byte[] atsBytes = iso.getHistoricalBytes();
                if (atsBytes != null && atsBytes.length > 0) {
                    ats = Common.bytes2Hex(atsBytes);
                }
            }
            int tagTypeResourceID = getTagIdentifier(atqa, sak, ats);
            String tagType;
            if (tagTypeResourceID == R.string.tag_unknown && mMFCSupport > -2) {
                tagType = getString(R.string.tag_unknown_mf_classic);
            } else {
                tagType = getString(tagTypeResourceID);
            }
            int hc = ContextCompat.getColor(this, R.color.blue);
            genericInfo.setText(TextUtils.concat(Common.colorString(getString(R.string.text_uid) + ":", hc), "\n", uid, "\n",
                    Common.colorString(getString(
                            R.string.text_rf_tech) + ":", hc), "\n", getString(R.string.text_rf_tech_14a), "\n",
                    Common.colorString(getString(R.string.text_atqa) + ":", hc), "\n", atqa, "\n",
                    Common.colorString(getString(R.string.text_sak) + ":", hc), "\n", sak, "\n",
                    Common.colorString(getString(
                            R.string.text_ats) + ":", hc), "\n", ats, "\n",
                    Common.colorString(getString(
                            R.string.text_tag_type_and_manuf) + ":", hc), "\n", tagType));

            if (tagTypeResourceID != R.string.tag_unknown) {
                TextView tagTypeInfo = new TextView(this);
                tagTypeInfo.setPadding(pad, 0, pad, pad);
                tagTypeInfo.setText("(" + getString(R.string.text_tag_type_guess) + ")");
                mLayout.addView(tagTypeInfo);
            }
            LinearLayout layout = findViewById(
                    R.id.linearLayoutTagInfoToolSupport);
            if (mMFCSupport == 0) {
                TextView headerMifareInfo = new TextView(this);
                headerMifareInfo.setText(Common.colorString(getString(R.string.text_mf_info), ContextCompat.getColor(this, R.color.blue)));
                TextViewCompat.setTextAppearance(headerMifareInfo, android.R.style.TextAppearance_Large);
                headerMifareInfo.setGravity(Gravity.CENTER_HORIZONTAL);
                headerMifareInfo.setPadding(pad, pad * 2, pad, pad);
                mLayout.addView(headerMifareInfo);
                TextView mifareInfo = new TextView(this);
                mifareInfo.setPadding(pad, pad, pad, pad);
                TextViewCompat.setTextAppearance(mifareInfo, android.R.style.TextAppearance_Medium);
                mLayout.addView(mifareInfo);

                MifareClassic mfc = MifareClassic.get(tag);
                String size = "" + mfc.getSize();
                String sectorCount = "" + mfc.getSectorCount();
                String blockCount = "" + mfc.getBlockCount();
                mifareInfo.setText(TextUtils.concat(Common.colorString(getString(
                                R.string.text_mem_size) + ":", hc), "\n", size, " byte\n",
                        Common.colorString(getString(
                                R.string.text_block_size) + ":", hc), "\n", "" + MifareClassic.BLOCK_SIZE, " byte\n",
                        Common.colorString(getString(
                                R.string.text_sector_count) + ":", hc), "\n", sectorCount, "\n",
                        Common.colorString(getString(
                                R.string.text_block_count) + ":", hc), "\n", blockCount));
                layout.setVisibility(View.GONE);
            } else if (mMFCSupport == -1) {
                mErrorMessage.setText(R.string.text_no_mfc_support_device);
                layout.setVisibility(View.VISIBLE);
            } else if (mMFCSupport == -2) {
                mErrorMessage.setText(R.string.text_no_mfc_support_tag);
                layout.setVisibility(View.VISIBLE);
            }
        } else {
            TextView text = new TextView(this);
            int pad = Common.dpToPx(5);
            text.setPadding(pad, pad, 0, 0);
            TextViewCompat.setTextAppearance(text, android.R.style.TextAppearance_Medium);
            text.setText(getString(R.string.text_no_tag));
            mLayout.removeAllViews();
            mLayout.addView(text);
            Toast.makeText(this, R.string.info_no_tag_found,
                    Toast.LENGTH_SHORT).show();
        }
    }
    private int getTagIdentifier(String atqa, String sak, String ats) {
        String prefix = "tag_";
        ats = ats.replace("-", "");
        int ret = getResources().getIdentifier(
                prefix + atqa + sak + ats, "string", getPackageName());
        if (ret == 0) {
            ret = getResources().getIdentifier(
                    prefix + atqa + sak, "string", getPackageName());
        }
        if (ret == 0) {
            ret = getResources().getIdentifier(
                    prefix + atqa, "string", getPackageName());
        }
        if (ret == 0) {
            return R.string.tag_unknown;
        }
        return ret;
    }
}

