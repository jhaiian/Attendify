package com.jhaiian.attendify.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Loads the splash screen, monitors network connectivity, checks for updates,
 * and routes the user to the correct screen based on authentication status.
 */
public class FlashActivity extends AppCompatActivity {

    // View binding for the splash layout
    private FlashBinding binding;

    // Firebase authentication instance
    private FirebaseAuth Auth;

    // Handles connectivity checks
    private NetworkMonitor networkMonitor;

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);

        // Inflate splash screen layout
        binding = FlashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        Auth = FirebaseAuth.getInstance();

        // Create network monitor instance
        networkMonitor = new NetworkMonitor(this);

        // Trigger update checking once internet is confirmed
        networkMonitor.setOnConnectionSuccess(new Runnable() {
            @Override
            public void run() {
                checkUpdates();
            }
        });

        // Begin monitoring internet status
        networkMonitor.startMonitoring();
    }

    /**
     * Initiates update verification using the UpdateManager.
     */
    private void checkUpdates() {
        UpdateManager updateManager = new UpdateManager(this);
        updateManager.checkForUpdate(new UpdateManager.OnUpdateCheckListener() {
            @Override
            public void onProceed() {
                checkAuthAndNavigate();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkMonitor != null) {
            networkMonitor.stopMonitoring();
        }
    }

    /**
     * Checks authentication state and routes the user
     * either to the main dashboard or login/register screen.
     */
    private void checkAuthAndNavigate() {
        FirebaseUser currentUser = Auth.getCurrentUser();
        Intent intent;

        if (currentUser != null && currentUser.isEmailVerified()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginRegisterActivity.class);
        }

        startActivity(intent);
        finish();
    }
}