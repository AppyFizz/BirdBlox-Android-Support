package com.birdbraintechnologies.birdblocks.Dropbox;

import android.os.AsyncTask;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.Project.UnzipTask;
import com.birdbraintechnologies.birdblocks.Util.ProgressOutputStream;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.birdbraintechnologies.birdblocks.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblocks.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblocks.httpservice.RequestHandlers.FileManagementHandler.findAvailableName;
import static com.birdbraintechnologies.birdblocks.httpservice.RequestHandlers.FileManagementHandler.getBirdblocksDir;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxDownloadTask extends AsyncTask<String, Long, String> {
    private final String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;

    public DropboxDownloadTask(DbxClientV2 dropboxClient) {
        super();
        this.dropboxClient = dropboxClient;
    }

    @Override
    protected String doInBackground(String... names) {
        /**
         * Implemented own {@link ProgressOutputStream}, since Dropbox API V2 has no built-in download progress.
         */
        try {
            final String name = names[0];
            File dbxDown = new File(mainWebViewContext.getFilesDir(), name + ".bbx");
            try {
                dropboxClient.files().getMetadata("/" + name + ".bbx");
            } catch (GetMetadataErrorException e) {
                if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                    Log.e(TAG, "Download: File " + name + " not found.");
                    // TODO: Display this error to the user
                    JSONObject obj = dropboxAppFolderContents();
                    if (obj != null)
                        runJavascript("CallbackManager.cloud.filesChanged(" + obj.toString() + ")");
                } else {
                    throw e;
                }
            }
            if (!dbxDown.exists()) {
                dbxDown.getParentFile().mkdirs();
                dbxDown.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(dbxDown);
            FileMetadata downloadData = null;
            try {
                DbxDownloader<FileMetadata> dbxDownloader = dropboxClient.files().download("/" + name + ".bbx");
                long size = dbxDownloader.getResult().getSize();
                downloadData = dbxDownloader.download(new ProgressOutputStream(size, fout, new ProgressOutputStream.Listener() {
                    @Override
                    public void progress(long completed, long totalSize) {
                        publishProgress((long) ((completed / (double) totalSize) * 100));
                        // Escape early if cancel() is called
                        // if (isCancelled())
                    }
                }));
                return name;
            } finally {
                fout.close();
                if (downloadData != null)
                    Log.d(TAG, "MetadataDownload: " + downloadData);
            }
        } catch (DbxException | IOException | SecurityException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to download file: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // TODO: Display download progress dialog to the user
        // Might help: https://stackoverflow.com/questions/5028421/android-unzip-a-folder
        // And this: https://stackoverflow.com/questions/6039158/android-cancel-async-task
    }

    @Override
    protected void onProgressUpdate(Long... progress) {
        super.onProgressUpdate(progress);
        // update download progress in the progress bar here ...
    }

    @Override
    protected void onPostExecute(String name) {
        if (name != null) {
            super.onPostExecute(name);
            try {
                runJavascript("CallbackManager.cloud.downloadComplete(" + name + ")");
                File zip = new File(mainWebViewContext.getFilesDir(), name + ".bbx");
                String availableName = findAvailableName(getBirdblocksDir(), name, "");
                File to = new File(getBirdblocksDir(), availableName);
                new UnzipTask().execute(zip, to);
            } catch (SecurityException e) {
                Log.e(TAG, "Error while unzipping project: " + name);
            }
        }
    }

    @Override
    protected void onCancelled(String s) {
        super.onCancelled(s);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }
}
