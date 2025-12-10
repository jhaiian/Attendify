package com.jhaiian.attendify.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import java.util.HashMap;

public class NetworkMonitor {

    // Reference to calling Activity; required for UI operations
    private Activity activity;

    // Main system service for monitoring connectivity
    private ConnectivityManager connectivityManager;

    // Callback listener for network changes
    private ConnectivityManager.NetworkCallback networkCallback;

    // Persistent dialog shown during errors or checks
    private AlertDialog dialog;

    // Prevent re-registering callbacks
    private boolean isMonitoring = false;

    // Utility class for HTTP requests
    private RequestNetwork requestNetwork;

    // Action executed when user presses Retry
    private Runnable onRetryAction;

    // Action executed when connection succeeds (FlashActivity listener)
    private Runnable onConnectionSuccess;

    // Firebase URL for app status
    private static final String FIREBASE_URL = "https://attendify-jhaiian-default-rtdb.asia-southeast1.firebasedatabase.app/Status.json";

    public NetworkMonitor(Activity activity) {
        this.activity = activity;
        this.connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.requestNetwork = new RequestNetwork(activity);
        createDialog();
    }

    // Setter for external success callback
    public void setOnConnectionSuccess(Runnable action) {
        this.onConnectionSuccess = action;
    }

    /**
     * Creates a persistent AlertDialog used for all connection messaging.
     * This avoids recreating multiple dialogs and reduces memory leaks.
     */
    private void createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Connection Error")
               .setMessage("Checking connection...")
               .setCancelable(false)
               .setPositiveButton("Retry", null)
               .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialogInterface, int i) {
                       activity.finishAffinity(); // Graceful app exit
                   }
               });

        dialog = builder.create();

        // Override back button inside dialog
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    activity.finishAffinity(); // Treat as exit
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Begins monitoring system connectivity and server status.
     * Registers a network callback and immediately checks server reachability.
     */
    public void startMonitoring() {
        if (isMonitoring) return;

        // First verify basic network connection
        if (!isSystemConnected()) {
            showDialog("No Internet Connection", "Please check your internet connection settings.", null);
        } else {
            verifyServerConnection(); // Then verify server availability
        }

        // Build network request
        NetworkRequest.Builder builder = new NetworkRequest.Builder();

        // Handle connectivity changes
        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                // Connectivity restored → verify server status again
                activity.runOnUiThread(() -> verifyServerConnection());
            }

            @Override
            public void onLost(Network network) {
                // Network lost → show offline dialog
                activity.runOnUiThread(() ->
                        showDialog("No Internet Connection", "Please check your internet connection.", null)
                );
            }
        };

        try {
            connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            isMonitoring = true;
        } catch (Exception e) {
            // Avoid crashing if callback registration fails
        }
    }

    /**
     * Stops monitoring connectivity to prevent leaks.
     */
    public void stopMonitoring() {
        if (!isMonitoring || connectivityManager == null || networkCallback == null) return;

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            // Silent fail — callback may already be unregistered
        }
        isMonitoring = false;
    }

    /**
     * Step 2 — Server verification.
     * Contacts Firebase "Status" node to determine if the app should be accessible.
     */
    private void verifyServerConnection() {

        // If dialog is visible, update it with progress info
        if (dialog.isShowing()) {
            updateDialogText("Connecting...", "Verifying server status...");
        }

        requestNetwork.startRequestNetwork(
                RequestNetworkController.GET,
                FIREBASE_URL,
                "check_connection",
                new RequestNetwork.RequestListener() {

                    @Override
                    public void onResponse(String tag, String response, HashMap<String, Object> headers) {

                        // Firebase wraps strings in quotes → clean them
                        String status = response.trim().replace("\"", "");

                        // App disabled by admin
                        if ("false".equalsIgnoreCase(status)) {
                            showDialog("Access Denied", "This application is currently disabled by the administrator.", null);
                            return;
                        }

                        // Maintenance mode
                        if ("maintenance".equalsIgnoreCase(status)) {
                            showDialog("Server Maintenance", "We are currently performing scheduled maintenance. Please try again later.", null);
                            return;
                        }

                        // Otherwise → everything is good
                        dismissDialog();

                        // Run pending retry action
                        if (onRetryAction != null) {
                            onRetryAction.run();
                            onRetryAction = null;
                        }

                        // Notify success listener (e.g., FlashActivity)
                        if (onConnectionSuccess != null) {
                            onConnectionSuccess.run();
                            onConnectionSuccess = null;
                        }
                    }

                    @Override
                    public void onErrorResponse(String tag, String message) {
                        String friendlyError = getFriendlyErrorMessage(message);
                        showDialog("Connection Failed", friendlyError, onRetryAction);
                    }
                }
        );
    }

    /**
     * Shows user-friendly connection error messages.
     */
    public void handleNetworkError(String rawErrorMessage, Runnable retryAction) {
        String friendlyMessage = getFriendlyErrorMessage(rawErrorMessage);
        showDialog("Connection Error", friendlyMessage, retryAction);
    }

    /**
     * Shows the main dialog with Retry logic.
     */
    private void showDialog(String title, String message, final Runnable retryAction) {
        if (!activity.isFinishing()) {

            this.onRetryAction = retryAction;

            dialog.setTitle(title);
            dialog.setMessage(message);

            // Display dialog only if not already visible
            if (!dialog.isShowing()) {
                dialog.show();

                // Wire Retry button manually (overrides default behavior)
                final Button retryBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                retryBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        retryBtn.setText("Checking...");
                        retryBtn.setEnabled(false);

                        // Re-check server connection
                        verifyServerConnection();

                        // UI recovery after short delay
                        new Handler().postDelayed(() -> {
                            if (dialog.isShowing()) {
                                retryBtn.setEnabled(true);
                                retryBtn.setText("Retry");
                            }
                        }, 2000);
                    }
                });

            } else {
                // If already showing → refresh button state
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");
            }
        }
    }

    /**
     * Update an already-open dialog without closing it.
     */
    private void updateDialogText(String title, String message) {
        if (dialog != null && dialog.isShowing()) {
            dialog.setTitle(title);
            dialog.setMessage(message);
        }
    }

    /**
     * Safely dismisses dialog.
     */
    private void dismissDialog() {
        if (dialog != null && dialog.isShowing() && !activity.isFinishing()) {
            dialog.dismiss();
        }
    }

    /**
     * Checks system-level internet availability.
     */
    public boolean isSystemConnected() {
        if (connectivityManager != null) {
            android.net.NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    /**
     * Converts raw network exceptions into user-friendly messages.
     */
    public static String getFriendlyErrorMessage(String originalMessage) {
        if (originalMessage == null) return "Unable to connect. Check internet.";

        if (originalMessage.contains("firebasedatabase.app") || originalMessage.contains("http")) {
            return "Cannot reach the server. Please check your internet.";
        }
        if (originalMessage.contains("timeout") || originalMessage.contains("Timeout")) {
            return "Connection timed out. Internet might be too slow.";
        }
        if (originalMessage.contains("UnknownHostException") || originalMessage.contains("Unable to resolve host")) {
            return "No Internet Connection. Please check your WiFi/Data.";
        }
        return "Connection error. Please try again.";
    }
  }
