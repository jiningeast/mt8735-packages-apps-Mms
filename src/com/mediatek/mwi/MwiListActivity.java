package com.mediatek.mwi;

import java.util.HashSet;

import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.ui.ConversationList.BaseProgressQueryHandler;
import com.android.mms.ui.CustomMenu;
import com.android.mms.ui.CustomMenu.DropDownMenu;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.MmsLog;
import android.provider.Telephony.Mwi;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.telephony.PhoneNumberUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.PopupMenu.OnMenuItemClickListener;

public class MwiListActivity extends Activity {
    private static final String TAG = "Mms/Mwi";

    private static boolean mIsShowing = false;
    private BackgroundQueryHandler mListQueryHandler;
    private Uri mMwiUri = Mwi.CONTENT_URI;
    private MwiListAdapter mListAdapter;
    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mUnreadConvCount;
    private boolean mNeedToMarkAsSeen;
    private ModeCallback mActionModeListener = new ModeCallback();
    private ActionMode mActionMode;
    private int mDeleteCount;
    private String mDefaultCountryIso;

    public static Drawable mUnreadBackDrawable = null;
    private static final int MENU_MULTI_DELETE_MESSAGES = 9;
    private static final int MWI_LIST_QUERY_TOKEN = 0;
    private static final int UNREAD_MESSAGES_QUERY_TOKEN = 1;
    private static final int DELETE_MESSAGE_TOKEN = 2;
    /// M: add for display unread thread count
    private static final int MAX_DISPLAY_UNREAD_COUNT = 99;
    private static final String DISPLAY_UNREAD_COUNT_CONTENT_FOR_ABOVE_99 = "99+";
    private static final String UNSEEN_SELECTION = "seen=0";
    private static final String[] SEEN_PROJECTION = new String[] {
        "seen"
        };

    private static final String[] PROJECTION = new String[] {
        BaseColumns._ID,
        Mwi.MSG_ACCOUNT,
        Mwi.TO,
        Mwi.FROM,
        Mwi.SUBJECT,
        Mwi.MSG_DATE,
        Mwi.PRIORITY,
        Mwi.MSG_ID,
        Mwi.MSG_CONTEXT,
        /*Mwi.SEEN*/"seen", //fixme
        Mwi.READ,
        Mwi.GOT_CONTENT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListQueryHandler = new BackgroundQueryHandler(getContentResolver());
        setContentView(R.layout.mwi_list_activity);
        mListView = (ListView) findViewById(R.id.mwi_list);
        mTvEmpty = (TextView) findViewById(R.id.empty);
        initMwiList();
        setupActionBar();
        //mUnreadBackDrawable = ConversationList.getThemeDrawble(this);
        mDefaultCountryIso = MmsApp.getApplication().getCurrentCountryIso();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsShowing = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mNeedToMarkAsSeen = true;
        startMsgListQuery();
        startUnreadQuery();
    }
    @Override
    protected void onStop() {
        super.onStop();
        mIsShowing = false;
    }

    private void initMwiList() {
        if (mListAdapter != null) {
            return;
        }

        // Initialize the list adapter with a null cursor.
        mListAdapter = new MwiListAdapter(this, null);
        mListView.setAdapter(mListAdapter);
        mListView.setItemsCanFocus(false);
        mListView.setVisibility(View.VISIBLE);
        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                mActionMode = MwiListActivity.this.startActionMode(mActionModeListener);
                 Log.e(TAG, "OnItemLongClickListener");
                mActionModeListener.setItemChecked(position, true);
                if (mListAdapter != null) {
                    mListAdapter.notifyDataSetChanged();
                }
                return true;
            }
        });
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) {
                    return;
                }
                final MwiMessage msgItem = ((MwiListItem) view).getMessageItem();
                if (mActionMode != null) {
                    boolean checked = msgItem.isChecked();
                    mActionModeListener.setItemChecked(position, !checked);
                    if (mListAdapter != null) {
                        mListAdapter.notifyDataSetChanged();
                    }
                    return;
                }
                String messageDetails = getMwiMessageDetails(MwiListActivity.this, msgItem);
                AlertDialog.Builder b =
                        new AlertDialog.Builder(MwiListActivity.this, R.style.mwi_dialog);
                b.setTitle(msgItem.getMsgContextShow());
                LinearLayout ll = new LinearLayout(MwiListActivity.this);

                TextView tv = new TextView(MwiListActivity.this);
                tv.setPadding(15, 15, 15, 15);
//                tv.setTextSize(18);
                tv.setTextColor(Color.BLACK);
                tv.setAutoLinkMask(Linkify.ALL);
                tv.setLinksClickable(false);
                tv.setText(messageDetails);
                LinkClickLisenter linkClickListener = new LinkClickLisenter(tv);
                tv.setOnClickListener(linkClickListener);
                ll.addView(tv);

                b.setView(ll);

                b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                b.show();
                msgItem.markAsRead();

            }
        });
        mListAdapter.setDataSetChangeListener(mDataSetChangeListener);
    }

    private String getMwiMessageDetails(Context context, MwiMessage message) {
        return context.getString(R.string.from_label) + message.getFrom() + "\n"
                + context.getString(R.string.subject_label) + message.getSubject() + "\n"
                + context.getString(R.string.date_label) + message.getTimeDetail() + "\n"
                + context.getString(R.string.priority_label) + message.getPriorityShow() + "\n"
                + context.getString(R.string.message_id_label) + message.getMsgId() + "\n";
    }

    public static boolean isShowing() {
        return mIsShowing;
    }

    private void startMsgListQuery() {
        if (mMwiUri == null) {
            MmsLog.d(TAG, "startMsgListQuery: mMwiUri is null, bail!");
            return;
        }
        MmsLog.d(TAG, "startMsgListQuery");
        try {
            mListQueryHandler.startQuery(MWI_LIST_QUERY_TOKEN, null, mMwiUri, PROJECTION,
                    null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void startUnreadQuery() {
        if (mMwiUri == null) {
            MmsLog.d(TAG, "startUnreadQuery: mMwiUri is null, bail!");
            return;
        }
        MmsLog.d(TAG, "startUnreadQuery");
        try {
            // Kick off the new query
            mListQueryHandler.startQuery(UNREAD_MESSAGES_QUERY_TOKEN, null /* cookie */,
                    mMwiUri, PROJECTION, Mwi.READ + "=0", null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();

        ViewGroup v = (ViewGroup) LayoutInflater.from(this)
            .inflate(R.layout.conversation_list_actionbar, null);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(v,
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        mUnreadConvCount = (TextView) v.findViewById(R.id.unread_conv_count);
    }

    public void markAllMessagesAsSeen() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                blockingMarkAllMessagesAsSeen(MwiListActivity.this);
                    MwiMessagingNotification.cancelNotification(MwiListActivity.this,
                            MwiMessagingNotification.NOTIFICATION_ID);
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void blockingMarkAllMessagesAsSeen(final Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(Mwi.CONTENT_URI, SEEN_PROJECTION, UNSEEN_SELECTION,
                null, null);
        int count = 0;
        if (cursor != null) {
            try {
                count = cursor.getCount();
            } finally {
                cursor.close();
            }
        }
        if (count == 0) {
            return;
        }
        ContentValues values = new ContentValues(1);
        values.put("seen", 1);
        resolver.update(Mwi.CONTENT_URI, values, UNSEEN_SELECTION, null);
    }

    private MwiListAdapter.DataSetChangeListener mDataSetChangeListener =
            new MwiListAdapter.DataSetChangeListener() {

                @Override
                public void onContentChanged() {
                    startMsgListQuery();
                    startUnreadQuery();
                }

    };

    public static void confirmDeleteMessageDialog(final DeleteMessageListener listener,
            boolean selectedAll,
            Context context) {
        View contents = View.inflate(context, R.layout.delete_thread_dialog_view, null);
        TextView msg = (TextView) contents.findViewById(R.id.message);

        if (selectedAll) {
            msg.setText(R.string.confirm_delete_allmwi);
        } else {
            // Show the number of threads getting deleted in the confirmation dialog.
//            int cnt = msgIds.size();
            msg.setText(R.string.confirm_delete_selected_mwi);
//            msg.setText(context.getResources().getQuantityString(
//                R.plurals.confirm_delete_conversation, cnt, cnt));
        }

        final CheckBox checkbox = (CheckBox) contents.findViewById(R.id.delete_locked);
        checkbox.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setCancelable(true)
            .setPositiveButton(R.string.delete, listener)
            .setNegativeButton(R.string.no, null)
            .setView(contents)
            .show();
    }

    private final class BackgroundQueryHandler extends BaseProgressQueryHandler {
        private ProgressDialog mDialog;

        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        public void setProgressDialog(ProgressDialog dialog) {
            this.mDialog = dialog;
        }

        public void showProgressDialog() {
            if (mDialog != null) {
                mDialog.show();
            }
        }

        protected void dismissProgressDialog() {
            if (mDialog == null) {
                MmsLog.e(TAG, "mDialog is null!");
                return;
            }

            try {
                mDialog.dismiss();
            } catch (IllegalArgumentException e) {
                // if parent activity is destroyed,and code come here, will happen this.
                // just catch it.
                MmsLog.d(TAG, "ignore IllegalArgumentException");
            }
            mDialog = null;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            super.onQueryComplete(token, cookie, cursor);
            if (cursor == null) {
                return;
            }
            switch(token) {
            case MWI_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                if (cursor.getCount() == 0) {
                    mTvEmpty.setVisibility(View.VISIBLE);
                    mListView.setVisibility(View.GONE);
                } else {
                    mTvEmpty.setVisibility(View.GONE);
                    mListView.setVisibility(View.VISIBLE);
                }
                if (mNeedToMarkAsSeen) {
                    mNeedToMarkAsSeen = false;
                    markAllMessagesAsSeen();
                }
                break;
            case UNREAD_MESSAGES_QUERY_TOKEN:
                int count = 0;
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            count = cursor.getCount();
                            MmsLog.d(TAG, "get unread message count = " + count);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                /// M: modified for unread count display
                if (count > MAX_DISPLAY_UNREAD_COUNT) {
                    mUnreadConvCount.setText(DISPLAY_UNREAD_COUNT_CONTENT_FOR_ABOVE_99);
                    mUnreadConvCount.setVisibility(View.VISIBLE);
                } else if (count > 0) {
                    mUnreadConvCount.setText(Integer.toString(count));
                    mUnreadConvCount.setVisibility(View.VISIBLE);
                } else {
                    mUnreadConvCount.setVisibility(View.GONE);
                }
                break;
            default:
                break;
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            if (token == DELETE_MESSAGE_TOKEN && cookie instanceof HashSet<?>) {
                HashSet<Long> msgIds = (HashSet<Long>) cookie;
                mListAdapter.deleteCachedItems(msgIds);
            }

            switch (token) {
                case DELETE_MESSAGE_TOKEN:
                    // Update the notification for new messages since they
                    // may be deleted.
                    if (mDeleteCount > 1) {
                        mDeleteCount--;
                        MmsLog.d(TAG, "igonre a onDeleteComplete,mDeleteCount:" + mDeleteCount);
                        return;
                    }
                    mDeleteCount = 0;
                    dismissProgressDialog();
                    break;
                default:
                    break;
            }
        }
    }

    private class ModeCallback implements ActionMode.Callback {
        private View mMultiSelectActionBarView;
        private Button mSelectionTitle;
        private HashSet<Long> mSelectedMsgIds;
        private HashSet<Integer> mCheckedPosition;
        private int mCheckedNum = 0;
        private MenuItem mDeleteItem;
        private DropDownMenu mSelectionMenu;
        private MenuItem mSelectionMenuItem;

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mSelectedMsgIds = new HashSet<Long>();
            mCheckedPosition = new HashSet<Integer>();
            mDeleteItem = menu.add(0, MENU_MULTI_DELETE_MESSAGES, 0, R.string.delete_message)
                    .setIcon(R.drawable.ic_menu_trash_holo_dark)
                    .setTitle(R.string.delete_message);
            mDeleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (mMultiSelectActionBarView == null) {
                mMultiSelectActionBarView = LayoutInflater.from(MwiListActivity.this).inflate(
                        R.layout.conversation_list_multi_select_actionbar2, null);
                mSelectionTitle = (Button) mMultiSelectActionBarView
                        .findViewById(R.id.selection_menu);
            }
            mode.setCustomView(mMultiSelectActionBarView);
            mSelectionTitle.setText(R.string.select_conversations);
            mListView.setLongClickable(false);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (mMultiSelectActionBarView == null) {
                ViewGroup v = (ViewGroup) LayoutInflater.from(MwiListActivity.this).inflate(
                        R.layout.conversation_list_multi_select_actionbar2, null);
                mode.setCustomView(v);
                mSelectionTitle = (Button) mMultiSelectActionBarView
                        .findViewById(R.id.selection_menu);
            }
            CustomMenu customMenu = new CustomMenu(MwiListActivity.this);
            mSelectionMenu = customMenu.addDropDownMenu(mSelectionTitle, R.menu.selection);
            mSelectionMenuItem = mSelectionMenu.findItem(R.id.action_select_all);
            customMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (mListAdapter.isAllSelected()) {
                        setAllItemChecked(mActionMode, false);
                    } else {
                        setAllItemChecked(mActionMode, true);
                    }
                    return false;
                }
            });
            return true;
        }

        public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case MENU_MULTI_DELETE_MESSAGES:
                    if (mSelectedMsgIds.size() > 0) {
                        DeleteMessageListener l = new DeleteMessageListener(mSelectedMsgIds,
                                mListQueryHandler, MwiListActivity.this, mode);
                        confirmDeleteMessageDialog(l,
                                mSelectedMsgIds.size() == mListView.getCount(),
                                MwiListActivity.this);
                    } else {
                        item.setEnabled(false);
                    }
                    break;

                default:
                    if (mCheckedPosition != null && mCheckedPosition.size() > 0) {
                        mCheckedPosition.clear();
                    }
                    break;
            }
            return true;
        }

        public void onDestroyActionMode(ActionMode mode) {
            mListAdapter.uncheckSelect(mCheckedPosition);
            mCheckedPosition = null;
            mSelectedMsgIds = null;
            mListView.setLongClickable(true);
            mCheckedNum = 0;
            mActionMode = null;
            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
        }

        public void setItemChecked(int position, boolean checked) {
            Cursor cursor = (Cursor) mListView.getItemAtPosition(position);
            long msgId =
                cursor.getLong(cursor.getColumnIndex(MwiMessage.Columns.Id.getColumnName()));
            MwiMessage msgItem = mListAdapter.getCachedMessageItem(msgId, cursor);
            if (checked == msgItem.isChecked()) {
                return;
            }
            msgItem.setChecked(checked);
            if (checked) {
                mSelectedMsgIds.add(msgId);
                mCheckedPosition.add(position);
                mCheckedNum++;
            } else {
                mSelectedMsgIds.remove(msgId);
                mCheckedPosition.remove(position);
                mCheckedNum--;
            }

            if (mDeleteItem != null) {
                if (mCheckedNum > 0) {
                    mDeleteItem.setEnabled(true);
                } else {
                    mDeleteItem.setEnabled(false);
                }
            }
            if (mCheckedNum == 0 && mActionMode != null) {
                mActionMode.finish();
            }
            mSelectionTitle.setText(MwiListActivity.this.getResources().getQuantityString(
                    R.plurals.message_view_selected_message_count, mCheckedNum, mCheckedNum));
            updateSelectionTitle();
        }

        private void setAllItemChecked(ActionMode mode, boolean checked) {
            int num = mListAdapter.getCount();
            for (int position = 0; position < num; position++) {
                setItemChecked(position, checked);
            }
            if (checked) {
                mDeleteItem.setEnabled(true);
            } else {
                mDeleteItem.setEnabled(false);
            }
            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
        }

        public void confirmSyncCheckedPositons() {
            mCheckedPosition.clear();
            mSelectedMsgIds.clear();
            int num = mListView.getCount();

            for (int position = 0; position < num; position++) {
                Cursor cursor = (Cursor) mListView.getItemAtPosition(position);
                long msgId =
                    cursor.getLong(cursor.getColumnIndex(MwiMessage.Columns.Id.getColumnName()));
                MwiMessage msgItem = mListAdapter.getCachedMessageItem(msgId, cursor);
                if (msgItem.isChecked()) {
                    mCheckedPosition.add(position);
                    mSelectedMsgIds.add(msgId);
                }
            }
            mCheckedNum = mCheckedPosition.size();
            mSelectionTitle.setText(MwiListActivity.this.getResources().getQuantityString(
                    R.plurals.message_view_selected_message_count, mCheckedNum, mCheckedNum));
            updateSelectionTitle();
        }

        private void updateSelectionTitle() {
            if (mSelectionMenuItem != null) {
                if (mListAdapter.isAllSelected()) {
                    mSelectionMenuItem.setTitle(R.string.unselect_all);
                } else {
                    mSelectionMenuItem.setTitle(R.string.select_all);
                }
            }
        }
    }

    private class DeleteMessageListener implements OnClickListener {
        private final HashSet<Long> mMsgIds;
        private final AsyncQueryHandler mHandler;
        private final Context mContext;
        private ActionMode mMode;
        public DeleteMessageListener(HashSet<Long> msgIds, AsyncQueryHandler handler,
                Context context, ActionMode mode) {
            mMsgIds = msgIds;
            mHandler = handler;
            mContext = context;
            mMode = mode;
        }
        private void showProgressDialog() {
            if (mHandler instanceof BackgroundQueryHandler) {
                ((BackgroundQueryHandler) mHandler).setProgressDialog(
                        getProgressDialog(mContext));
                ((BackgroundQueryHandler) mHandler).showProgressDialog();
            }
        }
        public void onClick(DialogInterface dialog, int whichButton) {
            if (mMode != null) {
                mMode.finish();
                mMode = null;
            }
            showProgressDialog();

            if (mMsgIds == null) {
                Uri deleteUri = Mwi.CONTENT_URI;
                mListQueryHandler.startDelete(DELETE_MESSAGE_TOKEN, null, deleteUri,
                        null, null);
            } else {
                mDeleteCount = 0;
                for (long msgId : mMsgIds) {
                    mDeleteCount++;
                    Uri deleteUri = ContentUris.withAppendedId(Mwi.CONTENT_URI, msgId);
                    mListQueryHandler.startDelete(DELETE_MESSAGE_TOKEN, mMsgIds, deleteUri,
                            null, null);
                }
            }
            dialog.dismiss();
        }
    }

    /**
     * Gets a delete progress dialog.
     * @param context the activity context.
     * @return the delete progress dialog.
     */
    public static ProgressDialog getProgressDialog(Context context) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setCancelable(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(context.getString(R.string.deleting));
        dialog.setMax(1); /* default is one complete */
        // ignore the search key, when deleting we do not want the search bar come out.
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return (keyCode == KeyEvent.KEYCODE_SEARCH);
            }
        });
        return dialog;
    }

    private class LinkClickLisenter implements android.view.View.OnClickListener {
        private TextView mTv;
        public LinkClickLisenter(TextView tv) {
            mTv = tv;
        }

        @Override
        public void onClick(View view1) {
            boolean isTel = false;
            final Context context = MwiListActivity.this;

            // Check for links. If none, do nothing; if 1, open it; if >1, ask user to pick one
            final URLSpan[] spans = mTv.getUrls();
            final java.util.ArrayList<String> urls = MessageUtils.extractUris(spans);
            final String telPrefix = "tel:";
            String url = "";
            /// uri_size sync according to urls after filter to unique array
            for (int i = 0; i < urls.size(); i++) {
                url = urls.get(i);
                if (url.startsWith(telPrefix)) {
                    isTel = true;
                    urls.add("smsto:" + url.substring(telPrefix.length()));
                }
            }

            if (spans.length == 0) {
                //sendMessage(mMessageItem, MSG_LIST_DETAILS);    // show the message details dialog
            } else if (spans.length == 1 && !isTel) {
                final String mUriTemp = spans[0].getURL();
                ///M: use new plugin instead of. @{
                MmsConfig.openUrl(context, mUriTemp);
                /// @}
            }  else {
                ArrayAdapter<String> adapter =
                    new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, urls) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        TextView tv = (TextView) v;
                            String url = getItem(position).toString();
                            Uri uri = Uri.parse(url);

                            final String telPrefix = "tel:";
                            Drawable d = null;
                            try {
                                d = context.getPackageManager().getActivityIcon(
                                        new Intent(Intent.ACTION_VIEW, uri));
                            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                                // go on, it is ok.
                            }
                            if (d != null) {
                                d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                                tv.setCompoundDrawablePadding(10);
                                tv.setCompoundDrawables(d, null, null, null);
                            } else {
                                if (url.startsWith(telPrefix)) {
                                    d = context.getResources().getDrawable(R.drawable.ic_launcher_phone);
                                    d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                                    tv.setCompoundDrawablePadding(10);
                                    tv.setCompoundDrawables(d, null, null, null);
                                } else {
                                    tv.setCompoundDrawables(null, null, null, null);
                                }
                            }

                            final String smsPrefix = "smsto:";
                            final String mailPrefix = "mailto";
                            if (url.startsWith(telPrefix)) {
                                url = PhoneNumberUtils.formatNumber(
                                                url.substring(telPrefix.length()), mDefaultCountryIso);
                                if (url == null) {
                                    MmsLog.w(TAG, "url turn to null after calling PhoneNumberUtils.formatNumber");
                                    url = getItem(position).toString().substring(telPrefix.length());
                                }
                            } else if (url.startsWith(smsPrefix)) {
                                url = PhoneNumberUtils.formatNumber(
                                                url.substring(smsPrefix.length()), mDefaultCountryIso);
                                if (url == null) {
                                    MmsLog.w(TAG, "url turn to null after calling PhoneNumberUtils.formatNumber");
                                    url = getItem(position).toString().substring(smsPrefix.length());
                                }
                            }
                            tv.setText(url);
                        return v;
                    }
                };

                AlertDialog.Builder b = new AlertDialog.Builder(MwiListActivity.this);

                DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        if (which >= 0) {
                            Uri uri = Uri.parse(urls.get(which));
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                            if (urls.get(which).startsWith("smsto:")) {
                                intent.setClassName(context, "com.android.mms.ui.SendMessageToActivity");
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                            context.startActivity(intent);
                            if (urls.get(which).startsWith("smsto:")) {
                                intent.setClassName(context, "com.android.mms.ui.SendMessageToActivity");
                            }
                        }
                        dialog.dismiss();
                    }
                };

                b.setTitle(R.string.select_link_title);
                b.setCancelable(true);
                b.setAdapter(adapter, click);

                b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                b.show();
            }
        }

    };
}
