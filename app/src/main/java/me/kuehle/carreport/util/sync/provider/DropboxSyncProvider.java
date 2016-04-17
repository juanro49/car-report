/*
 * Copyright 2015 Jan Kühle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kuehle.carreport.util.sync.provider;

import android.accounts.Account;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import me.kuehle.carreport.Application;
import me.kuehle.carreport.BuildConfig;
import me.kuehle.carreport.R;
import me.kuehle.carreport.gui.AuthenticatorAddAccountActivity;
import me.kuehle.carreport.util.FileCopyUtil;
import me.kuehle.carreport.util.sync.AbstractSyncProvider;

public class DropboxSyncProvider extends AbstractSyncProvider {
    private static final String TAG = "DropboxSyncProvider";

    private static final String APP_KEY = "a6edub2n9b029if";

    private DbxClientV2 mDbxClient;

    @Override
    public long getId() {
        return 1;
    }

    @Override
    public int getIcon() {
        return R.drawable.ic_c_sync_dropbox_64dp;
    }

    @Override
    public String getName() {
        return "Dropbox";
    }

    @Override
    public void setup(@Nullable Account account, @Nullable String password, @Nullable String authToken, @Nullable JSONObject settings) {
        if (authToken == null) {
            mDbxClient = null;
        } else {
            String userLocale = Locale.getDefault().toString();
            DbxRequestConfig requestConfig = new DbxRequestConfig(BuildConfig.APPLICATION_ID, userLocale);
            mDbxClient = new DbxClientV2(requestConfig, authToken);
        }
    }

    @Override
    public void startAuthentication(AuthenticatorAddAccountActivity activity) {
        Auth.startOAuth2Authentication(activity, APP_KEY);
    }

    @Override
    public void continueAuthentication(final AuthenticatorAddAccountActivity activity,
                                       final int requestCode, final int resultCode,
                                       final @Nullable Intent data) {
        final String accessToken = Auth.getOAuth2Token();
        if (accessToken != null) {
            try {
                setup(null, null, accessToken, null);

                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        try {
                            return mDbxClient.users().getCurrentAccount().getName().getDisplayName();
                        } catch (DbxException e) {
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        activity.onAuthenticationResult(result, null, accessToken, null);
                    }
                }.execute();
            } catch (IllegalStateException e) {
                activity.onAuthenticationResult(null, null, null, null);
            }
        } else {
            activity.onAuthenticationResult(null, null, null, null);
        }
    }

    @Override
    public String getRemoteFileRev() throws Exception {
        try {
            File localFile = getLocalFile();
            Metadata remoteMetadata = mDbxClient.files().getMetadata("/" + localFile.getName());

            if (remoteMetadata instanceof FileMetadata) {
                return ((FileMetadata) remoteMetadata).getRev();
            }
        } catch (DbxException e) {
            throw new Exception(e);
        }

        return null;
    }

    @Override
    public String uploadFile() throws Exception {
        File localFile = getLocalFile();
        File tempFile = new File(Application.getContext().getCacheDir(), getClass().getSimpleName());
        if (!FileCopyUtil.copyFile(localFile, tempFile)) {
            throw new Exception();
        }

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(tempFile);
            FileMetadata remoteMetadata = mDbxClient.files()
                    .uploadBuilder("/" + localFile.getName())
                    .withMode(WriteMode.OVERWRITE)
                    .start()
                    .uploadAndFinish(inputStream);
            return remoteMetadata.getRev();
        } catch (DbxException | FileNotFoundException e) {
            throw new Exception(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close input stream after uploading file.", e);
                }
            }

            if (!tempFile.delete()) {
                Log.w(TAG, "Could not delete temp file after uploading.");
            }
        }
    }

    @Override
    public void downloadFile() throws Exception {
        File localFile = getLocalFile();
        File tempFile = new File(Application.getContext().getCacheDir(), getClass().getSimpleName());

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(tempFile);
            mDbxClient.files()
                    .download("/" + localFile.getName())
                    .download(outputStream);
            if (!FileCopyUtil.copyFile(tempFile, localFile)) {
                throw new Exception();
            }
        } catch (DbxException | FileNotFoundException e) {
            throw new Exception(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close output stream after downloading file.", e);
                }
            }

            if (!tempFile.delete()) {
                Log.w(TAG, "Could not delete temp file after downloading.");
            }
        }
    }
}
