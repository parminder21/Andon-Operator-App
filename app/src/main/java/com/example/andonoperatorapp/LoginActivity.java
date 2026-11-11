package com.example.andonoperatorapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.annotations.SerializedName;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Operator Login screen.
 * Validates credentials, calls the authentication API,
 * stores the full login response, and navigates to ConfigActivity on success.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String STATIC_TOKEN = "b3BlcmF0b3JBcHBTdGF0aWNUb2tlbjEyMw==";

    private TextInputEditText etEmployeeId;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar loginProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmployeeId = findViewById(R.id.etEmployeeId);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        loginProgress = findViewById(R.id.loginProgress);

        btnLogin.setOnClickListener(v -> performValidationAndLogin());
    }

    private void performValidationAndLogin() {
        String empId = etEmployeeId.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        etEmployeeId.setError(null);
        etPassword.setError(null);

        if (empId.isEmpty()) {
            etEmployeeId.setError("Employee ID is required");
            etEmployeeId.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        Log.d(TAG, "Attempting login for Employee ID: " + empId);
        performLoginApiCall(empId, password);
    }

    private void performLoginApiCall(String empId, String password) {
        setLoading(true);

        try {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://smartandon.robatosystems.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            ApiService api = retrofit.create(ApiService.class);
            LoginRequest request = new LoginRequest(STATIC_TOKEN, empId, password);

            api.login(request).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    setLoading(false);

                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse body = response.body();
                        if ("success".equals(body.status)) {
                            Log.d(TAG, "Login successful. Employee: " +
                                    (body.employee != null ? body.employee.employeeId : "null"));

                            // Save full response
                            SessionManager sessionManager = new SessionManager(LoginActivity.this);
                            sessionManager.saveLoginResponse(body);

                            // Navigate to next screen
                            navigateToNextScreen();
                        } else {
                            showError(body.msg != null ? body.msg : "Login failed");
                        }
                    } else {
                        String errorMsg = "Server error or invalid credentials";
                        try {
                            if (response.errorBody() != null) {
                                errorMsg += ": " + response.errorBody().string();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                        showError(errorMsg);
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    setLoading(false);
                    String errorMsg;
                    if (t instanceof SocketTimeoutException) {
                        errorMsg = "Connection timed out. Please check your network.";
                    } else if (t instanceof ConnectException) {
                        errorMsg = "Could not connect to server. Please try again later.";
                    } else {
                        errorMsg = "Network error: " + t.getMessage();
                    }
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });
        } catch (Exception e) {
            setLoading(false);
            Log.e(TAG, "Unexpected error during login setup: " + e.getMessage(), e);
            showError("An unexpected error occurred. Please try again.");
        }
    }

    private void setLoading(boolean isLoading) {
        btnLogin.setEnabled(!isLoading);
        loginProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setText(isLoading ? "" : "LOGIN");
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Determines whether to go to ConfigActivity (first time) or HomeActivity (if machine already selected).
     */
    private void navigateToNextScreen() {
        SessionManager sessionManager = new SessionManager(this);
        Intent intent;

        // If a machine was previously selected and saved, go directly to Home.
        if (sessionManager.getSelectedMachine() != null) {
            Log.d(TAG, "Machine already selected – going to HomeActivity");
            intent = new Intent(LoginActivity.this, HomeActivity.class);
        } else {
            Log.d(TAG, "No machine selected – going to ConfigActivity");
            intent = new Intent(LoginActivity.this, ConfigActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------- Retrofit API interface ----------

    interface ApiService {
        @POST("api/operator-app/login")   // Corrected endpoint – saved for future
        Call<LoginResponse> login(@Body LoginRequest request);
    }

    // ---------- Request / Response DTOs ----------

    static class LoginRequest {
        @SerializedName("tk")
        final String tk;
        @SerializedName("employee_id")
        final String employeeId;
        @SerializedName("pswd")
        final String pswd;

        LoginRequest(String tk, String employeeId, String pswd) {
            this.tk = tk;
            this.employeeId = employeeId;
            this.pswd = pswd;
        }
    }

    static class LoginResponse {
        @SerializedName("status")
        String status;
        @SerializedName("msg")
        String msg;
        @SerializedName("token")
        String token;
        @SerializedName("employee")
        Employee employee;
        @SerializedName("company")
        Company company;
        @SerializedName("designation")
        Designation designation;
        @SerializedName("department")
        Department department;
        @SerializedName("plant")
        Plant plant;
        @SerializedName("skills")
        List<Skill> skills;
        @SerializedName("shifts")
        List<Shift> shifts;
    }

    // ---------- Domain models (inner static classes) ----------

    public static class Employee {
        @SerializedName("id") String id;
        @SerializedName("employee_id") String employeeId;
        @SerializedName("firstname") String firstname;
        @SerializedName("lastname") String lastname;
        @SerializedName("gender") String gender;
        @SerializedName("usertype") String usertype;
        @SerializedName("phone") String phone;
        @SerializedName("email") String email;
        @SerializedName("username") String username;
        @SerializedName("experience") String experience;
        @SerializedName("date_of_joining") String dateOfJoining;
        @SerializedName("access_id") String accessId;
        @SerializedName("plant_id") String plantId;
        @SerializedName("created_at") String createdAt;

        public String getEmployeeId() { return employeeId; }
        public String getFirstname() { return firstname; }
        public String getLastname() { return lastname; }
        public String getFullName() { return firstname + " " + lastname; }
        public String getEmail() { return email; }
        public String getUsertype() { return usertype; }
        public String getDateOfJoining() { return dateOfJoining; }
        public String getPhone() { return phone; }
        public String getAccessId() { return accessId; }
        public int getPlantId() { return Integer.parseInt(plantId); }

        /** Safe getter that never throws – returns the raw plant_id string, or "0" if null/empty. */
        public String getPlantIdString() {
            return (plantId != null && !plantId.isEmpty()) ? plantId : "0";
        }
    }

    public static class Company {
        @SerializedName("company_id") int companyId;
        @SerializedName("industryid") String industryId;
        @SerializedName("company_name") String companyName;
        @SerializedName("email") String email;
        @SerializedName("phone") String phone;
        @SerializedName("address") String address;
        @SerializedName("contact_person") String contactPerson;
        @SerializedName("contact_person_number") String contactPersonNumber;
        @SerializedName("is_verified") String isVerified;
        @SerializedName("contact_person_designation") String contactPersonDesignation;
        @SerializedName("status") String status;
        @SerializedName("timestamp") String timestamp;
    }

    public static class Designation {
        @SerializedName("designation_id") int designationId;
        @SerializedName("company_id") int companyId;
        @SerializedName("departmentid") String departmentId;
        @SerializedName("designation_name") String designationName;
        @SerializedName("description") String description;
        @SerializedName("status") String status;
        @SerializedName("timestamp") String timestamp;
    }

    public static class Department {
        @SerializedName("id") int id;
        @SerializedName("deptname") String deptName;
        @SerializedName("deptdescription") String deptDescription;
        @SerializedName("status") String status;
        @SerializedName("company") String company;
        @SerializedName("created_at") String createdAt;
    }

    public static class Plant {
        @SerializedName("id") int id;
        @SerializedName("plant_name") String plantName;
        @SerializedName("plant_code") String plantCode;
        @SerializedName("plant_description") String plantDescription;
        @SerializedName("plant_address") String plantAddress;
        @SerializedName("plant_city") String plantCity;
        @SerializedName("plant_state") String plantState;
        @SerializedName("plant_country") String plantCountry;
        @SerializedName("plant_pincode") String plantPincode;
        @SerializedName("plant_contact_person") String plantContactPerson;
        @SerializedName("plant_contact_number") String plantContactNumber;
        @SerializedName("plant_email") String plantEmail;
        @SerializedName("total_floors") int totalFloors;
        @SerializedName("total_assembly_lines") int totalAssemblyLines;
        @SerializedName("total_machines") int totalMachines;
        @SerializedName("status") String status;
        @SerializedName("company_id") int companyId;
        @SerializedName("created_by") int createdBy;
        @SerializedName("created_at") String createdAt;
        @SerializedName("updated_at") String updatedAt;
    }

    public static class Skill {
        @SerializedName("id") int id;
        @SerializedName("user_id") int userId;
        @SerializedName("skill_id") int skillId;
        @SerializedName("skill_category_id") int skillCategoryId;
        @SerializedName("company_id") int companyId;
        @SerializedName("plant_id") int plantId;
        @SerializedName("created_at") String createdAt;
        @SerializedName("skill_name") String skillName;
        @SerializedName("description") String description;
        @SerializedName("skill_status") String skillStatus;
    }

    public static class Shift {
        @SerializedName("user_shift_id") int userShiftId;
        @SerializedName("user_id") int userId;
        @SerializedName("shift_id") int shiftId;
        @SerializedName("company_id") int companyId;
        @SerializedName("plant_id") int plantId;
        @SerializedName("created_at") String createdAt;
        @SerializedName("shiftname") String shiftName;
        @SerializedName("starttime") String startTime;
        @SerializedName("endtime") String endTime;
        @SerializedName("description") String description;
        @SerializedName("status") String status;
    }
}