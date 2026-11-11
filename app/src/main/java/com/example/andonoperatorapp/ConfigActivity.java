package com.example.andonoperatorapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
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
 * Allows operator to select a machine before proceeding to the dashboard.
 * Saves selection in SharedPreferences. Includes top bar with logout.
 */
public class ConfigActivity extends AppCompatActivity {

    private static final String TAG = "ConfigActivity";
    private static final String BASE_URL = "https://smartandon.robatosystems.com/";
    private static final String STATIC_TOKEN = "b3BlcmF0b3JBcHBTdGF0aWNUb2tlbjEyMw==";

    private RecyclerView rvMachines;
    private ProgressBar progressBar;
    private ImageView ivLogout;
    private SessionManager sessionManager;
    private String plantId;
    private List<Machine> machineList = new ArrayList<>();
    private MachineAdapter adapter;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_config);

            sessionManager = new SessionManager(this);

            // Read employee from full login response
            LoginActivity.LoginResponse loginResponse = sessionManager.getLoginResponse();
            LoginActivity.Employee employee = (loginResponse != null) ? loginResponse.employee : null;

            if (employee == null) {
                Toast.makeText(this, "Session not found, redirecting to Home", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, HomeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
                return;
            }
            plantId = employee.getPlantIdString();

            // Bind views
            rvMachines = findViewById(R.id.rvMachines);
            progressBar = findViewById(R.id.progressBar);
            ivLogout = findViewById(R.id.ivLogout);

            // Setup RecyclerView
            rvMachines.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            adapter = new MachineAdapter(machineList, this::onMachineSelected);
            rvMachines.setAdapter(adapter);

            // Logout click
            ivLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

            // Load machines
            apiService = buildApiService();
            loadMachines();

        } catch (Exception e) {
            Log.e(TAG, "Crash in ConfigActivity.onCreate", e);
            Toast.makeText(this, "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage(), Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, HomeActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }
    }

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

    private void loadMachines() {
        progressBar.setVisibility(View.VISIBLE);
        MachineRequest request = new MachineRequest(STATIC_TOKEN, plantId);
        apiService.getMachines(request).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    machineList.clear();
                    if (response.body().machines != null) {
                        machineList.addAll(response.body().machines);
                    }
                    adapter.notifyDataSetChanged();
                } else {
                    String error = "Failed to load machines";
                    try {
                        if (response.errorBody() != null) error += ": " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    showError(error);
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void onMachineSelected(Machine machine) {
        sessionManager.saveSelectedMachine(machine);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmationDialog() {
        // Inflate custom dialog layout
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
            performLogout();
        });

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

    // ---------- API & Models ----------

    interface ApiService {
        @POST("api/operator-app/dashboard-get-machine")
        Call<DashboardResponse> getMachines(@Body MachineRequest request);
    }

    static class MachineRequest {
        @SerializedName("tk") final String tk;
        @SerializedName("plant_id") final String plantId;
        MachineRequest(String tk, String plantId) {
            this.tk = tk;
            this.plantId = plantId;
        }
    }

    static class DashboardResponse {
        @SerializedName("status") String status;
        @SerializedName("msg") String msg;
        @SerializedName("plant_name") String plantName;
        @SerializedName("machines") List<Machine> machines;
    }

    // ---------- Adapter ----------

    static class MachineAdapter extends RecyclerView.Adapter<MachineAdapter.VH> {
        private List<Machine> machines;
        private OnMachineClickListener listener;
        interface OnMachineClickListener { void onMachineClick(Machine machine); }

        MachineAdapter(List<Machine> machines, OnMachineClickListener listener) {
            this.machines = machines;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_machine_card, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Machine m = machines.get(position);
            holder.tvName.setText(m.machName);
            holder.tvStatus.setText(m.machStatus);
            holder.tvFloor.setText("Floor: " + (m.floorName != null ? m.floorName : "N/A"));
            holder.tvHealth.setText("Health: " + (m.healthScore != null ? m.healthScore + "%" : "N/A"));
            holder.indicator.setBackgroundResource(m.getStatusIndicatorDrawable());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMachineClick(m);
            });
        }

        @Override public int getItemCount() { return machines.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus, tvFloor, tvHealth;
            View indicator;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvMachineName);
                tvStatus = itemView.findViewById(R.id.tvMachineStatus);
                tvFloor = itemView.findViewById(R.id.tvCurrentJob);
                tvHealth = itemView.findViewById(R.id.tvOee);
                indicator = itemView.findViewById(R.id.statusIndicator);
            }
        }
    }
}