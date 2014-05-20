package com.lmis.addons.expense;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import com.lmis.LmisArguments;
import com.lmis.orm.LmisDataRow;
import com.lmis.orm.LmisHelper;
import com.lmis.orm.LmisValues;
import com.lmis.providers.expense.ExpenseProvider;
import com.lmis.receivers.DataSetChangeReceiver;
import com.lmis.support.AppScope;
import com.lmis.support.BaseFragment;
import com.lmis.support.listview.LmisListAdapter;
import com.lmis.util.Base64Helper;
import com.lmis.util.LmisDate;
import com.lmis.util.drawer.DrawerItem;
import com.lmis.util.drawer.DrawerListener;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ExpenseDetail extends BaseFragment {

    public static final String TAG = "ExpenseDetail";
    View mView = null;
    Integer mExpenseId = null;
    LmisDataRow mExpenseData = null;
    ListView mExpenseLinesView = null;
    LmisListAdapter mExpenseLinesAdapter = null;

    //message列表
    ListView mMessageListView = null;
    LmisListAdapter mMessageListAdapter = null;

    //是否已操作
    Boolean mProcessed = false;
    //费用单明细
    List<Object> mExpenseLines = new ArrayList<Object>();
    //审批记录对象
    List<Object> mMessages = new ArrayList<Object>();

    //工作流程记录对象
    MessagesLoader mMessagesLoader = null;
    //工作流审批对象
    WorkflowOperation mWorkflowOperation = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mView = inflater.inflate(com.lmis.R.layout.fragment_expense_detail_view, container, false);
        scope = new AppScope(getActivity());
        init();
        return mView;
    }

    private void init() {
        Log.d(TAG, "ExpenseDetail->init()");
        Bundle bundle = getArguments();
        if (bundle != null) {
            mExpenseId = bundle.getInt("expense_id");
            mExpenseData = db().select(mExpenseId);
            mProcessed = mExpenseData.getBoolean("processed");

            List<LmisDataRow> lines = mExpenseData.getO2MRecord("line_ids").browseEach();
            for (Object l : lines) {
                mExpenseLines.add(l);
            }
            initControls();
            initLstMessages();
            initData();
        }
    }

    private void initControls() {
        //设置主表内容
        TextView txvName, txvStatus, txvEmployee, txvDate, txvAmount;
        txvName = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseName);
        txvEmployee = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseEmployee);
        txvDate = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseDate);
        txvAmount = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseAmount);

        String name = mExpenseData.getString("name");
        String state = mExpenseData.getString("state");
        String status = getStatus(state);
        txvName.setText(name + "(" + status + ")");

        LmisDataRow employee = mExpenseData.getM2ORecord("employee_id").browse();
        txvEmployee.setText(employee.getString("name"));
        String date = mExpenseData.getString("date");
        txvDate.setText(date);
        String amount = mExpenseData.getString("amount");
        txvAmount.setText("合计：" + amount);

        mExpenseLinesView = (ListView) mView.findViewById(com.lmis.R.id.lstLineIds);
        mExpenseLinesAdapter = new LmisListAdapter(getActivity(), com.lmis.R.layout.fragment_expense_detail_expense_lines, mExpenseLines) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View mView = convertView;
                if (mView == null)
                    mView = getActivity().getLayoutInflater().inflate(getResource(), parent, false);

                mView = createListViewRow(mView, position);
                return mView;
            }
        };
        mExpenseLinesView.setAdapter(mExpenseLinesAdapter);
    }

    //初始化message_ids list
    private void initLstMessages() {
        mMessageListView = (ListView) mView.findViewById(com.lmis.R.id.lstMessages);
        mMessageListAdapter = new LmisListAdapter(getActivity(),
                com.lmis.R.layout.fragment_message_ids_listview_items,
                mMessages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View mView = convertView;
                if (mView == null)
                    mView = getActivity().getLayoutInflater().inflate(
                            getResource(), parent, false);
                mView = createListMessagesViewRow(mView, position);
                return mView;
            }
        };
        mMessageListView.setAdapter(mMessageListAdapter);
    }

    @SuppressLint("CutPasteId")
    private View createListMessagesViewRow(View mView, final int position) {

        final LmisDataRow row = (LmisDataRow) mMessages.get(position);
        TextView txvAuthor, txvEmail, txvTime;
        final TextView txvVoteNumber;
        txvAuthor = (TextView) mView.findViewById(com.lmis.R.id.txvMessageAuthor);
        txvEmail = (TextView) mView.findViewById(com.lmis.R.id.txvAuthorEmail);
        txvTime = (TextView) mView.findViewById(com.lmis.R.id.txvTime);

        String author = row.getString("email_from");
        String email = author;
        LmisDataRow author_id = null;
        if (author.equals("false")) {
            author_id = row.getM2ORecord("author_id").browse();
            if (author_id != null) {
                author = author_id.getString("name");
                email = author_id.getString("email");
            }
        }
        txvAuthor.setText(author);
        txvEmail.setText(email);

        txvTime.setText(LmisDate.getDate(row.getString("date"), TimeZone
                .getDefault().getID(), "MM dd, yyyy,  hh:mm a"));

        Log.d(TAG, "message body : " + row.getString("body"));
        WebView webView = (WebView) mView.findViewById(com.lmis.R.id.webViewMessageBody);
        //FIXME 此处参考了
        //http://wj495175289.blog.163.com/blog/static/1620826662012364512840/
        //对webview处理乱码的方法
        webView.loadData(row.getString("body"), "text/html;charset=UTF-8", null);

        ImageView imgUserPicture;
        imgUserPicture = (ImageView) mView.findViewById(com.lmis.R.id.imgUserPicture);

        if (author_id != null
                && !author_id.getString("image_small").equals("false")) {
            imgUserPicture.setImageBitmap(Base64Helper.getBitmapImage(
                    getActivity(), author_id.getString("image_small")));
        }

        return mView;
    }

    @SuppressLint("CutPasteId")
    private View createListViewRow(View mView, final int position) {
        final LmisDataRow row = (LmisDataRow) mExpenseLines.get(position);
        TextView txvName, txvQuantity, txvTotalAmount;
        txvName = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseLineName);
        txvQuantity = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseLineUnitQuantity);
        txvTotalAmount = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseLineTotalAmount);

        String name = row.getString("name");
        String quantity = row.getString("unit_quantity");
        String total_amount = row.getString("total_amount");
        //txvNo.setText(1);
        txvName.setText(name);
        txvQuantity.setText(quantity);
        txvTotalAmount.setText(total_amount);

        return mView;
    }

    private void initData() {
        mMessagesLoader = new MessagesLoader();
        mMessagesLoader.execute((Void) null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(com.lmis.R.menu.menu_fragment_expense_detail, menu);
        setMenuVisible(menu, !mProcessed);
    }

    //workflow处理完毕后,需要禁用审核按钮
    private void setMenuVisible(Menu menu, Boolean visible) {
        MenuItem item_ok = menu.findItem(com.lmis.R.id.menu_expense_detail_audit);
        MenuItem item_cancel = menu.findItem(com.lmis.R.id.menu_expense_detail_cancel);
        item_ok.setVisible(visible);
        item_cancel.setVisible(visible);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        setMenuVisible(menu, !mProcessed);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // handle item selection
        switch (item.getItemId()) {
            case com.lmis.R.id.menu_expense_detail_audit:
                Log.d(TAG, "ExpenseDetail#onOptionsItemSelected#ok");
                // 编写审批代码
                String signal = mExpenseData.getString("next_workflow_signal");
                mWorkflowOperation = new WorkflowOperation(signal);
                mWorkflowOperation.execute();
                return true;
            case com.lmis.R.id.menu_expense_detail_cancel:
                Log.d(TAG, "ExpenseDetail#onOptionsItemSelected#cancel");
                // 编写cancel代码
                mWorkflowOperation = new WorkflowOperation("refuse");
                mWorkflowOperation.execute();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        scope.context().registerReceiver(datasetChangeReceiver, new IntentFilter(DataSetChangeReceiver.DATA_CHANGED));
    }

    @Override
    public void onPause() {
        super.onPause();
        scope.context().unregisterReceiver(datasetChangeReceiver);
    }

    private DataSetChangeReceiver datasetChangeReceiver = new DataSetChangeReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String id = intent.getExtras().getString("id");
                String model = intent.getExtras().getString("model");
                if (model.equals("hr.expense.expense") && mExpenseId == Integer.parseInt(id)) {
                    Log.d(TAG, "ExpenseDetail->datasetChangeReceiver@onReceive");
                    LmisDataRow row = db().select(Integer.parseInt(id));
                    mExpenseData = row;
                    //更新界面上state的显示
                    String name = mExpenseData.getString("name");
                    String state = mExpenseData.getString("state");
                    String status = getStatus(state);
                    TextView txvName = (TextView) mView.findViewById(com.lmis.R.id.txvExpenseName);

                    txvName.setText(name + "(" + status + ")");
                }
            } catch (Exception e) {
            }

        }
    };

    private String getStatus(String state) {
        int id = scope.main().getResources().getIdentifier("state_" + state, "string", scope.main().getPackageName());
        String value = id == 0 ? "" : scope.main().getResources().getString(id);
        return value;
    }


    public class MessagesLoader extends AsyncTask<Void, Void, Boolean> {

        public MessagesLoader() {
            mView.findViewById(com.lmis.R.id.loadingProgress).setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            String where = "res_id = ? and model = ?";
            String[] whereArgs = new String[]{mExpenseId + "", "hr.expense.expense"};
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mView.findViewById(com.lmis.R.id.loadingProgress).setVisibility(View.GONE);
            mMessageListAdapter.notifiyDataChange(mMessages);
            mMessagesLoader = null;
        }
    }

    /*
     *工作流处理类,用于异步处理工作流,处理过程如下:
     *1 用户点击[通过]或[不通过]按钮
     *2 系统异步调用服务端的exec_workflow
     *3 系统更新db中的操作状态为已操作,同时从服务器获取expense的最后状态,并更新到本地
     *4 将审批通过按钮设置为disable
     *5 使用Toast提示用户操作完成
     *6 更新drawer的状态
     * */
    public class WorkflowOperation extends AsyncTask<Void, Void, Boolean> {
        boolean isConnection = true;
        LmisHelper mOE = null;
        ProgressDialog mProgressDialog = null;
        String mSignal = null;

        public WorkflowOperation(String signal) {
            mSignal = signal;
            mOE = db().getOEInstance();
            if (mOE == null)
                isConnection = false;

            String working_text = scope.main().getResources().getString(com.lmis.R.string.working_text);
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMessage(working_text);
            if (isConnection) {
                mProgressDialog.show();
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Boolean execSuccess = false;
            if (!isConnection) {
                return false;
            }
            LmisArguments arguments = new LmisArguments();
            // Param 1 : model_name
            String modelName = "hr.expense.expense";
            // Param 2 : res_id
            Integer resId = mExpenseId;
            //params 3 : signal
            try {
                //mOE.exec_workflow(modelName, resId, mSignal);
                execSuccess = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Creating Local Database Requirement Values
            LmisValues values = new LmisValues();
            String value = (execSuccess) ? "true" : "false";
            mProcessed = true;
            values.put("processed", value);

            if (execSuccess) {
                try {
                    db().update(values, mExpenseId);
                } catch (Exception e) {
                }
            }
            return execSuccess;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                //刷新菜单栏
                scope.main().supportInvalidateOptionsMenu();

                //重新同步数据
                scope.main().requestSync(ExpenseProvider.AUTHORITY);

                DrawerListener drawer = (DrawerListener) getActivity();
                drawer.refreshDrawer(Expense.TAG);

                String toast_text = scope.main().getResources().getString(com.lmis.R.string.expense_processed_text);
                Toast.makeText(getActivity(), toast_text, Toast.LENGTH_LONG).show();

                Toast.makeText(getActivity(), "expense has processed", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "No connection", Toast.LENGTH_LONG).show();
            }
            mProgressDialog.dismiss();
        }
    }


    @Override
    public Object databaseHelper(Context context) {
        return new ExpenseDBHelper(context);
    }

    @Override
    public List<DrawerItem> drawerMenus(Context context) {
        return null;
    }
}
