package com.example.andonoperatorapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Home dashboard screen.
 * Shows selected machine details at the top, and a list of jobs fetched from the server.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final String BASE_URL = "https://smartandon.robatosystems.com/";
    private static final String STATIC_TOKEN = "b3BlcmF0b3JBcHBTdGF0aWNUb2tlbjEyMw==";
    private static final long AUTO_REFRESH_INTERVAL = 30_000;

    // UI
    private TextView tvOperatorName;
    private ImageView ivChooseMachine, ivRefresh, ivProfile, ivLogout;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvJobs;

    // Machine detail card views
    private View machineDetailCard;
    private TextView tvDetailMachineName, tvDetailStatus, tvDetailHealth,
            tvDetailFloor, tvDetailUid;
    private View statusIndicator;

    // Data
    private SessionManager sessionManager;
    private LoginActivity.Employee employee;
    private LoginActivity.LoginResponse loginResponse;
    private String plantId;
    private String operatorId;
    private String shiftId;
    private String companyId;                              // NEW
    private Machine selectedMachine = null;
    private List<JobItem> jobList = new ArrayList<>();

    // Adapters
    private JobAdapter jobAdapter;

    // Retrofit
    private ApiService apiService;

    // Auto-refresh
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;

    private boolean doubleBackToExit = false;
    private Handler backHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);

        // Check session
        loginResponse = sessionManager.getLoginResponse();
        if (loginResponse == null || loginResponse.employee == null) {
            navigateToLogin();
            return;
        }
        employee = loginResponse.employee;
        plantId = employee.getPlantIdString();
        operatorId = employee.id;
        shiftId = getFirstShiftId(loginResponse.shifts);

        // Extract companyId from login response
        companyId = (loginResponse.company != null)
                ? String.valueOf(loginResponse.company.companyId)
                : "45";

        Log.d(TAG, "Session loaded: operatorId=" + operatorId + ", shiftId=" + shiftId + ", plantId=" + plantId);

        // Bind top bar views
        tvOperatorName = findViewById(R.id.tvOperatorName);
        ivChooseMachine = findViewById(R.id.ivChooseMachine);
        ivRefresh = findViewById(R.id.ivRefresh);
        ivProfile = findViewById(R.id.ivProfile);
        ivLogout = findViewById(R.id.ivLogout);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvJobs = findViewById(R.id.rvJobs);

        // Bind machine detail card views
        machineDetailCard = findViewById(R.id.machineDetailCard);
        tvDetailMachineName = machineDetailCard.findViewById(R.id.tvDetailMachineName);
        tvDetailStatus = machineDetailCard.findViewById(R.id.tvDetailStatus);
        tvDetailHealth = machineDetailCard.findViewById(R.id.tvDetailHealth);
        tvDetailFloor = machineDetailCard.findViewById(R.id.tvDetailFloor);
        tvDetailUid = machineDetailCard.findViewById(R.id.tvDetailUid);
        statusIndicator = machineDetailCard.findViewById(R.id.statusIndicator);

        // Welcome message
        tvOperatorName.setText("Welcome, " + employee.getFirstname());

        // Jobs RecyclerView with listener
        jobAdapter = new JobAdapter(jobList, new JobAdapter.OnJobClickListener() {
            @Override
            public void onJobClick(JobItem job) {
                Intent intent = new Intent(HomeActivity.this, JobManageActivity.class);
                intent.putExtra("job_id", job.jobId);
                intent.putExtra("part_number", job.partNumber);
                intent.putExtra("target", job.target);
                intent.putExtra("current", job.current);
                intent.putExtra("job_status", job.jobStatus);
                intent.putExtra("machine_name", job.machineName);
                intent.putExtra("floor_name", job.floorName);
                intent.putExtra("shift", job.shift);
                intent.putExtra("avg_cycle_time", job.avgCycleTime);
                intent.putExtra("priority", job.priority);
                intent.putExtra("total_runtime", formatRuntimeMinutes(job.totalRuntime));
                intent.putExtra("total_planned_downtime", formatDowntimeMinutes(job.totalPlannedDowntime));
                intent.putExtra("total_unplanned_downtime", formatDowntimeMinutes(job.totalUnplannedDowntime));
                intent.putExtra("defective", job.defective != null ? String.valueOf(job.defective) : "0");
                intent.putExtra("actual_start_time", job.actualStartTime);
                intent.putExtra("actual_end_time", job.actualEndTime);
                intent.putExtra("machine_id", job.machineId);
                startActivity(intent);
            }

            @Override
            public void onStartJobClick(JobItem job) {
                showConfirmDialog(job, "start",
                        "You are about to start Job " + job.jobId + " on " + job.machineName +
                                ".\nOnce started, production will begin. Are you sure you want to proceed?");
            }

            @Override
            public void onStopJobClick(JobItem job) {
                showConfirmDialog(job, "stop",
                        "You are about to stop Job " + job.jobId + ".\nProduction will be paused. Are you sure?");
            }
        });

        rvJobs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvJobs.setAdapter(jobAdapter);

        // Swipe refresh
        swipeRefresh.setOnRefreshListener(() -> refreshData());

        // Icon listeners
        ivChooseMachine.setOnClickListener(v -> openConfigActivity());
        ivRefresh.setOnClickListener(v -> {
            swipeRefresh.setRefreshing(true);
            refreshData();
        });
        ivProfile.setOnClickListener(v -> navigateToProfile());
        ivLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        // Initialize Retrofit
        apiService = buildApiService();

        // Auto-refresh runnable
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchJobs(false);   // silent refresh
                refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        };

        // Load saved machine and fetch jobs
        Machine savedMachine = sessionManager.getSelectedMachine();
        if (savedMachine != null) {
            updateMachineDetailCard(savedMachine);
            fetchJobs(true);   // initial load
        } else {
            machineDetailCard.setVisibility(View.GONE);
            Log.d(TAG, "No machine saved – machine detail card hidden.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh jobs immediately when returning from JobManageActivity
        fetchJobs(true);
        // Restart auto-refresh
        refreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private String getFirstShiftId(List<LoginActivity.Shift> shifts) {
        if (shifts != null && !shifts.isEmpty()) {
            String id = String.valueOf(shifts.get(0).shiftId);
            Log.d(TAG, "First shift ID: " + id);
            return id;
        }
        Log.w(TAG, "No shifts found, using '0' as shiftId");
        return "0";
    }

    // ---------- Networking ----------

    private ApiService buildApiService() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(ApiService.class);
    }

    private void fetchJobs(boolean showLoading) {
        if (showLoading) {
            swipeRefresh.setRefreshing(true);
        }

        JobRequest request = new JobRequest(STATIC_TOKEN, operatorId, shiftId, plantId);
        apiService.getJobs(request).enqueue(new Callback<JobResponse>() {
            @Override
            public void onResponse(Call<JobResponse> call, Response<JobResponse> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    JobResponse body = response.body();
                    if ("success".equals(body.status)) {
                        jobList.clear();
                        if (body.jobs != null) {
                            jobList.addAll(body.jobs);
                        }
                        jobAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Jobs refreshed: " + jobList.size() + " job(s)");
                    } else {
                        Log.e(TAG, "Job API error: " + body.msg);
                        if (showLoading) showError(body.msg != null ? body.msg : "Failed to load jobs");
                    }
                } else {
                    String error = "Failed to load jobs";
                    try {
                        if (response.errorBody() != null) error += ": " + response.errorBody().string();
                    } catch (IOException e) { /* ignore */ }
                    Log.e(TAG, error);
                    if (showLoading) showError(error);
                }
            }

            @Override
            public void onFailure(Call<JobResponse> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Log.e(TAG, "Job API call failed: " + t.getMessage(), t);
                if (showLoading) showError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Shows a premium confirmation dialog for start/stop actions.
     */
    private void showConfirmDialog(JobItem job, String action, String message) {
        View dialogView = LayoutInflater.from(HomeActivity.this)
                .inflate(R.layout.dialog_confirm_start_job, null);

        // Update title
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setText(action.equals("start") ? "Confirm Job Start" : "Confirm Job Stop");
        }

        // Update message
        TextView tvMsg = dialogView.findViewById(R.id.tvDialogMessage);
        tvMsg.setText(message);

        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmStart);

        // Update confirm button
        if (action.equals("start")) {
            btnConfirm.setText("Yes, Start Job");
            btnConfirm.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.planned_button)));
        } else {
            btnConfirm.setText("Yes, Stop Job");
            btnConfirm.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.stop_button)));
        }

        AlertDialog dialog = new AlertDialog.Builder(HomeActivity.this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            performJobAction(job, action);
        });

        dialog.show();
    }

    /**
     * Calls the job-action API with the given action ("start" or "stop"),
     * updates the local job item, and optionally navigates to JobManageActivity.
     */
    private void performJobAction(JobItem job, String action) {
        // Build request – only "stop" needs jobendtime
        String jobEndTime = null;
        if (action.equals("stop")) {
            jobEndTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date());
        }

        JobActionRequest request = new JobActionRequest(
                STATIC_TOKEN, job.jobId, action, jobEndTime, companyId);

        apiService.jobAction(request).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    String msg = action.equals("start") ? "Job started successfully" : "Job stopped successfully";
                    Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();

                    if (response.body().job != null) {
                        job.jobStatus = response.body().job.jobStatus;
                        job.actualStartTime = response.body().job.actualStartTime;
                        job.actualEndTime = response.body().job.actualEndTime;
                        jobAdapter.notifyDataSetChanged();
                    }

                    // Only navigate when starting
                    if (action.equals("start")) {
                        Intent intent = new Intent(HomeActivity.this, JobManageActivity.class);
                        intent.putExtra("job_id", job.jobId);
                        intent.putExtra("part_number", job.partNumber);
                        intent.putExtra("target", job.target);
                        intent.putExtra("current", job.current);
                        intent.putExtra("job_status", job.jobStatus);
                        intent.putExtra("machine_name", job.machineName);
                        intent.putExtra("floor_name", job.floorName);
                        intent.putExtra("shift", job.shift);
                        intent.putExtra("avg_cycle_time", job.avgCycleTime);
                        intent.putExtra("priority", job.priority);
                        intent.putExtra("total_runtime", formatRuntimeMinutes(job.totalRuntime));
                        intent.putExtra("total_planned_downtime", formatDowntimeMinutes(job.totalPlannedDowntime));
                        intent.putExtra("total_unplanned_downtime", formatDowntimeMinutes(job.totalUnplannedDowntime));
                        intent.putExtra("defective", job.defective != null ? String.valueOf(job.defective) : "0");
                        intent.putExtra("actual_start_time", job.actualStartTime);
                        intent.putExtra("actual_end_time", job.actualEndTime);
                        intent.putExtra("machine_id", job.machineId);
                        startActivity(intent);
                    }
                } else {
                    String errorMsg = "Failed to " + action + " job";
                    if (response.body() != null && response.body().msg != null) errorMsg = response.body().msg;
                    showError(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void updateMachineDetailCard(Machine machine) {
        machineDetailCard.setVisibility(View.VISIBLE);
        selectedMachine = machine;
        Log.d(TAG, "Updating machine detail card for: " + machine.machName);

        tvDetailMachineName.setText(machine.machName);
        tvDetailStatus.setText(machine.machStatus);
        tvDetailHealth.setText("Health: " + (machine.healthScore != null ? machine.healthScore + "%" : "N/A"));
        tvDetailFloor.setText("Floor: " + (machine.floorName != null ? machine.floorName : "N/A"));
        tvDetailUid.setText("UID: " + (machine.machUid != null ? machine.machUid : "N/A"));

        TextView tvBrand = machineDetailCard.findViewById(R.id.tvDetailBrand);
        TextView tvCriticality = machineDetailCard.findViewById(R.id.tvDetailCriticality);
        TextView tvModel = machineDetailCard.findViewById(R.id.tvDetailModel);
        TextView tvManufacturer = machineDetailCard.findViewById(R.id.tvDetailManufacturer);
        TextView tvPurchaseDate = machineDetailCard.findViewById(R.id.tvDetailPurchaseDate);
        TextView tvInstallDate = machineDetailCard.findViewById(R.id.tvDetailInstallDate);
        TextView tvWarranty = machineDetailCard.findViewById(R.id.tvDetailWarranty);
        TextView tvPrevService = machineDetailCard.findViewById(R.id.tvDetailPrevService);
        TextView tvNextService = machineDetailCard.findViewById(R.id.tvDetailNextService);

        tvBrand.setText(machine.brand != null ? machine.brand : "---");
        tvCriticality.setText(machine.criticalityLevel != null ? machine.criticalityLevel : "---");
        tvModel.setText(machine.machineModel != null ? machine.machineModel : "---");
        tvManufacturer.setText(machine.manufacturer != null ? machine.manufacturer : "---");
        tvPurchaseDate.setText(machine.purchaseDate != null ? machine.purchaseDate : "---");
        tvInstallDate.setText(machine.installationDate != null ? machine.installationDate : "---");
        tvWarranty.setText(machine.warrantyExpiry != null ? machine.warrantyExpiry : "---");
        tvPrevService.setText(machine.machPreviousServiceDate != null ? machine.machPreviousServiceDate : "---");
        tvNextService.setText(machine.machNextServiceDate != null ? machine.machNextServiceDate : "---");

        statusIndicator.setBackgroundResource(machine.getStatusIndicatorDrawable());
    }

    private void refreshData() {
        Log.d(TAG, "Manual refresh triggered");
        Machine savedMachine = sessionManager.getSelectedMachine();
        if (savedMachine != null) {
            updateMachineDetailCard(savedMachine);
            fetchJobs(true);
        } else {
            machineDetailCard.setVisibility(View.GONE);
            jobList.clear();
            jobAdapter.notifyDataSetChanged();
            swipeRefresh.setRefreshing(false);
        }
    }

    private void openConfigActivity() {
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToProfile() {
        startActivity(new Intent(this, OperatorProfile.class));
    }

    private void showLogoutConfirmationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmLogout);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> { dialog.dismiss(); performLogout(); });
        dialog.show();
    }

    private void performLogout() {
        sessionManager.clearSession();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static void startPulseAnimation(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.08f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.08f);
        scaleX.setDuration(800); scaleY.setDuration(800);
        scaleX.setRepeatCount(ValueAnimator.INFINITE); scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.REVERSE); scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.start(); scaleY.start();
    }

    private static String formatDowntimeMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "0m";
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        else return minutes + "m";
    }

    private static String formatRuntimeMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "00:00";
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, 0);
        else return String.format(Locale.getDefault(), "%02d:%02d", minutes, 0);
    }

    // ---------- Retrofit API interface ----------
    interface ApiService {
        @POST("api/operator-app/get-operator-jobs")
        Call<JobResponse> getJobs(@Body JobRequest request);

        @POST("api/operator-app/job-action")
        Call<JobActionResponse> jobAction(@Body JobActionRequest request);
    }

    // ---------- Request / Response models ----------
    static class JobRequest {
        @SerializedName("tk") final String tk;
        @SerializedName("operator_id") final String operatorId;
        @SerializedName("shift_id") final String shiftId;
        @SerializedName("plant_id") final String plantId;
        JobRequest(String tk, String operatorId, String shiftId, String plantId) {
            this.tk = tk; this.operatorId = operatorId; this.shiftId = shiftId; this.plantId = plantId;
        }
    }

    static class JobResponse {
        @SerializedName("status") String status;
        @SerializedName("msg") String msg;
        @SerializedName("jobs") List<JobItem> jobs;
    }

    // ---------- Updated JobActionRequest ----------
    static class JobActionRequest {
        @SerializedName("tk")         final String tk;
        @SerializedName("jobid")      final String jobId;
        @SerializedName("action")     final String action;
        @SerializedName("jobendtime") final String jobEndTime;   // null for start
        @SerializedName("companyid")  final String companyId;

        JobActionRequest(String tk, String jobId, String action,
                         String jobEndTime, String companyId) {
            this.tk = tk;
            this.jobId = jobId;
            this.action = action;
            this.jobEndTime = jobEndTime;
            this.companyId = companyId;
        }
    }

    static class JobActionResponse {
        @SerializedName("status") String status;
        @SerializedName("msg") String msg;
        @SerializedName("job") JobUpdate job;
    }

    static class JobUpdate {
        @SerializedName("jobid") String jobId;
        @SerializedName("jobstatus") String jobStatus;
        @SerializedName("actualstarttime") String actualStartTime;
        @SerializedName("actualendtime") String actualEndTime;
    }

    static class JobItem {
        @SerializedName("id") int id;
        @SerializedName("jobid") String jobId;
        @SerializedName("averagecycletime") String avgCycleTime;
        @SerializedName("total_planneddowntime") int totalPlannedDowntime;
        @SerializedName("total_unplanneddowntime") int totalUnplannedDowntime;
        @SerializedName("total_runtime") int totalRuntime;
        @SerializedName("total_downtime") int totalDowntime;
        @SerializedName("jobstatus") String jobStatus;
        @SerializedName("production") String production;
        @SerializedName("assemblyline") String assemblyline;
        @SerializedName("product") String product;
        @SerializedName("partnumber") String partNumber;
        @SerializedName("operation_id") int operationId;
        @SerializedName("machine") String machineId;
        @SerializedName("shift") String shift;
        @SerializedName("operator") String operator;
        @SerializedName("target") String target;
        @SerializedName("current") String current;
        @SerializedName("planningdate") String planningDate;
        @SerializedName("executiondate") String executionDate;
        @SerializedName("actualstarttime") String actualStartTime;
        @SerializedName("actualendtime") String actualEndTime;
        @SerializedName("defectivepeice") Integer defective;
        @SerializedName("machine_name") String machineName;
        @SerializedName("machine_code") String machineCode;
        @SerializedName("machine_uid") String machineUid;
        @SerializedName("machine_status") String machineStatus;
        @SerializedName("floor_name") String floorName;
        @SerializedName("jobEndBasedOn") String jobEndBasedOn;
        @SerializedName("company") String company;
        @SerializedName("job_type") String jobType;
        @SerializedName("created_at") String createdAt;
        String priority;
    }

    // ---------- Adapters ----------
    static class JobAdapter extends RecyclerView.Adapter<JobAdapter.VH> {

        private List<JobItem> jobs;
        private OnJobClickListener listener;

        interface OnJobClickListener {
            void onJobClick(JobItem job);
            void onStartJobClick(JobItem job);
            void onStopJobClick(JobItem job);   // NEW
        }

        JobAdapter(List<JobItem> jobs, OnJobClickListener listener) {
            this.jobs = jobs;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_job_card, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JobItem job = jobs.get(position);

            // ----- Apply locally cached good count from SessionManager -----
            try {
                SessionManager sm = new SessionManager(holder.itemView.getContext());
                int cachedGood = sm.getGoodCount(job.jobId);
                if (cachedGood > 0) {
                    job.current = String.valueOf(cachedGood);
                }
            } catch (Exception e) {
                // ignore if context is not available
            }

            // ----- Apply locally cached defective count from SessionManager -----
            try {
                SessionManager sm2 = new SessionManager(holder.itemView.getContext());
                int cachedDef = sm2.getDefectiveCount(job.jobId);
                if (cachedDef > 0 && job.defective != null) {
                    job.defective = cachedDef;
                }
            } catch (Exception e) { }

            holder.tvJobId.setText(job.jobId);
            holder.tvProductName.setText("Part: " + job.partNumber);

            holder.tvJobStatus.setText(job.jobStatus != null ? job.jobStatus : "Unknown");
            updateStatusColor(holder, job.jobStatus);

            holder.tvMachineInfo.setText(job.machineName != null ? job.machineName : "N/A");
            holder.tvFloorInfo.setText(job.floorName != null ? job.floorName : "");

            int target = parseIntSafely(job.target);
            int current = parseIntSafely(job.current);
            holder.tvTarget.setText("Target: " + target);
            holder.tvActual.setText("Actual: " + current);
            int progress = (target > 0) ? (current * 100 / target) : 0;
            holder.progressBar.setProgressCompat(progress, true);

            int defective = (job.defective != null) ? job.defective : 0;
            holder.tvDefective.setText("Defect: " + defective);

            holder.tvStartTime.setText("Job Start: " + (job.actualStartTime != null ? job.actualStartTime : "--"));
            holder.tvEndTime.setText("Job End: " + (job.actualEndTime != null ? job.actualEndTime : "--"));

            holder.tvPlannedDowntime.setText("Planned Downtime: " + HomeActivity.formatDowntimeMinutes(job.totalPlannedDowntime));
            holder.tvUnplannedDowntime.setText("Unplanned Downtime: " + HomeActivity.formatDowntimeMinutes(job.totalUnplannedDowntime));
            holder.tvTotalRuntime.setText("Total Runtime: " + HomeActivity.formatRuntimeMinutes(job.totalRuntime));

            holder.tvShift.setText(job.shift != null ? job.shift : "N/A");

            String priority = job.priority != null ? job.priority : "HIGH";
            holder.tvPriority.setText(priority.toUpperCase());
            holder.tvPriority.setVisibility(priority.isEmpty() ? View.GONE : View.VISIBLE);

            // Start / Stop buttons visibility
            if (job.jobStatus != null) {
                if (job.jobStatus.equalsIgnoreCase("Not Started")) {
                    holder.btnStartJob.setVisibility(View.VISIBLE);
                    holder.btnStopJob.setVisibility(View.GONE);
                    // pulse animation on Start button
                    holder.btnStartJob.animate().cancel();
                    holder.btnStartJob.clearAnimation();
                    startPulseAnimation(holder.btnStartJob);
                } else if (job.jobStatus.equalsIgnoreCase("Running")) {
                    holder.btnStartJob.setVisibility(View.GONE);
                    holder.btnStopJob.setVisibility(View.VISIBLE);
                    holder.btnStartJob.clearAnimation();
                } else {
                    holder.btnStartJob.setVisibility(View.GONE);
                    holder.btnStopJob.setVisibility(View.GONE);
                    holder.btnStartJob.clearAnimation();
                }
            } else {
                holder.btnStartJob.setVisibility(View.GONE);
                holder.btnStopJob.setVisibility(View.GONE);
                holder.btnStartJob.clearAnimation();
            }

            // Click listeners
            holder.btnStartJob.setOnClickListener(v -> {
                if (listener != null) listener.onStartJobClick(job);
            });
            holder.btnStopJob.setOnClickListener(v -> {
                if (listener != null) listener.onStopJobClick(job);
            });
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onJobClick(job);
            });
        }

        @Override public int getItemCount() { return jobs.size(); }

        private int parseIntSafely(String value) {
            try { return Integer.parseInt(value); }
            catch (Exception e) { return 0; }
        }

        private void updateStatusColor(VH holder, String status) {
            int dotColor;
            if (status == null) dotColor = R.drawable.circle_status_green;
            else switch (status.toLowerCase()) {
                case "running":   dotColor = R.drawable.circle_status_green;  break;
                case "not started": dotColor = R.drawable.circle_status_yellow; break;
                case "completed": dotColor = R.drawable.circle_status_green;  break;
                default:          dotColor = R.drawable.circle_status_red;    break;
            }
            holder.statusDot.setBackgroundResource(dotColor);
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvJobId, tvProductName, tvJobStatus, tvMachineInfo,
                    tvFloorInfo, tvTarget, tvActual, tvDefective,
                    tvStartTime, tvEndTime,
                    tvPlannedDowntime, tvUnplannedDowntime, tvTotalRuntime,
                    tvShift, tvPriority;
            LinearProgressIndicator progressBar;
            View statusDot;
            Button btnStartJob, btnStopJob;   // NEW

            VH(@NonNull View itemView) {
                super(itemView);
                tvJobId = itemView.findViewById(R.id.tvJobId);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvJobStatus = itemView.findViewById(R.id.tvJobStatus);
                tvMachineInfo = itemView.findViewById(R.id.tvMachineInfo);
                tvFloorInfo = itemView.findViewById(R.id.tvFloorInfo);
                tvTarget = itemView.findViewById(R.id.tvTarget);
                tvActual = itemView.findViewById(R.id.tvActual);
                tvDefective = itemView.findViewById(R.id.tvDefective);
                tvStartTime = itemView.findViewById(R.id.tvStartTime);
                tvEndTime = itemView.findViewById(R.id.tvEndTime);
                tvPlannedDowntime = itemView.findViewById(R.id.tvPlannedDowntime);
                tvUnplannedDowntime = itemView.findViewById(R.id.tvUnplannedDowntime);
                tvTotalRuntime = itemView.findViewById(R.id.tvTotalRuntime);
                tvShift = itemView.findViewById(R.id.tvShift);
                tvPriority = itemView.findViewById(R.id.tvPriority);
                progressBar = itemView.findViewById(R.id.progressBar);
                statusDot = itemView.findViewById(R.id.statusDot);
                btnStartJob = itemView.findViewById(R.id.btnStartJob);
                btnStopJob = itemView.findViewById(R.id.btnStopJob);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExit) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExit = true;
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show();
        backHandler.postDelayed(() -> doubleBackToExit = false, 2000);
    }
}