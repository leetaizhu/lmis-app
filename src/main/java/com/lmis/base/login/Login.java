/*
 * OpenERP, Open Source Management Solution
 * Copyright (C) 2012-today OpenERP SA (<http:www.openerp.com>)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 * 
 */
package com.lmis.base.login;

import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.lmis.auth.LmisAccountManager;
import com.lmis.orm.LmisHelper;
import com.lmis.support.AppScope;
import com.lmis.support.JSONDataHelper;
import com.lmis.support.LmisDialog;
import com.lmis.support.LmisUser;
import com.lmis.support.fragment.FragmentListener;
import com.lmis.R;
import com.lmis.support.BaseFragment;
import com.lmis.util.controls.LmisEditText;
import com.lmis.util.drawer.DrawerItem;

/**
 * The Class Login.
 */
public class Login extends BaseFragment {

    /**
     * The item arr.
     */
    String[] itemArr = null;

    /**
     * The context.
     */
    Context context = null;

    /**
     * The m action mode.
     */
    ActionMode mActionMode;

    /**
     * The open erp server url.
     */
    String lmisServerURL = "";

    /**
     * The edt server url.
     */
    LmisEditText edtServerUrl = null;

    /**
     * The arguments.
     */
    Bundle arguments = null;

    /**
     * The db list spinner.
     */
    Spinner dbListSpinner = null;

    /**
     * The root view.
     */
    View rootView = null;

    /**
     * The login user a sync.
     */
    LoginUser loginUserASync = null;

    /**
     * The edt username.
     */
    LmisEditText edtUsername = null;

    /**
     * The edt password.
     */
    LmisEditText edtPassword = null;

    /**
     * The Lmis Object
     */
    LmisHelper lmis = null;

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater,
     * android.view.ViewGroup, android.os.Bundle)
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        this.context = getActivity();
        scope = new AppScope(this);
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_login, container, false);
        dbListSpinner = (Spinner) rootView.findViewById(R.id.lstDatabases);
        this.handleArguments((Bundle) getArguments());
        getActivity().setTitle("Login");
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
        getActivity().getActionBar().setHomeButtonEnabled(false);
        edtUsername = (LmisEditText) rootView.findViewById(R.id.edtUsername);
        edtPassword = (LmisEditText) rootView.findViewById(R.id.edtPassword);
        edtPassword.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    goNext();
                }
                return false;
            }
        });
        return rootView;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.lmis.support.FragmentHelper#handleArguments(android.os.Bundle)
     */
    public void handleArguments(Bundle bundle) {
        arguments = bundle;
        if (arguments != null && arguments.size() > 0) {
            if (arguments.containsKey("LmisServerURL")) {
                lmisServerURL = arguments.getString("LmisServerURL");
            }
        }
        try {
            lmis = new LmisHelper(context, lmisServerURL);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu,
     * android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_login, menu);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem
     * )
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle item selection

        switch (item.getItemId()) {
            case R.id.menu_login_account:
                Log.d("LoginFragment()->ActionBarMenuClicked", "menu_login_account");
                goNext();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void goNext() {
        edtUsername.setError(null);
        edtPassword.setError(null);
        if (TextUtils.isEmpty(edtUsername.getText())) {
            edtUsername.setError("Provide Username");
        } else if (TextUtils.isEmpty(edtPassword.getText())) {
            edtPassword.setError("Provide Password");
        } else {
            loginUserASync = new LoginUser();
            loginUserASync.execute((Void) null);
        }
    }

    /**
     * The Class LoginUser.
     */
    private class LoginUser extends AsyncTask<Void, Void, Boolean> {

        /**
         * The pdialog.
         */
        LmisDialog pdialog;

        /**
         * The error msg.
         */
        String errorMsg = "";

        /**
         * The user data.
         */
        LmisUser userData = null;

        /*
         * (non-Javadoc)
         *
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            pdialog = new LmisDialog(getActivity(), false, "Logging in...");
            pdialog.show();
            edtPassword.setError(null);
        }

        /*
         * (non-Javadoc)
         *
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // Simulate network access.
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return false;
            }
            if (lmis != null) {
                String userName = edtUsername.getText().toString();
                String password = edtPassword.getText().toString();
                userData = lmis.login(userName, password, lmisServerURL);
                if (userData != null) {
                    return true;
                } else {
                    errorMsg = "Invalid Username or Password !";
                    return false;
                }
            }

            return false;
        }

        /*
         * (non-Javadoc)
         *
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(final Boolean success) {
            if (success) {
                Log.d("Creating Account For Username :", userData.getAndroidName());
                if (LmisAccountManager.fetchAllAccounts(getActivity()) != null) {
                    if (LmisAccountManager.isAnyUser(getActivity())) {
                        LmisAccountManager.logoutUser(getActivity(), LmisAccountManager.currentUser(getActivity()).getAndroidName());
                    }
                }
                if (LmisAccountManager.createAccount(getActivity(), userData)) {
                    loginUserASync.cancel(true);
                    pdialog.hide();
                    SyncWizard syncWizard = new SyncWizard();
                    FragmentListener mFragment = (FragmentListener) getActivity();
                    mFragment.startMainFragment(syncWizard, false);

                }
            } else {
                edtPassword.setError(errorMsg);
            }
            loginUserASync.cancel(true);
            pdialog.hide();
        }

        /*
         * (non-Javadoc)
         *
         * @see android.os.AsyncTask#onCancelled()
         */
        @Override
        protected void onCancelled() {
            loginUserASync.cancel(true);
            pdialog.hide();
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.Fragment#onDestroyView()
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null; // now cleaning up!
    }

    @Override
    public Object databaseHelper(Context context) {
        return null;
    }

    @Override
    public List<DrawerItem> drawerMenus(Context context) {
        return null;
    }
}
