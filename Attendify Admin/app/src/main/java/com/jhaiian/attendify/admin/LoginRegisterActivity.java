package com.jhaiian.attendify.admin;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.shashank.sony.fancytoastlib.FancyToast;

import java.util.HashMap;

public class LoginRegisterActivity extends AppCompatActivity {

    // Firebase instances
    private FirebaseDatabase _firebase = FirebaseDatabase.getInstance();
    private FirebaseAuth Auth;
    private DatabaseReference datadb = _firebase.getReference("data");

    // Layout binding
    private LoginRegisterBinding binding;

    // Dialog instance for alerts
    private AlertDialog.Builder Dialogss;

    // Network monitor instance
    private NetworkMonitor networkMonitor;

    // Firebase Auth listeners
    private OnCompleteListener<AuthResult> _Auth_create_user_listener;
    private OnCompleteListener<AuthResult> _Auth_sign_in_listener;
    private OnCompleteListener<Void> _Auth_reset_password_listener;
    private OnCompleteListener<Void> Auth_emailVerificationSentListener;

    // Cooldown timestamps for preventing spam actions
    private long lastVerificationRequestTime = 0;
    private long lastPasswordResetRequestTime = 0;

    // Cooldown duration in ms
    private static final long COOLDOWN_PERIOD = 60000;

    // Flags for cooldown state
    private boolean isVerificationCooldown = false;
    private boolean isResetCooldown = false;

    // Double-back exit tracking
    private long backPressedTime;

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        binding = LoginRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initialize(_savedInstanceState);
        initializeLogic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (networkMonitor != null) networkMonitor.startMonitoring();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkMonitor != null) networkMonitor.stopMonitoring();
    }

    @Override
    public void onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
            finishAffinity();
        } else {
            FancyToast.makeText(
                LoginRegisterActivity.this,
                "Press back again to exit",
                FancyToast.LENGTH_SHORT,
                FancyToast.INFO,
                false
            ).show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    private void initialize(Bundle _savedInstanceState) {
        Auth = FirebaseAuth.getInstance();
        Dialogss = new AlertDialog.Builder(this);

        // Initialize network monitor
        networkMonitor = new NetworkMonitor(this);

        // Setup UI and Firebase listeners
        setupListeners();
        initializeAuthListeners();
    }

    private void initializeLogic() {
        // Adjust layout when keyboard appears
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Default UI state: show registration form
        binding.HolderRegister.setVisibility(View.VISIBLE);
        binding.LoginHolder.setVisibility(View.GONE);
    }

    private void setupListeners() {

        // Register button
        binding.RegisterButton.setOnClickListener(v -> _Register());

        // Switch to login form
        binding.LoginSwitch.setOnClickListener(v -> {
            binding.HolderRegister.setVisibility(View.GONE);
            binding.LoginHolder.setVisibility(View.VISIBLE);
            binding.EmailLogin.setText(binding.RegisterEmail.getText().toString());
        });

        // Switch to register form
        binding.RegisterSwitch.setOnClickListener(v -> {
            binding.HolderRegister.setVisibility(View.VISIBLE);
            binding.LoginHolder.setVisibility(View.GONE);
        });

        // Login button
        binding.LoginButton.setOnClickListener(v -> _Login_Block());

        // Reset password button
        binding.ForgetPassword.setOnClickListener(v -> {
            String email_login = binding.EmailLogin.getText().toString().trim();

            if (TextUtils.isEmpty(email_login)) {
                binding.LayoutEmailLogin.setError("Please enter your email address.");
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email_login).matches()) {
                binding.LayoutEmailLogin.setError("Please enter a valid email address.");
            } else {
                _ResetPassword(email_login);
            }
        });

        setupTextWatchers();
    }

    private void setupTextWatchers() {
        // Removes error messages when the user begins typing
        TextWatcher clearErrorWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.LayoutWorkplaceSchool.setError(null);
                binding.LayoutEmailRegister.setError(null);
                binding.LayoutPasswordRegister.setError(null);
                binding.LayoutConfirmPassword.setError(null);
                binding.LayoutEmailLogin.setError(null);
                binding.LayoutPasswordLogin.setError(null);

                binding.LayoutWorkplaceSchool.setErrorEnabled(false);
                binding.LayoutEmailRegister.setErrorEnabled(false);
                binding.LayoutPasswordRegister.setErrorEnabled(false);
                binding.LayoutConfirmPassword.setErrorEnabled(false);
                binding.LayoutEmailLogin.setErrorEnabled(false);
                binding.LayoutPasswordLogin.setErrorEnabled(false);
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
        };

        // Attach watcher to all text fields
        binding.RegisterWorkplaceSchool.addTextChangedListener(clearErrorWatcher);
        binding.RegisterEmail.addTextChangedListener(clearErrorWatcher);
        binding.PasswordRegister.addTextChangedListener(clearErrorWatcher);
        binding.ConfirmPasswordRegister.addTextChangedListener(clearErrorWatcher);
        binding.EmailLogin.addTextChangedListener(clearErrorWatcher);
        binding.PasswordLogin.addTextChangedListener(clearErrorWatcher);
    }

    private void initializeAuthListeners() {

        // Registration listener
        _Auth_create_user_listener = task -> {
            if (task.isSuccessful()) {

                // Save only the workplace name in the database
                String workplace_school = binding.RegisterWorkplaceSchool.getText().toString().trim();
                HashMap<String, Object> mapver = new HashMap<>();
                mapver.put("Workplace Name", workplace_school);

                datadb.child(Auth.getCurrentUser().getUid()).updateChildren(mapver);

                _Sendverf();

            } else {
                String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown Error";

                if (isNetworkError(errorMsg)) {
                    networkMonitor.handleNetworkError(
                        getFirebaseErrorMessage(errorMsg),
                        this:: _Register
                    );
                } else {
                    showErrorDialog("Registration Failed", getFirebaseErrorMessage(errorMsg));
                }
            }
        };

        // Login listener
        _Auth_sign_in_listener = task -> {
            if (task.isSuccessful()) {

                // If email verified, proceed to main
                if (Auth.getCurrentUser().isEmailVerified()) {
                    startActivity(new Intent(LoginRegisterActivity.this, MainActivity.class));
                    finish();
                } else {
                    _Sendverf();
                }

            } else {
                String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown Error";

                if (isNetworkError(errorMsg)) {
                    networkMonitor.handleNetworkError(
                        getFirebaseErrorMessage(errorMsg),
                        this:: _Login_Block
                    );
                } else {
                    showErrorDialog("Login Failed", getFirebaseErrorMessage(errorMsg));
                }
            }
        };

        // Reset password listener
        _Auth_reset_password_listener = task -> {
            if (task.isSuccessful()) {
                showSuccessDialog("Password Reset", "Password reset email has been sent.");
            } else {
                networkMonitor.handleNetworkError(
                    getFirebaseErrorMessage(task.getException().getMessage()), null
                );
            }
        };

        // Email verification listener
        Auth_emailVerificationSentListener = task -> {
            if (task.isSuccessful()) {
                showSuccessDialog("Verification Email Sent",
                    "Please check your inbox or spam folder.");
            } else {
                networkMonitor.handleNetworkError(
                    getFirebaseErrorMessage(task.getException().getMessage()), null
                );
            }
        };
    }

    public void _Register() {
        String workplace_school = binding.RegisterWorkplaceSchool.getText().toString().trim();
        String mail_register = binding.RegisterEmail.getText().toString().trim();
        String password_register = binding.PasswordRegister.getText().toString().trim();
        String confirmpassword = binding.ConfirmPasswordRegister.getText().toString().trim();

        boolean register_valid = true;

        // Basic validation
        if (TextUtils.isEmpty(workplace_school)) {
            register_valid = false;
            binding.LayoutWorkplaceSchool.setError("This field is empty.");
        } else if (workplace_school.length() < 2) {
            register_valid = false;
            binding.LayoutWorkplaceSchool.setError("Name is too short.");
        }

        if (TextUtils.isEmpty(mail_register)) {
            register_valid = false;
            binding.LayoutEmailRegister.setError("This field is empty.");
        }
        if (TextUtils.isEmpty(password_register)) {
            register_valid = false;
            binding.LayoutPasswordRegister.setError("This field is empty.");
        }
        if (TextUtils.isEmpty(confirmpassword)) {
            register_valid = false;
            binding.LayoutConfirmPassword.setError("This field is empty.");
        }

        // Further validation logic
        if (register_valid) {

            if (!Patterns.EMAIL_ADDRESS.matcher(mail_register).matches()) {
                binding.LayoutEmailRegister.setError("Invalid email address.");

            } else if (!password_register.equals(confirmpassword)) {
                binding.LayoutPasswordRegister.setError("Passwords do not match.");
                binding.LayoutConfirmPassword.setError("Passwords do not match.");

            } else if (password_register.length() <= 6) {
                binding.LayoutPasswordRegister.setError("Password too short (min 6 chars).");
                binding.LayoutConfirmPassword.setError("Too short.");

            } else {
                if (Auth.getCurrentUser() != null &&
                        Auth.getCurrentUser().isEmailVerified()) {

                    FancyToast.makeText(
                        this,
                        "Account already created. Please login.",
                        FancyToast.LENGTH_LONG,
                        FancyToast.INFO,
                        false
                    ).show();

                } else {
                    Auth.createUserWithEmailAndPassword(mail_register, password_register)
                        .addOnCompleteListener(this, _Auth_create_user_listener);
                }
            }
        }
    }

    public void _Login_Block() {
        String email_login = binding.EmailLogin.getText().toString().trim();
        String password_login = binding.PasswordLogin.getText().toString().trim();

        boolean login_valid = true;

        // Empty field validation
        if (TextUtils.isEmpty(email_login)) {
            binding.LayoutEmailLogin.setError("This field is empty.");
            login_valid = false;
        }
        if (TextUtils.isEmpty(password_login)) {
            binding.LayoutPasswordLogin.setError("This field is empty.");
            login_valid = false;
        }

        if (login_valid) {
            Auth.signInWithEmailAndPassword(email_login, password_login)
                .addOnCompleteListener(this, _Auth_sign_in_listener);
        }
    }

    public void _Sendverf() {
        FirebaseUser user = Auth.getCurrentUser();

        if (user != null && !user.isEmailVerified()) {

            // Cooldown handling
            if (isVerificationCooldown) {
                long timeLeft = COOLDOWN_PERIOD - (System.currentTimeMillis() - lastVerificationRequestTime);
                if (timeLeft > 0) {
                    FancyToast.makeText(this, "Please wait " + (timeLeft / 1000) + "s before retrying.",
                        FancyToast.LENGTH_SHORT,
                        FancyToast.WARNING,
                        false
                    ).show();
                    return;
                }
            }

            lastVerificationRequestTime = System.currentTimeMillis();
            user.sendEmailVerification().addOnCompleteListener(Auth_emailVerificationSentListener);
        }
    }

    public void _ResetPassword(String email) {

        // Cooldown handling
        if (isResetCooldown) {
            long timeLeft = COOLDOWN_PERIOD - (System.currentTimeMillis() - lastPasswordResetRequestTime);
            if (timeLeft > 0) {
                FancyToast.makeText(this, "Please wait " + (timeLeft / 1000) + "s before retrying.",
                    FancyToast.LENGTH_SHORT,
                    FancyToast.WARNING,
                    false
                ).show();
                return;
            }
        }

        lastPasswordResetRequestTime = System.currentTimeMillis();
        Auth.sendPasswordResetEmail(email).addOnCompleteListener(_Auth_reset_password_listener);
    }

    private void showSuccessDialog(String title, String message) {
        Dialogss.setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", (d, w) -> {})
            .create().show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .show();
    }

    private String getFirebaseErrorMessage(String errorMessage) {
        if (errorMessage == null) return "An unknown error occurred";

        if (errorMessage.contains("badly formatted")) return "Invalid email format.";
        if (errorMessage.contains("no user record")) return "Account not found.";
        if (errorMessage.contains("password is invalid")) return "Incorrect password.";
        if (errorMessage.contains("network error")) return "Network connection failed.";
        if (errorMessage.contains("email address is already in use")) return "Email already registered.";
        if (errorMessage.contains("blocked all requests")) return "Too many attempts. Try again later.";
        if (errorMessage.contains("least 6 characters")) return "Password too short.";

        return errorMessage;
    }

    private boolean isNetworkError(String errorMessage) {
        if (errorMessage == null) return false;

        String msg = errorMessage.toLowerCase();

        return msg.contains("network") ||
               msg.contains("connection") ||
               msg.contains("host") ||
               msg.contains("offline") ||
               msg.contains("timeout");
    }
}