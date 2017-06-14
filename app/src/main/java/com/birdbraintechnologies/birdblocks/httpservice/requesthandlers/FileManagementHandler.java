package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static android.media.CamcorderProfile.get;
import static java.security.AccessController.getContext;

/**
 * Request handler for managing files on the device.
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class FileManagementHandler implements RequestHandler {
    private static final String TAG = FileManagementHandler.class.getName();
    private static final String BIRDBLOCKS_SAVE_DIR = "Saved";
    private static final String FILE_NOT_FOUND_RESPONSE = "File Not Found";
    public static File SecretFileDirectory;

    private HttpService service;

    public FileManagementHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "save":
                if (m.get("options") == null) {
                    responseBody = saveFile(m.get("filename").get(0), session, null);
                } else if (m.get("options").get(0).equals("new") || m.get("options").get(0).equals("soft")) {
                    responseBody = saveFile(m.get("filename").get(0), session, m.get("options").get(0));
                } else {
                    // bad request
                }
                break;
            case "load":
                responseBody = loadFile(m.get("filename").get(0));
                break;
            case "rename":
                if (m.get("options") == null) {
                    renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), null);
                } else if (m.get("options").get(0).equals("soft")) {
                    renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), m.get("options").get(0));
                } else {
                    // bad request
                }
                break;
            case "delete":
                deleteFile(m.get("filename").get(0));
                break;
            case "files":
                responseBody = listFiles();
                break;
            case "export":
                exportFile(m.get("filename").get(0), session);
                break;
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Saves a file to the device (Only supports POST requests)
     *
     * @param filename Name of the file to save
     * @param session  HttpRequest to get the POST body of
     * @param option
     * @return
     */
    private String saveFile(String filename, NanoHTTPD.IHTTPSession session, String option) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "Save must be done via POST request");
            return null;
        }
        Map<String, String> postFiles = new HashMap<>();

        if (option == null) {
            // do nothing extra in this case
            // forcibly attempt to write
            // overwrite file with same name, if it exists
        } else if (option.equals("new")) {
            // try to save
            // automatically find an available name
            filename = findAvailableName(getBirdblocksDir(), filename);
        } else if (option.equals("soft")) {
            // try to save
            // respond with 409 if name unavailable
            if (!isNameAvailable(getBirdblocksDir(), filename))
                // Raise 409
                return "409";
        }

        File newFile = new File(getBirdblocksDir(), filename);
        try {
            // Parse POST body to get parameters
            session.parseBody(postFiles);
            // Write POST["data"] to file
            FileWriter writer = new FileWriter(newFile);
            writer.write(postFiles.get("postData"));
            writer.close();
        } catch (IOException e) {
            newFile.delete();
            Log.e(TAG, e.toString());
            return null;
        } catch (NanoHTTPD.ResponseException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return filename;
    }

    /**
     * Loads a file from the device
     *
     * @param filename Name of the file to load
     * @return String contents of the file
     */
    private String loadFile(String filename) {
        File file = new File(getBirdblocksDir(), filename);
        if (!file.exists()) {
            return FILE_NOT_FOUND_RESPONSE;
        }
        StringBuilder response = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line + "\n");
            }
        } catch (FileNotFoundException e) {
            return FILE_NOT_FOUND_RESPONSE;
        } catch (IOException e) {
            Log.d(TAG, "Error reading saved file: " + e.toString());
        }
        return response.toString().trim();
    }

    /**
     * Renames a saved file on the device
     *
     * @param oldFilename Old file name
     * @param newFilename New name of file
     * @param option
     * @return CAREFUL -> RETURNS ERROR CODE FOR NOW
     * // TODO: Implement Properly
     */
    private String renameFile(String oldFilename, String newFilename, String option) {
        File file = new File(getBirdblocksDir(), oldFilename);
        if (!file.exists() || !isNameSanitized(newFilename)) {
            // 409
            return "409";
        }
        if (option == null) {
            // force rename if newFilename is valid
            // overwrite file if it exists
            // 409 if newFilename is corrupt
        } else if (option.equals("soft")) {
            // throw error if new name file already exists
            // if new name corrupt, throw error
            // else rename
            if (!isNameAvailable(getBirdblocksDir(), newFilename))
                return "409";
        }
        try {
            file.renameTo(new File(getBirdblocksDir(), newFilename));
            return null;
        } catch (Exception e) {
            Log.e("Rename", "");
            // 503
            return "503";
        }
    }

    /**
     * Deletes a saved file on the device
     *
     * @param filename Name of file to delete
     */
    private void deleteFile(String filename) {
        File file = new File(getBirdblocksDir(), filename);
        if (!file.exists()) {
            return;
        }
        file.delete();
    }

    /**
     * Lists the files on the device
     *
     * @return List of files on the device separated by \n
     */
    private String listFiles() {
        File[] files = getBirdblocksDir().listFiles();
        String response = "";
        if (files == null) {
            return response;
        }
        for (int i = 0; i < files.length; i++) {
            response += files[i].getName() + "\n";
        }
        return response;
    }

    /**
     * Starts a share command for a saved file on the device
     *
     * @param filename Name of the file to share
     * @param session  HttpRequest containing the most up to date contents of the file
     */
    private String exportFile(String filename, NanoHTTPD.IHTTPSession session) {
        /* SAVING HERE NO LONGER REQUIRED */
        // Save the updated contents (in case they were updated)
        // saveFile(filename, session);
        try {
            // Create share intent on the main activity
            File file = new File(getBirdblocksDir(), filename);
            if (file.exists()) {
                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
                showDialog.putExtra("file_uri", Uri.fromFile(file));
                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
            }
            return filename;
        } catch (Exception e) {
            Log.e("Export", "");
            return null;
        }
    }

    /**
     *
     * @param name
     * @return
     */
    public static boolean isNameSanitized(String name) {
        return true;
    }

    /**
     *
     * @param name
     * @return
     */
    public static String sanitizeName (String name) {
        return name;
    }

    /**
     * @param dir
     * @param name
     * @return
     */
    public static boolean isNameAvailable(File dir, String name) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().equals(name)) return false;
        }
        return true;
    }

    /**
     * @param dir
     * @param name
     * @return
     */
    public static String findAvailableName(File dir, String name) {
        if (isNameAvailable(dir, name)) return name;
        // else
        try {
            File[] files = dir.listFiles();
            for (int i = 1; i <= files.length; i++) {
                String newName = name + "(" + i + ")";
                if (isNameAvailable(dir, newName)) return newName;
            }
        } catch (SecurityException e) {
            Log.e("FindName", e.getMessage());
        }
        return null;
    }

    /**
     * Gets the BirdBlocks save directory
     *
     * @return File object for the save directory
     */
    public static File getBirdblocksDir() {
        //File file = new File(Environment.getExternalStoragePublicDirectory(
        //        Environment.DIRECTORY_DOCUMENTS), BIRDBLOCKS_SAVE_DIR);

        File file = new File(SecretFileDirectory, BIRDBLOCKS_SAVE_DIR);
        if (!file.exists()) {
            try {
                file.mkdirs();
            } catch (SecurityException e) {
                Log.e("Save Directory", "" + e);
            }
        }
        Log.d(TAG, "Created BirdBlocks save directory: " + file.getPath());
        return file;
    }

}
