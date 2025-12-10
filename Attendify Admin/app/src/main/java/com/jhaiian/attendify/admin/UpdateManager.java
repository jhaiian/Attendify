package com.jhaiian.attendify.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.HashMap;

/**
 * Manages app update checking, update dialogs, APK downloading, and signature verification.
 */
public class UpdateManager {

    private Activity activity;
    private RequestNetwork requestNetwork;
    private int currentVersionCode;
    private SharedPreferences prefs;

    // Firebase Realtime Database REST URLs
    private static final String BASE_URL = "https://attendify-jhaiian-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String URL_SHA256 = BASE_URL + "/SHA256.json";
    private static final String URL_UPDATE = BASE_URL + "/Update.json";

    public interface OnUpdateCheckListener {
        void onProceed();
    }

    public UpdateManager(Activity activity) {
        this.activity = activity;
        this.requestNetwork = new RequestNetwork(activity);
        this.prefs = activity.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);

        // Load current version code
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            currentVersionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            currentVersionCode = 0;
        }
    }

    /**
     * Starts the update-check sequence:
     * 1. Verify signature SHA-256
     * 2. Fetch update metadata
     */
    public void checkForUpdate(final OnUpdateCheckListener listener) {
        requestNetwork.startRequestNetwork(RequestNetworkController.GET, URL_SHA256, "check_sha",
                new RequestNetwork.RequestListener() {
                    @Override
                    public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                        String authorizedHash = response.trim().replace("\"", "");

                        // Verify signature only when a hash exists in database
                        if (!authorizedHash.equals("null") && !authorizedHash.isEmpty()) {
                            if (!isSignatureValid(authorizedHash)) {
                                showTamperDialog();
                                return;
                            }
                        }

                        // Continue to update metadata
                        fetchUpdateData(listener);
                    }

                    @Override
                    public void onErrorResponse(String tag, String message) {
                        listener.onProceed();
                    }
                });
    }

    /**
     * Fetch update metadata (mandatory version, optional version, changelog URL, download URL).
     */
    private void fetchUpdateData(final OnUpdateCheckListener listener) {
        requestNetwork.startRequestNetwork(RequestNetworkController.GET, URL_UPDATE, "check_update",
                new RequestNetwork.RequestListener() {
                    @Override
                    public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {

                        if (response.equals("null") || response.isEmpty()) {
                            listener.onProceed();
                            return;
                        }

                        try {
                            HashMap<String, Object> data =
                                    new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());

                            int mandatoryVer = 0;
                            int optionalVer = 0;
                            String downloadLink = "";
                            String changelogLink = "";

                            // Convert JSON numerics (parsed as Double) to int
                            if (data.containsKey("Mandatory_Update")) {
                                mandatoryVer = (int) Double.parseDouble(String.valueOf(data.get("Mandatory_Update")));
                            }

                            if (data.containsKey("Optional_Update")) {
                                optionalVer = (int) Double.parseDouble(String.valueOf(data.get("Optional_Update")));
                            }

                            if (data.containsKey("Download_Link")) {
                                downloadLink = String.valueOf(data.get("Download_Link"));
                            }

                            if (data.containsKey("Changelog")) {
                                changelogLink = String.valueOf(data.get("Changelog"));
                            }

                            // Mandatory update required
                            if (currentVersionCode < mandatoryVer) {
                                showUpdateDialog(true, downloadLink, changelogLink, mandatoryVer, listener);
                                return;
                            }

                            // Optional update available
                            if (currentVersionCode < optionalVer) {
                                int ignoredVer = prefs.getInt("ignored_version", 0);
                                if (ignoredVer != optionalVer) {
                                    showUpdateDialog(false, downloadLink, changelogLink, optionalVer, listener);
                                    return;
                                }
                            }

                            listener.onProceed();

                        } catch (Exception e) {
                            listener.onProceed();
                        }
                    }

                    @Override
                    public void onErrorResponse(String tag, String message) {
                        listener.onProceed();
                    }
                });
    }

    /**
     * Validates the app signature hash against the authorized hash.
     */
    private boolean isSignatureValid(String authorizedHash) {
        String currentHash = getCurrentAppSignature();
        String cleanAuthorized = authorizedHash.replace(":", "").toUpperCase().trim();
        String cleanCurrent = currentHash.replace(":", "").toUpperCase().trim();
        return cleanCurrent.equals(cleanAuthorized);
    }

    /**
     * Retrieves current app signing certificate SHA-256.
     */
    private String getCurrentAppSignature() {
        try {
            PackageManager pm = activity.getPackageManager();
            String packageName = activity.getPackageName();
            PackageInfo packageInfo;

            if (Build.VERSION.SDK_INT >= 28) {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo.signingInfo.hasMultipleSigners()) {
                    return bytesToHex(packageInfo.signingInfo.getApkContentsSigners()[0].toByteArray());
                } else {
                    return bytesToHex(packageInfo.signingInfo.getSigningCertificateHistory()[0].toByteArray());
                }
            } else {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                return bytesToHex(packageInfo.signatures[0].toByteArray());
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Converts byte[] signature to SHA-256 hex string.
     */
    private String bytesToHex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X:", b));
            }

            if (sb.length() > 0) sb.setLength(sb.length() - 1);
            return sb.toString();

        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Shows a dialog when the signature does not match the authorized hash.
     */
    private void showTamperDialog() {
        if (activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Unofficial Version Detected");
        builder.setMessage("This version of the application has been modified or is not signed by the official developer.");
        builder.setCancelable(false);

        builder.setPositiveButton("Close App", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                activity.finishAffinity();
                System.exit(0);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(android.graphics.Color.RED);
    }

    /**
     * Displays update dialog for both mandatory and optional updates.
     */
    private void showUpdateDialog(
            final boolean isMandatory,
            final String downloadUrl,
            final String changelogUrl,
            final int newVersionCode,
            final OnUpdateCheckListener listener) {

        if (activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_update, null);

        builder.setView(view);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView txtTitle = view.findViewById(R.id.update_title);
        TextView txtMessage = view.findViewById(R.id.update_message);
        TextView btnChangelog = view.findViewById(R.id.btn_changelog);
        final CheckBox chkIgnore = view.findViewById(R.id.chk_ignore);

        View btnPositive = view.findViewById(R.id.btn_positive);
        TextView btnNegative = view.findViewById(R.id.btn_negative);

        // Mandatory update dialog setup
        if (isMandatory) {
            txtTitle.setText("Critical Update Required");
            txtMessage.setText("This version is no longer supported. You must update to continue.");
            chkIgnore.setVisibility(View.GONE);
            btnNegative.setText("Exit App");
        } else {
            txtTitle.setText("Update Available");
            txtMessage.setText("A new version of Attendify is available.");
            chkIgnore.setVisibility(View.VISIBLE);
            btnNegative.setText("Later");
        }

        // Open changelog URL
        btnChangelog.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(changelogUrl));
                activity.startActivity(browserIntent);
            } catch (Exception ignored) {}
        });

        // Install update
        btnPositive.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Please allow permission to install updates", Toast.LENGTH_LONG).show();
                    activity.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())
                    ));
                    return;
                }
            }
            new DownloadTask(activity).execute(downloadUrl);
        });

        // Handle dismiss or exit
        btnNegative.setOnClickListener(v -> {
            if (isMandatory) {
                activity.finishAffinity();
            } else {
                if (chkIgnore.isChecked()) {
                    prefs.edit().putInt("ignored_version", newVersionCode).apply();
                }
                dialog.dismiss();
                listener.onProceed();
            }
        });

        dialog.show();
    }

    /**
     * Downloads APK in background and displays a progress dialog.
     */
    private class DownloadTask extends AsyncTask<String, Integer, String> {
        private Context context;
        private ProgressDialog progressDialog;
        private File file;

        public DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setTitle("Downloading Update");
            progressDialog.setMessage("Please wait...");
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... sUrl) {
            try {
                URL url = new URL(sUrl[0]);
                URLConnection connection = url.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();

                file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "Attendify_Update.apk");

                if (file.exists()) file.delete();

                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(file);

                byte data[] = new byte[1024];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                return null;

            } catch (Exception e) {
                return e.toString();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            if (result != null) {
                Toast.makeText(context, "Download Error: " + result, Toast.LENGTH_LONG).show();
            } else {
                installApk(file);
            }
        }
    }

    /**
     * Installs the downloaded APK file.
     */
    private void installApk(File file) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(activity, "Error installing", Toast.LENGTH_SHORT).show();
        }
    }
}