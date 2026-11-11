package com.example.andonoperatorapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Displays a complete operator profile with all employee details,
 * department, designation, shift, skills, and plant name.
 * Back arrow and system back button navigate to HomeActivity.
 * Logout shows a premium confirmation dialog and navigates to LoginActivity.
 */
public class OperatorProfile extends AppCompatActivity {

    private static final String TAG = "OperatorProfile";

    private ImageView ivBack, ivProfilePhoto;
    private TextView tvFullName, tvUsertype, tvEmployeeId, tvEmail, tvPhone,
            tvDepartment, tvDesignation, tvShift, tvDateOfJoining,
            tvAccessId, tvPlantId, tvSkills;
    private MaterialButton btnLogout;

    private SessionManager sessionManager;
    private LoginActivity.Employee employee;
    private LoginActivity.LoginResponse fullResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator_profile);

        sessionManager = new SessionManager(this);

        fullResponse = sessionManager.getLoginResponse();
        if (fullResponse == null || fullResponse.employee == null) {
            Log.e(TAG, "No session data – redirecting to login");
            navigateToLogin();
            return;
        }
        employee = fullResponse.employee;

        bindViews();
        setupCircularProfilePhoto();
        populateProfile();
        populateAdditionalData();

        ivBack.setOnClickListener(v -> navigateToHome());
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        tvFullName = findViewById(R.id.tvFullName);
        tvUsertype = findViewById(R.id.tvUsertype);
        tvEmployeeId = findViewById(R.id.tvEmployeeId);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvDepartment = findViewById(R.id.tvDepartment);
        tvDesignation = findViewById(R.id.tvDesignation);
        tvShift = findViewById(R.id.tvShift);
        tvDateOfJoining = findViewById(R.id.tvDateOfJoining);
        tvAccessId = findViewById(R.id.tvAccessId);
        tvPlantId = findViewById(R.id.tvPlantId);
        tvSkills = findViewById(R.id.tvSkills);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupCircularProfilePhoto() {
        ivProfilePhoto.setClipToOutline(true);
        ivProfilePhoto.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int diameter = Math.min(view.getWidth(), view.getHeight());
                if (diameter == 0) {
                    diameter = (int) (100 * getResources().getDisplayMetrics().density);
                }
                int left = (view.getWidth() - diameter) / 2;
                int top = (view.getHeight() - diameter) / 2;
                outline.setOval(left, top, left + diameter, top + diameter);
            }
        });

        ShapeDrawable border = new ShapeDrawable(new OvalShape());
        border.getPaint().setColor(getResources().getColor(R.color.primary));
        border.getPaint().setStyle(Paint.Style.STROKE);
        border.getPaint().setStrokeWidth(6);
        border.getPaint().setAntiAlias(true);
        ivProfilePhoto.setForeground(border);
    }

    private void populateProfile() {
        String fullName = employee.getFirstname() + " " + employee.getLastname();
        tvFullName.setText(fullName.trim().isEmpty() ? "Operator" : fullName.trim());

        String userType = employee.getUsertype();
        if (userType != null && !userType.isEmpty()) {
            tvUsertype.setText(userType.substring(0, 1).toUpperCase() + userType.substring(1));
        } else {
            tvUsertype.setText("Operator");
        }

        tvEmployeeId.setText(employee.getEmployeeId() != null ? employee.getEmployeeId() : "N/A");
        tvEmail.setText(employee.getEmail() != null ? employee.getEmail() : "N/A");
        tvPhone.setText(employee.getPhone() != null ? employee.getPhone() : "N/A");
        tvDateOfJoining.setText(employee.getDateOfJoining() != null ? employee.getDateOfJoining() : "N/A");
        tvAccessId.setText(employee.getAccessId() != null ? employee.getAccessId() : "N/A");

        ivProfilePhoto.setImageResource(R.drawable.ic_user);
    }

    private void populateAdditionalData() {
        if (fullResponse.designation != null) {
            tvDesignation.setText(fullResponse.designation.designationName != null ?
                    fullResponse.designation.designationName : "N/A");
        } else {
            tvDesignation.setText("N/A");
        }

        if (fullResponse.department != null) {
            tvDepartment.setText(fullResponse.department.deptName != null ?
                    fullResponse.department.deptName : "N/A");
        } else {
            tvDepartment.setText("N/A");
        }

        if (fullResponse.shifts != null && !fullResponse.shifts.isEmpty()) {
            LoginActivity.Shift firstShift = fullResponse.shifts.get(0);
            String shiftInfo = firstShift.shiftName + " (" + firstShift.startTime + " - " + firstShift.endTime + ")";
            tvShift.setText(shiftInfo);
        } else {
            tvShift.setText("N/A");
        }

        if (fullResponse.skills != null && !fullResponse.skills.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fullResponse.skills.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(fullResponse.skills.get(i).skillName);
            }
            tvSkills.setText(sb.toString());
        } else {
            tvSkills.setText("N/A");
        }

        if (fullResponse.plant != null && fullResponse.plant.plantName != null) {
            tvPlantId.setText(fullResponse.plant.plantName);
        } else {
            tvPlantId.setText("N/A");
        }
    }

    /**
     * Premium custom logout dialog with icon, title, and styled buttons.
     */
    private void showLogoutConfirmationDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_logout, null);

        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmLogout);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            performLogout();         // clears session & goes to Login
        });

        dialog.show();
    }

    private void performLogout() {
        try {
            sessionManager.clearSession();
            Log.d(TAG, "Session cleared successfully");
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing session: " + e.getMessage(), e);
            Toast.makeText(this, "Error during logout", Toast.LENGTH_SHORT).show();
        }
        navigateToLogin();          // always go to Login, not Home
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Do NOT call super – just go to Home
        navigateToHome();
    }
}