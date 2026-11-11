package com.example.andonoperatorapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

public class JobManageActivity extends AppCompatActivity {

    private static final String TAG = "JobManageActivity";
    private static final String BASE_URL = "https://smartandon.robatosystems.com/";
    private static final String STATIC_TOKEN = "b3BlcmF0b3JBcHBTdGF0aWNUb2tlbjEyMw==";
    private static final long AUTO_REFRESH_INTERVAL = 30_000;

    // Job detail views
    private TextView tvJobId, tvJobStatus, tvPartNumber, tvTarget, tvActual, tvDefective,
            tvMachineName, tvFloor, tvShift, tvPriority,
            tvStartTime, tvEndTime, tvPlannedDowntime, tvUnplannedDowntime, tvTotalRuntime;

    // Count controls
    private TextView tvGoodCount, tvDefectiveCount;
    private Button btnGoodPlus, btnGoodMinus, btnDefectivePlus, btnDefectiveMinus;

    // Downtime cards
    private View cardPlanned, cardUnplanned;
    private ScrollView scrollPlannedCategories, scrollUnplannedCategories;

    // Planned active views
    private View layoutPlannedCategories, layoutPlannedActive;
    private TextView tvPlannedActiveCategory, tvPlannedActiveSubcategory, tvPlannedTimer;
    private Button btnStopPlanned;

    // Unplanned active views
    private View layoutUnplannedCategories, layoutUnplannedActive;
    private TextView tvUnplannedActiveCategory, tvUnplannedActiveSubcategory, tvUnplannedTimer;
    private Button btnStopUnplanned;

    // Status labels
    private TextView tvPlannedStatus, tvUnplannedStatus;

    // Count values
    private int goodCount = 0;
    private int defectiveCount = 0;

    // Timer fields for downtime
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long plannedStartTime = 0, unplannedStartTime = 0;
    private boolean plannedRunning = false, unplannedRunning = false;
    private Runnable plannedTimerRunnable, unplannedTimerRunnable;

    // Dynamic downtime categories
    private List<DowntimeCategory> plannedCategoryList = new ArrayList<>();
    private List<DowntimeCategory> unplannedCategoryList = new ArrayList<>();
    private final Map<String, String[]> plannedSubMap = new HashMap<>();
    private final Map<String, String[]> unplannedSubMap = new HashMap<>();

    // API
    private ApiService apiService;
    private String plantId;
    private String companyId;

    // Job identifiers
    private String currentJobId;
    private String currentMachineId;
    private String operatorId;
    private String shiftId;

    // Auto‑refresh
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;

    private SessionManager sessionManager;

    // Store login response for shift name / company id
    private LoginActivity.LoginResponse loginResponse;

    // ID of the active planned downtime (for later stop)
    private int plannedDowntimeId = 0;

    // Flag to disable interactions when job is completed
    private boolean jobCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_manage);

        // Back button
        ImageView ivBack = findViewById(R.id.ivBack);
        ivBack.setOnClickListener(v -> finish());

        // Bind job detail views
        tvJobId = findViewById(R.id.tvJobId);
        tvJobStatus = findViewById(R.id.tvJobStatus);
        tvPartNumber = findViewById(R.id.tvPartNumber);
        tvTarget = findViewById(R.id.tvTarget);
        tvActual = findViewById(R.id.tvActual);
        tvDefective = findViewById(R.id.tvDefective);
        tvMachineName = findViewById(R.id.tvMachineName);
        tvFloor = findViewById(R.id.tvFloor);
        tvShift = findViewById(R.id.tvShift);
        tvPriority = findViewById(R.id.tvPriority);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        tvPlannedDowntime = findViewById(R.id.tvPlannedDowntime);
        tvUnplannedDowntime = findViewById(R.id.tvUnplannedDowntime);
        tvTotalRuntime = findViewById(R.id.tvTotalRuntime);

        // Bind count views
        tvGoodCount = findViewById(R.id.tvGoodCount);
        tvDefectiveCount = findViewById(R.id.tvDefectiveCount);
        btnGoodPlus = findViewById(R.id.btnGoodPlus);
        btnGoodMinus = findViewById(R.id.btnGoodMinus);
        btnDefectivePlus = findViewById(R.id.btnDefectivePlus);
        btnDefectiveMinus = findViewById(R.id.btnDefectiveMinus);

        // Bind downtime header views
        cardPlanned = findViewById(R.id.cardPlannedDowntime);
        cardUnplanned = findViewById(R.id.cardUnplannedDowntime);
        tvPlannedStatus = findViewById(R.id.tvPlannedStatus);
        tvUnplannedStatus = findViewById(R.id.tvUnplannedStatus);

        // Bind planned active views
        layoutPlannedCategories = findViewById(R.id.layoutPlannedCategories);
        layoutPlannedActive = findViewById(R.id.layoutPlannedActive);
        tvPlannedActiveCategory = findViewById(R.id.tvPlannedActiveCategory);
        tvPlannedActiveSubcategory = findViewById(R.id.tvPlannedActiveSubcategory);
        tvPlannedTimer = findViewById(R.id.tvPlannedTimer);
        btnStopPlanned = findViewById(R.id.btnStopPlanned);
        scrollPlannedCategories = findViewById(R.id.scrollPlannedCategories);

        // Bind unplanned active views
        layoutUnplannedCategories = findViewById(R.id.layoutUnplannedCategories);
        layoutUnplannedActive = findViewById(R.id.layoutUnplannedActive);
        tvUnplannedActiveCategory = findViewById(R.id.tvUnplannedActiveCategory);
        tvUnplannedActiveSubcategory = findViewById(R.id.tvUnplannedActiveSubcategory);
        tvUnplannedTimer = findViewById(R.id.tvUnplannedTimer);
        btnStopUnplanned = findViewById(R.id.btnStopUnplanned);
        scrollUnplannedCategories = findViewById(R.id.scrollUnplannedCategories);

        // Initialize session manager early (needed for restoration)
        sessionManager = new SessionManager(this);

        // ---------- Restore downtime state from persistent storage ----------
        // Planned
        if (sessionManager.isPlannedDowntimeRunning()) {
            plannedRunning = true;
            plannedStartTime = sessionManager.getPlannedDowntimeStartTime();
            String savedCat = sessionManager.getPlannedDowntimeCategory();
            String savedSub = sessionManager.getPlannedDowntimeSubcategory();
            tvPlannedActiveCategory.setText(savedCat);
            tvPlannedActiveSubcategory.setText(savedSub);
            tvPlannedStatus.setText("Active");
            scrollPlannedCategories.setVisibility(View.GONE);
            layoutPlannedActive.setVisibility(View.VISIBLE);

            plannedTimerRunnable = () -> {
                long elapsed = System.currentTimeMillis() - plannedStartTime;
                tvPlannedTimer.setText(formatTime(elapsed));
                timerHandler.postDelayed(plannedTimerRunnable, 1000);
            };
            timerHandler.post(plannedTimerRunnable);
        }

        // Unplanned
        if (sessionManager.isUnplannedDowntimeRunning()) {
            unplannedRunning = true;
            unplannedStartTime = sessionManager.getUnplannedDowntimeStartTime();
            String savedCat = sessionManager.getUnplannedDowntimeCategory();
            String savedSub = sessionManager.getUnplannedDowntimeSubcategory();
            tvUnplannedActiveCategory.setText(savedCat);
            tvUnplannedActiveSubcategory.setText(savedSub);
            tvUnplannedStatus.setText("Active");
            scrollUnplannedCategories.setVisibility(View.GONE);
            layoutUnplannedActive.setVisibility(View.VISIBLE);

            unplannedTimerRunnable = () -> {
                long elapsed = System.currentTimeMillis() - unplannedStartTime;
                tvUnplannedTimer.setText(formatTime(elapsed));
                timerHandler.postDelayed(unplannedTimerRunnable, 1000);
            };
            timerHandler.post(unplannedTimerRunnable);
        }

        // Load data from intent
        Intent intent = getIntent();
        currentJobId = intent.getStringExtra("job_id");
        currentMachineId = intent.getStringExtra("machine_id");
        tvJobId.setText(safeString(currentJobId));
        tvPartNumber.setText(safeString(intent.getStringExtra("part_number")));
        tvTarget.setText(safeString(intent.getStringExtra("target")));
        tvActual.setText(safeString(intent.getStringExtra("current")));
        tvDefective.setText(safeString(intent.getStringExtra("defective")));
        tvMachineName.setText(safeString(intent.getStringExtra("machine_name")));
        tvFloor.setText(safeString(intent.getStringExtra("floor_name")));
        tvShift.setText(safeString(intent.getStringExtra("shift")));
        String priority = intent.getStringExtra("priority");
        tvPriority.setText((priority != null && !priority.isEmpty()) ? priority : "HIGH");
        String jobStatus = intent.getStringExtra("job_status");
        tvJobStatus.setText(safeString(jobStatus));

        // ---- Check if job is completed and disable interactions with Toast ----
        if (jobStatus != null && jobStatus.equalsIgnoreCase("Completed")) {
            jobCompleted = true;
            applyJobCompletedState();
        }

        tvStartTime.setText(": " + safeString(intent.getStringExtra("actual_start_time")));
        tvEndTime.setText(": " + safeString(intent.getStringExtra("actual_end_time")));
        tvPlannedDowntime.setText(": " + safeString(intent.getStringExtra("total_planned_downtime")));
        tvUnplannedDowntime.setText(": " + safeString(intent.getStringExtra("total_unplanned_downtime")));
        tvTotalRuntime.setText(": " + safeString(intent.getStringExtra("total_runtime")));

        // ---- Good count initialisation ----
        String currentStr = intent.getStringExtra("current");
        int apiGood = parseIntSafely(currentStr);
        int localGood = sessionManager.getGoodCount(currentJobId);
        goodCount = (localGood > 0) ? localGood : apiGood;
        tvGoodCount.setText(String.valueOf(goodCount));
        tvActual.setText(String.valueOf(goodCount));

        // ---- Defective count initialisation ----
        String defectiveStr = intent.getStringExtra("defective");
        int apiDefective = parseIntSafely(defectiveStr);
        int localDefective = sessionManager.getDefectiveCount(currentJobId);
        defectiveCount = (localDefective > 0) ? localDefective : apiDefective;
        tvDefectiveCount.setText(String.valueOf(defectiveCount));

        // Good count buttons with jobCompleted check
        btnGoodPlus.setOnClickListener(v -> {
            if (jobCompleted) {
                Toast.makeText(this, "Job is completed", Toast.LENGTH_SHORT).show();
                return;
            }
            int newVal = goodCount + 1;
            animateButton(v);
            updateGoodCountApi(newVal);
        });
        btnGoodMinus.setOnClickListener(v -> {
            if (jobCompleted) {
                Toast.makeText(this, "Job is completed", Toast.LENGTH_SHORT).show();
                return;
            }
            if (goodCount > 0) {
                int newVal = goodCount - 1;
                animateButton(v);
                updateGoodCountApi(newVal);
            }
        });

        // Defective count buttons with jobCompleted check
        btnDefectivePlus.setOnClickListener(v -> {
            if (jobCompleted) {
                Toast.makeText(this, "Job is completed", Toast.LENGTH_SHORT).show();
                return;
            }
            int newVal = defectiveCount + 1;
            animateButton(v);
            updateDefectiveCountApi(newVal);
        });
        btnDefectiveMinus.setOnClickListener(v -> {
            if (jobCompleted) {
                Toast.makeText(this, "Job is completed", Toast.LENGTH_SHORT).show();
                return;
            }
            if (defectiveCount > 0) {
                int newVal = defectiveCount - 1;
                animateButton(v);
                updateDefectiveCountApi(newVal);
            }
        });

        LoginActivity.Employee employee = sessionManager.getEmployee();
        plantId = employee != null ? employee.getPlantIdString() : "3";
        loginResponse = sessionManager.getLoginResponse();
        companyId = (loginResponse != null && loginResponse.company != null) ?
                String.valueOf(loginResponse.company.companyId) : "45";
        operatorId = employee != null ? employee.id : "0";
        shiftId = (loginResponse != null && loginResponse.shifts != null && !loginResponse.shifts.isEmpty()) ?
                String.valueOf(loginResponse.shifts.get(0).shiftId) : "0";

        // Init API
        apiService = buildApiService();
        fetchDowntimeCategories();

        // Auto‑refresh job details
        autoRefreshRunnable = () -> {
            fetchJobDetails();
            refreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
        };

        btnStopPlanned.setOnClickListener(v -> stopPlannedDowntime());
        btnStopUnplanned.setOnClickListener(v -> stopUnplannedDowntime());
    }

    /**
     * Visually disables downtime containers and count buttons, but keeps them clickable
     * so a Toast can be shown.
     */
    private void applyJobCompletedState() {
        // Reduce opacity to look disabled
        scrollPlannedCategories.setAlpha(0.5f);
        scrollUnplannedCategories.setAlpha(0.5f);
        layoutPlannedCategories.setAlpha(0.5f);
        layoutUnplannedCategories.setAlpha(0.5f);

        btnGoodPlus.setAlpha(0.5f);
        btnGoodMinus.setAlpha(0.5f);
        btnDefectivePlus.setAlpha(0.5f);
        btnDefectiveMinus.setAlpha(0.5f);

        // Hide stop buttons if visible
        btnStopPlanned.setVisibility(View.GONE);
        btnStopUnplanned.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchJobDetails();
        refreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
    }

    private String safeString(String value) {
        return (value != null && !value.isEmpty()) ? value : "---";
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

    private void fetchJobDetails() {
        JobRequest request = new JobRequest(STATIC_TOKEN, operatorId, shiftId, plantId);
        apiService.getJobs(request).enqueue(new Callback<JobResponse>() {
            @Override
            public void onResponse(Call<JobResponse> call, Response<JobResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    List<JobItem> jobs = response.body().jobs;
                    if (jobs != null) {
                        for (JobItem job : jobs) {
                            if (job.jobId != null && job.jobId.equals(currentJobId)) {
                                tvJobStatus.setText(job.jobStatus != null ? job.jobStatus : "---");
                                int apiCount = parseIntSafely(job.current);
                                int localSavedCount = sessionManager.getGoodCount(currentJobId);
                                if (localSavedCount != 0) goodCount = localSavedCount;
                                else goodCount = apiCount;
                                tvGoodCount.setText(String.valueOf(goodCount));
                                tvActual.setText(String.valueOf(goodCount));
                                tvDefective.setText(job.defective != null ? String.valueOf(job.defective) : "0");
                                tvStartTime.setText("Start: " + (job.actualStartTime != null ? job.actualStartTime : "--"));
                                tvEndTime.setText("End: " + (job.actualEndTime != null ? job.actualEndTime : "--"));
                                tvTotalRuntime.setText("Runtime: " + formatRuntimeMinutes(job.totalRuntime));
                                tvPlannedDowntime.setText("Planned: " + formatDowntimeMinutes(job.totalPlannedDowntime));
                                tvUnplannedDowntime.setText("Unplanned: " + formatDowntimeMinutes(job.totalUnplannedDowntime));
                                break;
                            }
                        }
                    }
                }
            }
            @Override
            public void onFailure(Call<JobResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Failed to fetch jobs", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateGoodCountApi(int newValue) {
        int previousCount = goodCount;
        goodCount = newValue;
        sessionManager.saveGoodCount(currentJobId, goodCount);
        updateGoodCount();
        JobActionRequest actionReq = new JobActionRequest(STATIC_TOKEN, currentJobId, "good_count", newValue, null, currentMachineId);
        apiService.jobAction(actionReq).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    if (response.body().job != null && response.body().job.current != null) {
                        goodCount = parseIntSafely(response.body().job.current);
                        sessionManager.saveGoodCount(currentJobId, goodCount);
                        updateGoodCount();
                    }
                    if (response.body().job != null && response.body().job.jobStatus != null) {
                        tvJobStatus.setText(response.body().job.jobStatus);
                    }
                } else {
                    goodCount = previousCount;
                    sessionManager.saveGoodCount(currentJobId, goodCount);
                    updateGoodCount();
                    String err = "Failed to update count";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                goodCount = previousCount;
                sessionManager.saveGoodCount(currentJobId, goodCount);
                updateGoodCount();
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDefectiveCountApi(int newValue) {
        int previousCount = defectiveCount;
        defectiveCount = newValue;
        sessionManager.saveDefectiveCount(currentJobId, defectiveCount);
        updateDefectiveCountUI();
        JobActionRequest actionReq = new JobActionRequest(STATIC_TOKEN, currentJobId, "defective_count", newValue, null, currentMachineId);
        apiService.jobAction(actionReq).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    if (response.body().job != null && response.body().job.defective != null) {
                        defectiveCount = parseIntSafely(response.body().job.defective);
                        sessionManager.saveDefectiveCount(currentJobId, defectiveCount);
                        updateDefectiveCountUI();
                    }
                    if (response.body().job != null && response.body().job.jobStatus != null) {
                        tvJobStatus.setText(response.body().job.jobStatus);
                    }
                } else {
                    defectiveCount = previousCount;
                    sessionManager.saveDefectiveCount(currentJobId, defectiveCount);
                    updateDefectiveCountUI();
                    String err = "Failed to update defective count";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                defectiveCount = previousCount;
                sessionManager.saveDefectiveCount(currentJobId, defectiveCount);
                updateDefectiveCountUI();
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDefectiveCountUI() {
        tvDefectiveCount.setText(String.valueOf(defectiveCount));
        tvDefective.setText(String.valueOf(defectiveCount));
        animateCountText(tvDefectiveCount);
    }

    private int parseIntSafely(String val) {
        try {
            if (val == null) return 0;
            val = val.trim();
            if (val.isEmpty()) return 0;
            return Integer.parseInt(val);
        } catch (Exception e) { return 0; }
    }

    // ---------- Downtime category fetch & button population ----------
    private void fetchDowntimeCategories() {
        DowntimeCategoryRequest request = new DowntimeCategoryRequest(STATIC_TOKEN, plantId, companyId);
        apiService.getDowntimeCategories(request).enqueue(new Callback<DowntimeCategoryResponse>() {
            @Override
            public void onResponse(Call<DowntimeCategoryResponse> call, Response<DowntimeCategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    plannedCategoryList.clear();
                    unplannedCategoryList.clear();
                    if (response.body().data != null) {
                        if (response.body().data.planned != null) plannedCategoryList.addAll(response.body().data.planned);
                        if (response.body().data.unplanned != null) unplannedCategoryList.addAll(response.body().data.unplanned);
                    }
                    populatePlannedButtons();
                    populateUnplannedButtons();
                } else {
                    Toast.makeText(JobManageActivity.this, "Failed to load downtime categories", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<DowntimeCategoryResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populatePlannedButtons() {
        LinearLayout container = (LinearLayout) layoutPlannedCategories;
        container.removeAllViews();
        if (plannedCategoryList.isEmpty()) return;
        for (int i = 0; i < plannedCategoryList.size(); i++) {
            DowntimeCategory cat = plannedCategoryList.get(i);
            Button btn = createOutlineButton(cat.categoryName, "#90CAF9");
            final int index = i;
            btn.setOnClickListener(v -> {
                if (jobCompleted) {
                    Toast.makeText(JobManageActivity.this, "Job is completed", Toast.LENGTH_SHORT).show();
                    return;
                }
                showPlannedDialog(index);
            });
            container.addView(btn);
        }
        arrangeButtonsInRows(container, plannedCategoryList.size(), 1);
        if (jobCompleted) {
            container.setAlpha(0.5f);
        }
    }

    private void populateUnplannedButtons() {
        LinearLayout container = (LinearLayout) layoutUnplannedCategories;
        container.removeAllViews();
        if (unplannedCategoryList.isEmpty()) return;
        for (int i = 0; i < unplannedCategoryList.size(); i++) {
            DowntimeCategory cat = unplannedCategoryList.get(i);
            Button btn = createOutlineButton(cat.categoryName, "#EF9A9A");
            final int index = i;
            btn.setOnClickListener(v -> {
                if (jobCompleted) {
                    Toast.makeText(JobManageActivity.this, "Job is completed", Toast.LENGTH_SHORT).show();
                    return;
                }
                showUnplannedDialog(index);
            });
            container.addView(btn);
        }
        arrangeButtonsInRows(container, unplannedCategoryList.size(), 1);
        if (jobCompleted) {
            container.setAlpha(0.5f);
        }
    }

    private Button createOutlineButton(String text, String strokeColor) {
        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(getResources().getColor(R.color.white, null));
        btn.setAllCaps(false);
        btn.setMaxLines(1);
        btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        btn.setStrokeColor(ColorStateList.valueOf(android.graphics.Color.parseColor(strokeColor)));
        btn.setStrokeWidth(dpToPx(1));
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
    }

    private void arrangeButtonsInRows(LinearLayout container, int total, int columns) {
        List<View> buttons = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) buttons.add(container.getChildAt(i));
        container.removeAllViews();
        int rows = (int) Math.ceil((double) total / columns);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rowParams.topMargin = dpToPx(6);
            row.setLayoutParams(rowParams);
            for (int c = 0; c < columns; c++) {
                int index = r * columns + c;
                if (index < total) {
                    Button btn = (Button) buttons.get(index);
                    if (btn.getParent() != null) ((ViewGroup) btn.getParent()).removeView(btn);
                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    btnParams.height = dpToPx(44);
                    if (c > 0) btnParams.leftMargin = dpToPx(6);
                    btn.setLayoutParams(btnParams);
                    row.addView(btn);
                }
            }
            container.addView(row);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ---------- Downtime Dialogs ----------
    private void showPlannedDialog(int catIndex) {
        if (jobCompleted) return;
        if (catIndex < 0 || catIndex >= plannedCategoryList.size()) return;
        DowntimeCategory cat = plannedCategoryList.get(catIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_unplanned_downtime, null);
        builder.setView(dialogView);

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        if (title != null) title.setText("Planned: " + cat.categoryName);

        Spinner spinnerSub = dialogView.findViewById(R.id.spinnerSubcategory);
        EditText etReason = dialogView.findViewById(R.id.etReason);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitDowntime);

        btnSubmit.setText("Start Planned Downtime");
        btnSubmit.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.planned_button)));

        List<String> reasonNames = new ArrayList<>();
        if (cat.reasons != null) for (DowntimeReason r : cat.reasons) reasonNames.add(r.reasonName);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item_white, reasonNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spinnerSub.setAdapter(adapter);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSubmit.setOnClickListener(v -> {
            String sub = spinnerSub.getSelectedItem().toString();
            String reason = etReason.getText().toString().trim();
            startPlannedDowntime(cat.categoryName, sub, reason);
            dialog.dismiss();
        });
    }

    private void showUnplannedDialog(int catIndex) {
        if (jobCompleted) return;
        if (catIndex < 0 || catIndex >= unplannedCategoryList.size()) return;
        DowntimeCategory cat = unplannedCategoryList.get(catIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_unplanned_downtime, null);
        builder.setView(dialogView);

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        if (title != null) title.setText("Unplanned: " + cat.categoryName);

        Spinner spinnerSub = dialogView.findViewById(R.id.spinnerSubcategory);
        EditText etReason = dialogView.findViewById(R.id.etReason);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitDowntime);

        btnSubmit.setText("Start Unplanned Downtime");
        btnSubmit.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.unplanned_button)));

        List<String> reasonNames = new ArrayList<>();
        if (cat.reasons != null) for (DowntimeReason r : cat.reasons) reasonNames.add(r.reasonName);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item_white, reasonNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spinnerSub.setAdapter(adapter);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSubmit.setOnClickListener(v -> {
            String sub = spinnerSub.getSelectedItem().toString();
            String reason = etReason.getText().toString().trim();
            startUnplannedDowntime(cat.categoryName, sub, reason);
            dialog.dismiss();
        });
    }

    // ---------- Timer start/stop ----------

    // ========== Planned Downtime ==========
    private void startPlannedDowntime(String category, String subcategory, String reason) {
        if (jobCompleted) return;
        String onTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date());
        String shiftName = (loginResponse != null && loginResponse.shifts != null && !loginResponse.shifts.isEmpty())
                ? loginResponse.shifts.get(0).shiftName : "General Shift";
        String companyId = (loginResponse != null && loginResponse.company != null)
                ? String.valueOf(loginResponse.company.companyId) : "45";

        PlannedDowntimeRequest request = new PlannedDowntimeRequest(
                STATIC_TOKEN, currentJobId, "start_planned_downtime", currentMachineId,
                shiftName, "Planned", category, reason.isEmpty() ? subcategory : reason, onTime, companyId);

        apiService.jobAction(request).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    plannedRunning = true;
                    plannedStartTime = System.currentTimeMillis();
                    tvPlannedStatus.setText("Active");
                    tvPlannedActiveCategory.setText(category);
                    tvPlannedActiveSubcategory.setText(subcategory);
                    scrollPlannedCategories.setVisibility(View.GONE);
                    layoutPlannedActive.setVisibility(View.VISIBLE);

                    sessionManager.savePlannedDowntimeRunning(true);
                    sessionManager.savePlannedDowntimeStartTime(plannedStartTime);
                    sessionManager.savePlannedDowntimeCategory(category);
                    sessionManager.savePlannedDowntimeSubcategory(subcategory);

                    plannedTimerRunnable = () -> {
                        long elapsed = System.currentTimeMillis() - plannedStartTime;
                        tvPlannedTimer.setText(formatTime(elapsed));
                        timerHandler.postDelayed(plannedTimerRunnable, 1000);
                    };
                    timerHandler.post(plannedTimerRunnable);

                    if (response.body().downtimeLog != null) {
                        plannedDowntimeId = response.body().downtimeLog.id;
                    }
                    Toast.makeText(JobManageActivity.this, "Planned downtime started", Toast.LENGTH_SHORT).show();
                } else {
                    String err = "Failed to start planned downtime";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopPlannedDowntime() {
        String shiftName = (loginResponse != null && loginResponse.shifts != null && !loginResponse.shifts.isEmpty())
                ? loginResponse.shifts.get(0).shiftName : "General Shift";
        String reasonType = tvPlannedActiveCategory.getText().toString();
        String reason = tvPlannedActiveSubcategory.getText().toString();
        String offTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date());

        StopPlannedDowntimeRequest request = new StopPlannedDowntimeRequest(
                STATIC_TOKEN, currentJobId, "stop_planned_downtime", currentMachineId,
                shiftName, "Planned", reasonType, reason, offTime);

        apiService.jobAction(request).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    timerHandler.removeCallbacks(plannedTimerRunnable);
                    plannedRunning = false;
                    tvPlannedStatus.setText("Off");
                    scrollPlannedCategories.setVisibility(View.VISIBLE);
                    layoutPlannedActive.setVisibility(View.GONE);
                    sessionManager.clearPlannedDowntimeState();

                    if (response.body().downtimeLog != null && response.body().downtimeLog.durationMins != null) {
                        int totalMinutes = Integer.parseInt(response.body().downtimeLog.durationMins);
                        tvPlannedDowntime.setText("Planned: " + formatDowntimeMinutes(totalMinutes));
                    }
                    Toast.makeText(JobManageActivity.this, "Planned downtime stopped", Toast.LENGTH_SHORT).show();
                } else {
                    String err = "Failed to stop planned downtime";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                    // stop locally anyway
                    timerHandler.removeCallbacks(plannedTimerRunnable);
                    plannedRunning = false;
                    tvPlannedStatus.setText("Off");
                    scrollPlannedCategories.setVisibility(View.VISIBLE);
                    layoutPlannedActive.setVisibility(View.GONE);
                    sessionManager.clearPlannedDowntimeState();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                timerHandler.removeCallbacks(plannedTimerRunnable);
                plannedRunning = false;
                tvPlannedStatus.setText("Off");
                scrollPlannedCategories.setVisibility(View.VISIBLE);
                layoutPlannedActive.setVisibility(View.GONE);
                sessionManager.clearPlannedDowntimeState();
            }
        });
    }

    // ========== Unplanned Downtime ==========
    private void startUnplannedDowntime(String category, String subcategory, String reason) {
        if (jobCompleted) return;
        String onTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date());
        String shiftName = (loginResponse != null && loginResponse.shifts != null && !loginResponse.shifts.isEmpty())
                ? loginResponse.shifts.get(0).shiftName : "General Shift";
        String companyId = (loginResponse != null && loginResponse.company != null)
                ? String.valueOf(loginResponse.company.companyId) : "45";

        StartUnplannedDowntimeRequest request = new StartUnplannedDowntimeRequest(
                STATIC_TOKEN, currentJobId, "start_unplanned_downtime", currentMachineId,
                shiftName, "Unplanned", category,
                reason.isEmpty() ? subcategory : reason, onTime, companyId);

        apiService.jobAction(request).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    unplannedRunning = true;
                    unplannedStartTime = System.currentTimeMillis();
                    tvUnplannedStatus.setText("Active");
                    tvUnplannedActiveCategory.setText(category);
                    tvUnplannedActiveSubcategory.setText(subcategory);
                    scrollUnplannedCategories.setVisibility(View.GONE);
                    layoutUnplannedActive.setVisibility(View.VISIBLE);

                    sessionManager.saveUnplannedDowntimeRunning(true);
                    sessionManager.saveUnplannedDowntimeStartTime(unplannedStartTime);
                    sessionManager.saveUnplannedDowntimeCategory(category);
                    sessionManager.saveUnplannedDowntimeSubcategory(subcategory);

                    unplannedTimerRunnable = () -> {
                        long elapsed = System.currentTimeMillis() - unplannedStartTime;
                        tvUnplannedTimer.setText(formatTime(elapsed));
                        timerHandler.postDelayed(unplannedTimerRunnable, 1000);
                    };
                    timerHandler.post(unplannedTimerRunnable);

                    Toast.makeText(JobManageActivity.this, "Unplanned downtime started", Toast.LENGTH_SHORT).show();
                } else {
                    String err = "Failed to start unplanned downtime";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopUnplannedDowntime() {
        String shiftName = (loginResponse != null && loginResponse.shifts != null && !loginResponse.shifts.isEmpty())
                ? loginResponse.shifts.get(0).shiftName : "General Shift";
        String reasonType = tvUnplannedActiveCategory.getText().toString();
        String reason = tvUnplannedActiveSubcategory.getText().toString();
        String offTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date());

        StopUnplannedDowntimeRequest request = new StopUnplannedDowntimeRequest(
                STATIC_TOKEN, currentJobId, "stop_unplanned_downtime", currentMachineId,
                shiftName, "Unplanned", reasonType, reason, offTime);

        apiService.jobAction(request).enqueue(new Callback<JobActionResponse>() {
            @Override
            public void onResponse(Call<JobActionResponse> call, Response<JobActionResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    timerHandler.removeCallbacks(unplannedTimerRunnable);
                    unplannedRunning = false;
                    tvUnplannedStatus.setText("Off");
                    scrollUnplannedCategories.setVisibility(View.VISIBLE);
                    layoutUnplannedActive.setVisibility(View.GONE);
                    sessionManager.clearUnplannedDowntimeState();

                    if (response.body().downtimeLog != null && response.body().downtimeLog.durationMins != null) {
                        int totalMinutes = Integer.parseInt(response.body().downtimeLog.durationMins);
                        tvUnplannedDowntime.setText("Unplanned: " + formatDowntimeMinutes(totalMinutes));
                    }
                    Toast.makeText(JobManageActivity.this, "Unplanned downtime stopped", Toast.LENGTH_SHORT).show();
                } else {
                    String err = "Failed to stop unplanned downtime";
                    if (response.body() != null && response.body().msg != null) err = response.body().msg;
                    Toast.makeText(JobManageActivity.this, err, Toast.LENGTH_SHORT).show();
                    timerHandler.removeCallbacks(unplannedTimerRunnable);
                    unplannedRunning = false;
                    tvUnplannedStatus.setText("Off");
                    scrollUnplannedCategories.setVisibility(View.VISIBLE);
                    layoutUnplannedActive.setVisibility(View.GONE);
                    sessionManager.clearUnplannedDowntimeState();
                }
            }
            @Override
            public void onFailure(Call<JobActionResponse> call, Throwable t) {
                Toast.makeText(JobManageActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                timerHandler.removeCallbacks(unplannedTimerRunnable);
                unplannedRunning = false;
                tvUnplannedStatus.setText("Off");
                scrollUnplannedCategories.setVisibility(View.VISIBLE);
                layoutUnplannedActive.setVisibility(View.GONE);
                sessionManager.clearUnplannedDowntimeState();
            }
        });
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60)) % 24;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatDowntimeMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "0m";
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private static String formatRuntimeMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "00:00";
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, 0) : String.format("%02d:%02d", minutes, 0);
    }

    private void updateGoodCount() {
        tvGoodCount.setText(String.valueOf(goodCount));
        tvActual.setText(String.valueOf(goodCount));
        animateCountText(tvGoodCount);
    }

    private void animateCountText(TextView textView) {
        textView.animate()
                .scaleX(1.4f).scaleY(1.4f).setDuration(120)
                .withEndAction(() -> textView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                .start();
    }

    private void animateButton(View button) {
        button.animate()
                .scaleX(0.85f).scaleY(0.85f).setDuration(80)
                .withEndAction(() -> button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start())
                .start();
    }

    // ---------- API models ----------
    interface ApiService {
        @POST("api/operator-app/get-downtime-categories-reasons")
        Call<DowntimeCategoryResponse> getDowntimeCategories(@Body DowntimeCategoryRequest request);

        @POST("api/operator-app/get-operator-jobs")
        Call<JobResponse> getJobs(@Body JobRequest request);

        @POST("api/operator-app/job-action")
        Call<JobActionResponse> jobAction(@Body Object request);
    }

    static class DowntimeCategoryRequest {
        @SerializedName("tk") String tk;
        @SerializedName("plant_id") String plantId;
        @SerializedName("company_id") String companyId;
        DowntimeCategoryRequest(String tk, String plantId, String companyId) {
            this.tk = tk; this.plantId = plantId; this.companyId = companyId;
        }
    }

    static class DowntimeCategoryResponse {
        @SerializedName("status") String status;
        @SerializedName("msg") String msg;
        @SerializedName("data") Data data;
    }

    static class Data {
        @SerializedName("planned") List<DowntimeCategory> planned;
        @SerializedName("unplanned") List<DowntimeCategory> unplanned;
    }

    static class DowntimeCategory {
        @SerializedName("id") int id;
        @SerializedName("category_name") String categoryName;
        @SerializedName("category_description") String categoryDescription;
        @SerializedName("reasons") List<DowntimeReason> reasons;
    }

    static class DowntimeReason {
        @SerializedName("id") int id;
        @SerializedName("reasonname") String reasonName;
        @SerializedName("reasondescription") String reasonDescription;
    }

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

    static class JobItem {
        @SerializedName("jobid") String jobId;
        @SerializedName("jobstatus") String jobStatus;
        @SerializedName("current") String current;
        @SerializedName("defectivepeice") Integer defective;
        @SerializedName("actualstarttime") String actualStartTime;
        @SerializedName("actualendtime") String actualEndTime;
        @SerializedName("total_runtime") int totalRuntime;
        @SerializedName("total_planneddowntime") int totalPlannedDowntime;
        @SerializedName("total_unplanneddowntime") int totalUnplannedDowntime;
    }

    static class JobActionRequest {
        @SerializedName("tk") final String tk;
        @SerializedName("jobid") final String jobId;
        @SerializedName("action") final String action;
        @SerializedName("value") final Integer value;
        @SerializedName("device_id") final String deviceId;
        @SerializedName("machine_id") final String machineId;

        JobActionRequest(String tk, String jobId, String action, Integer value, String deviceId, String machineId) {
            this.tk = tk; this.jobId = jobId; this.action = action;
            this.value = value; this.deviceId = deviceId; this.machineId = machineId;
        }
    }

    static class PlannedDowntimeRequest {
        @SerializedName("tk")           final String tk;
        @SerializedName("jobid")        final String jobid;
        @SerializedName("action")       final String action;
        @SerializedName("machineid")    final String machineid;
        @SerializedName("shiftname")    final String shiftname;
        @SerializedName("downtime_type") final String downtimeType;
        @SerializedName("reason_type")  final String reasonType;
        @SerializedName("reason")       final String reason;
        @SerializedName("ontime")       final String ontime;
        @SerializedName("companyid")    final String companyid;

        PlannedDowntimeRequest(String tk, String jobid, String action, String machineid,
                               String shiftname, String downtimeType, String reasonType,
                               String reason, String ontime, String companyid) {
            this.tk = tk; this.jobid = jobid; this.action = action;
            this.machineid = machineid; this.shiftname = shiftname;
            this.downtimeType = downtimeType; this.reasonType = reasonType;
            this.reason = reason; this.ontime = ontime; this.companyid = companyid;
        }
    }

    static class StopPlannedDowntimeRequest {
        @SerializedName("tk")           final String tk;
        @SerializedName("jobid")        final String jobid;
        @SerializedName("action")       final String action;
        @SerializedName("machineid")    final String machineid;
        @SerializedName("shiftname")    final String shiftname;
        @SerializedName("downtime_type") final String downtimeType;
        @SerializedName("reason_type")  final String reasonType;
        @SerializedName("reason")       final String reason;
        @SerializedName("off_time")     final String offTime;

        StopPlannedDowntimeRequest(String tk, String jobid, String action, String machineid,
                                   String shiftname, String downtimeType, String reasonType,
                                   String reason, String offTime) {
            this.tk = tk; this.jobid = jobid; this.action = action;
            this.machineid = machineid; this.shiftname = shiftname;
            this.downtimeType = downtimeType; this.reasonType = reasonType;
            this.reason = reason; this.offTime = offTime;
        }
    }

    // ---------- NEW models for Unplanned Downtime ----------
    static class StartUnplannedDowntimeRequest {
        @SerializedName("tk")           final String tk;
        @SerializedName("jobid")        final String jobid;
        @SerializedName("action")       final String action;
        @SerializedName("machineid")    final String machineid;
        @SerializedName("shiftname")    final String shiftname;
        @SerializedName("downtime_type") final String downtimeType;
        @SerializedName("reason_type")  final String reasonType;
        @SerializedName("reason")       final String reason;
        @SerializedName("ontime")       final String ontime;
        @SerializedName("companyid")    final String companyid;

        StartUnplannedDowntimeRequest(String tk, String jobid, String action, String machineid,
                                      String shiftname, String downtimeType, String reasonType,
                                      String reason, String ontime, String companyid) {
            this.tk = tk; this.jobid = jobid; this.action = action;
            this.machineid = machineid; this.shiftname = shiftname;
            this.downtimeType = downtimeType; this.reasonType = reasonType;
            this.reason = reason; this.ontime = ontime; this.companyid = companyid;
        }
    }

    static class StopUnplannedDowntimeRequest {
        @SerializedName("tk")           final String tk;
        @SerializedName("jobid")        final String jobid;
        @SerializedName("action")       final String action;
        @SerializedName("machineid")    final String machineid;
        @SerializedName("shiftname")    final String shiftname;
        @SerializedName("downtime_type") final String downtimeType;
        @SerializedName("reason_type")  final String reasonType;
        @SerializedName("reason")       final String reason;
        @SerializedName("off_time")     final String offTime;

        StopUnplannedDowntimeRequest(String tk, String jobid, String action, String machineid,
                                     String shiftname, String downtimeType, String reasonType,
                                     String reason, String offTime) {
            this.tk = tk; this.jobid = jobid; this.action = action;
            this.machineid = machineid; this.shiftname = shiftname;
            this.downtimeType = downtimeType; this.reasonType = reasonType;
            this.reason = reason; this.offTime = offTime;
        }
    }

    static class JobActionResponse {
        @SerializedName("status") String status;
        @SerializedName("msg") String msg;
        @SerializedName("job") JobUpdate job;
        @SerializedName("downtime_log") DowntimeLog downtimeLog;
    }

    static class JobUpdate {
        @SerializedName("jobid") String jobId;
        @SerializedName("jobstatus") String jobStatus;
        @SerializedName("current") String current;
        @SerializedName("defective") String defective;
    }

    static class DowntimeLog {
        @SerializedName("id") int id;
        @SerializedName("machineid") String machineId;
        @SerializedName("shiftname") String shiftName;
        @SerializedName("jobid") String jobId;
        @SerializedName("downtime_type") String downtimeType;
        @SerializedName("reason_type") String reasonType;
        @SerializedName("reason") String reason;
        @SerializedName("on_time") String onTime;
        @SerializedName("off_time") String offTime;
        @SerializedName("duration_mins") String durationMins;
        @SerializedName("companyid") String companyId;
        @SerializedName("timestamp") String timestamp;
    }
}