package com.example.andonoperatorapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Manages operator session locally using SharedPreferences.
 * Stores login state, token, employee data, and the full login response.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "SmartAndonPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EMPLOYEE = "employee_json";
    private static final String KEY_LOGIN_RESPONSE = "login_response_json";

    private static final String KEY_SELECTED_MACHINE = "selected_machine_json";

    private static final String KEY_GOOD_COUNT = "good_count";
    private static final String KEY_DEFECTIVE_COUNT = "defective_count";

    // Downtime persistence keys
    private static final String KEY_PLANNED_DOWNTIME_RUNNING = "planned_downtime_running";
    private static final String KEY_PLANNED_DOWNTIME_START = "planned_downtime_start";
    private static final String KEY_PLANNED_DOWNTIME_CATEGORY = "planned_downtime_category";
    private static final String KEY_PLANNED_DOWNTIME_SUBCATEGORY = "planned_downtime_subcategory";
    private static final String KEY_UNPLANNED_DOWNTIME_RUNNING = "unplanned_downtime_running";
    private static final String KEY_UNPLANNED_DOWNTIME_START = "unplanned_downtime_start";
    private static final String KEY_UNPLANNED_DOWNTIME_CATEGORY = "unplanned_downtime_category";
    private static final String KEY_UNPLANNED_DOWNTIME_SUBCATEGORY = "unplanned_downtime_subcategory";

    private final SharedPreferences prefs;
    private final Gson gson;

    public SessionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Persists only the Employee object (kept for backward compatibility).
     */
    public void saveEmployeeSession(LoginActivity.Employee employee) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.putString(KEY_EMPLOYEE, gson.toJson(employee));
            editor.apply();
            Log.d(TAG, "Employee session saved.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save employee session: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the stored Employee object.
     */
    public LoginActivity.Employee getEmployee() {
        String json = prefs.getString(KEY_EMPLOYEE, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, LoginActivity.Employee.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse employee JSON: " + e.getMessage(), e);
            clearSession();
            return null;
        }
    }

    /**
     * Saves the entire login response (employee, company, designation,
     * department, skills, shifts) and the token.
     */
    public void saveLoginResponse(LoginActivity.LoginResponse response) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.putString(KEY_TOKEN, response.token);
            editor.putString(KEY_LOGIN_RESPONSE, gson.toJson(response));
            editor.apply();
            Log.d(TAG, "Full login response saved.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save login response: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the full login response.
     */
    public LoginActivity.LoginResponse getLoginResponse() {
        String json = prefs.getString(KEY_LOGIN_RESPONSE, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, LoginActivity.LoginResponse.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse login response: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clears all session data (logout).
     */
    public void clearSession() {
        try {
            prefs.edit().clear().apply();
            Log.d(TAG, "Session cleared.");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing session: " + e.getMessage(), e);
        }
    }

    /**
     * Saves the selected machine.
     */
    public void saveSelectedMachine(Machine machine) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_SELECTED_MACHINE, gson.toJson(machine));
            editor.apply();
            Log.d(TAG, "Selected machine saved.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save selected machine: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the saved selected machine, or null if none.
     */
    public Machine getSelectedMachine() {
        String json = prefs.getString(KEY_SELECTED_MACHINE, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, Machine.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse selected machine: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clears only the selected machine (optional, we clear whole session on logout).
     */
    public void clearSelectedMachine() {
        prefs.edit().remove(KEY_SELECTED_MACHINE).apply();
    }

    // ========== Good / Defective Count per Job ==========
    public void saveGoodCount(String jobId, int count) {
        prefs.edit().putInt(KEY_GOOD_COUNT + "_" + jobId, count).apply();
    }

    public int getGoodCount(String jobId) {
        return prefs.getInt(KEY_GOOD_COUNT + "_" + jobId, 0);
    }

    public void saveDefectiveCount(String jobId, int count) {
        prefs.edit().putInt(KEY_DEFECTIVE_COUNT + "_" + jobId, count).apply();
    }

    public int getDefectiveCount(String jobId) {
        return prefs.getInt(KEY_DEFECTIVE_COUNT + "_" + jobId, 0);
    }

    // ========== Planned Downtime State ==========
    public void savePlannedDowntimeRunning(boolean running) {
        prefs.edit().putBoolean(KEY_PLANNED_DOWNTIME_RUNNING, running).apply();
    }

    public boolean isPlannedDowntimeRunning() {
        return prefs.getBoolean(KEY_PLANNED_DOWNTIME_RUNNING, false);
    }

    public void savePlannedDowntimeStartTime(long startTime) {
        prefs.edit().putLong(KEY_PLANNED_DOWNTIME_START, startTime).apply();
    }

    public long getPlannedDowntimeStartTime() {
        return prefs.getLong(KEY_PLANNED_DOWNTIME_START, System.currentTimeMillis());
    }

    public void savePlannedDowntimeCategory(String category) {
        prefs.edit().putString(KEY_PLANNED_DOWNTIME_CATEGORY, category).apply();
    }

    public String getPlannedDowntimeCategory() {
        return prefs.getString(KEY_PLANNED_DOWNTIME_CATEGORY, "");
    }

    public void savePlannedDowntimeSubcategory(String subcategory) {
        prefs.edit().putString(KEY_PLANNED_DOWNTIME_SUBCATEGORY, subcategory).apply();
    }

    public String getPlannedDowntimeSubcategory() {
        return prefs.getString(KEY_PLANNED_DOWNTIME_SUBCATEGORY, "");
    }

    public void clearPlannedDowntimeState() {
        prefs.edit()
                .remove(KEY_PLANNED_DOWNTIME_RUNNING)
                .remove(KEY_PLANNED_DOWNTIME_START)
                .remove(KEY_PLANNED_DOWNTIME_CATEGORY)
                .remove(KEY_PLANNED_DOWNTIME_SUBCATEGORY)
                .apply();
    }

    // ========== Unplanned Downtime State ==========
    public void saveUnplannedDowntimeRunning(boolean running) {
        prefs.edit().putBoolean(KEY_UNPLANNED_DOWNTIME_RUNNING, running).apply();
    }

    public boolean isUnplannedDowntimeRunning() {
        return prefs.getBoolean(KEY_UNPLANNED_DOWNTIME_RUNNING, false);
    }

    public void saveUnplannedDowntimeStartTime(long startTime) {
        prefs.edit().putLong(KEY_UNPLANNED_DOWNTIME_START, startTime).apply();
    }

    public long getUnplannedDowntimeStartTime() {
        return prefs.getLong(KEY_UNPLANNED_DOWNTIME_START, System.currentTimeMillis());
    }

    public void saveUnplannedDowntimeCategory(String category) {
        prefs.edit().putString(KEY_UNPLANNED_DOWNTIME_CATEGORY, category).apply();
    }

    public String getUnplannedDowntimeCategory() {
        return prefs.getString(KEY_UNPLANNED_DOWNTIME_CATEGORY, "");
    }

    public void saveUnplannedDowntimeSubcategory(String subcategory) {
        prefs.edit().putString(KEY_UNPLANNED_DOWNTIME_SUBCATEGORY, subcategory).apply();
    }

    public String getUnplannedDowntimeSubcategory() {
        return prefs.getString(KEY_UNPLANNED_DOWNTIME_SUBCATEGORY, "");
    }

    public void clearUnplannedDowntimeState() {
        prefs.edit()
                .remove(KEY_UNPLANNED_DOWNTIME_RUNNING)
                .remove(KEY_UNPLANNED_DOWNTIME_START)
                .remove(KEY_UNPLANNED_DOWNTIME_CATEGORY)
                .remove(KEY_UNPLANNED_DOWNTIME_SUBCATEGORY)
                .apply();
    }
}