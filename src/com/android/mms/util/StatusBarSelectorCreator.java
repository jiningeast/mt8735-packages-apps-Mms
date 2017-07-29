package com.android.mms.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.mediatek.common.MPlugin;
import com.mediatek.widget.CustomAccountRemoteViews;
import com.mediatek.widget.CustomAccountRemoteViews.AccountInfo;
import com.mediatek.widget.DefaultAccountSelectionBar;
import com.mediatek.mms.ext.IMmsStatusBarSelectorExt;
import com.mediatek.mms.ext.DefaultMmsStatusBarSelectorExt;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;

import com.mediatek.internal.telephony.DefaultSmsSimSettings;

public class StatusBarSelectorCreator {

    private static final String TAG = "[StatusBarSelectorCreator]";
    public static final String ACTION_MMS_ACCOUNT_CHANGED = "com.android.mms.ui.ACTION_MMS_ACCOUNT_CHANGED";
    private static StatusBarSelectorCreator sInstance = null;

    private ArrayList<AccountInfo> mData;
    private DefaultAccountSelectionBar mDefaultBar;
    private Context mContext;
    private IMmsStatusBarSelectorExt mMmsStatusBarSelectorPlugin = null;

    public static final StatusBarSelectorCreator getInstance(Activity activity) {
        if (sInstance == null) {
            sInstance = new StatusBarSelectorCreator(activity);
        }
        return sInstance;
    }

    private StatusBarSelectorCreator(Activity activity) {
        mContext = activity.getApplicationContext();
        initPlugin(mContext);
        refreshData();
        mDefaultBar = new DefaultAccountSelectionBar(activity, mContext.getPackageName(), mData);
    }

    public void updateStatusBarData() {
        if (mData != null) {
            refreshData();
            mDefaultBar.updateData(mData);
            if (mData.size() > 0) {
                mDefaultBar.show();
            } else {
                mDefaultBar.hide();
            }
        } else {
            Log.d(TAG, "already finished");
        }
    }

    public void showStatusBar() {
        if (mData == null) {
            refreshData();
            mDefaultBar.updateData(mData);
        }
        if (mData.size() > 0) {
            mDefaultBar.show();
        } else {
            mDefaultBar.hide();
        }
    }

    public void hideStatusBar() {
        mDefaultBar.hide();
        mData = null;
    }

    public void hideNotification() {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mContext.sendBroadcast(intent);
    }

    private void refreshData() {
        if (mData == null) {
            mData = new ArrayList<AccountInfo>();
        }
        int currentSubId = SubscriptionManager.getDefaultSmsSubId();
        Log.d(TAG, "currentSubId: " + currentSubId);
        mData.clear();

        List<SubscriptionInfo> list = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();

        if (list == null || list.size() <= 1) {
            return;
        }
        Log.d(TAG, "sublist size  = " + list.size());
        /// M: Modify for OP09 dual button feature @{
        if (MmsConfig.isDualSendButtonEnable()) {
            if (!SubscriptionManager.isValidSubscriptionId(currentSubId)
                || SubscriptionManager.from(MmsApp.getApplication()).getActiveSubscriptionInfo(
                    currentSubId) == null) {
                SubscriptionInfo firstSub = SubscriptionManager.from(MmsApp.getApplication())
                        .getActiveSubscriptionInfoForSimSlotIndex(0);
                if (firstSub != null) {
                    currentSubId = firstSub.getSubscriptionId();
                }
            }
        }
        // always ask
        AccountInfo askInfo = createAlwaysAskAccountInfo(mContext, shouldAlwaysAskSelected());
        mData.add(askInfo);
        /// @}
        for (SubscriptionInfo record : list) {
            Intent intent = new Intent(ACTION_MMS_ACCOUNT_CHANGED);
            intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, record.getSubscriptionId());
            AccountInfo info = new AccountInfo(0, record.createIconBitmap(mContext),
                    record.getDisplayName().toString(), record.getNumber(), intent, record.getSubscriptionId() == currentSubId);
            mData.add(info);
        }

        //operator config
        if (mMmsStatusBarSelectorPlugin != null) {
            mMmsStatusBarSelectorPlugin.config(mData, 
                    currentSubId, ACTION_MMS_ACCOUNT_CHANGED);
        }
        Log.d(TAG, "mData size  = " + mData.size());
    }

    private boolean shouldAlwaysAskSelected() {
        List<SubscriptionInfo> list = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        int defaultSubId = SubscriptionManager.getDefaultSmsSubId();
        for (SubscriptionInfo subInfo : list) {
            if (subInfo.getSubscriptionId() == defaultSubId) {
                 return false;
            }
        }
        return true;
    }

    private AccountInfo createAlwaysAskAccountInfo(Context context, boolean isSelected) {
        Intent intent = new Intent(ACTION_MMS_ACCOUNT_CHANGED).putExtra(
                PhoneConstants.SUBSCRIPTION_KEY, DefaultSmsSimSettings.ASK_USER_SUB_ID);
        String label = context.getString(com.mediatek.R.string.account_always_ask_title);
        if (MmsConfig.isDualSendButtonEnable()) {
            return new AccountInfo(0, null, label, null, intent, isSelected);
        }
        return new AccountInfo(com.mediatek.R.drawable.account_always_ask_icon, null, label, null,
                intent, isSelected);
    }

    private void initPlugin(Context context) {
    	  Log.d(TAG, "initPlugin");
        if (mMmsStatusBarSelectorPlugin == null) {
            mMmsStatusBarSelectorPlugin = (IMmsStatusBarSelectorExt)MPlugin.createInstance(IMmsStatusBarSelectorExt.class.getName(), context);
            if (mMmsStatusBarSelectorPlugin == null) {
                mMmsStatusBarSelectorPlugin = new DefaultMmsStatusBarSelectorExt(context);
                Log.d(TAG, "default mMmsStatusBarSelectorPlugin = " + mMmsStatusBarSelectorPlugin);
            }
        }
        Log.d(TAG, "initPlugin: " + mMmsStatusBarSelectorPlugin);
    }
}
