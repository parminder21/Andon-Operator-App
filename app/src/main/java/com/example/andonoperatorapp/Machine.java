package com.example.andonoperatorapp;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a machine from the dashboard-get-machine API.
 */
public class Machine {
    @SerializedName("id")
    int id;
    @SerializedName("machuid")
    String machUid;
    @SerializedName("machname")
    String machName;
    @SerializedName("machstatus")
    String machStatus;
    @SerializedName("machphoto")
    String machPhoto;
    @SerializedName("health_score")
    String healthScore;
    @SerializedName("criticality_level")
    String criticalityLevel;
    @SerializedName("floor_name")
    String floorName;
    @SerializedName("machassembly")
    String machAssembly;
    @SerializedName("machproduction")
    String machProduction;
    @SerializedName("company_id")
    String companyId;
    @SerializedName("plant_id")
    String plantId;
    @SerializedName("brand")
    String brand;
    @SerializedName("machcode")
    String machCode;
    @SerializedName("machdescription")
    String machDescription;
    @SerializedName("machine_serial_number")
    String machineSerialNumber;
    @SerializedName("manufacturer")
    String manufacturer;
    @SerializedName("machpreviousservicedate")
    String machPreviousServiceDate;
    @SerializedName("machnextservicedate")
    String machNextServiceDate;
    @SerializedName("purchase_cost")
    String purchaseCost;
    @SerializedName("running_hours")
    String runningHours;
    @SerializedName("machstate")
    String machState;
    @SerializedName("created_at")
    String createdAt;
    @SerializedName("jobidis")
    String jobIdis;  // empty often

    // ---------- NEW FIELDS ----------
    @SerializedName("machine_model")
    String machineModel;
    @SerializedName("purchase_date")
    String purchaseDate;
    @SerializedName("installation_date")
    String installationDate;
    @SerializedName("warranty_expiry")
    String warrantyExpiry;

    // ---------- GETTERS ----------
    public int getId() { return id; }
    public String getMachName() { return machName; }
    public String getMachStatus() { return machStatus; }
    public String getFloorName() { return floorName; }
    public String getHealthScore() { return healthScore; }
    public String getCriticalityLevel() { return criticalityLevel; }
    public String getMachUid() { return machUid; }
    public String getBrand() { return brand; }
    public String getMachCode() { return machCode; }
    public String getMachDescription() { return machDescription; }
    public String getMachineSerialNumber() { return machineSerialNumber; }
    public String getManufacturer() { return manufacturer; }
    public String getMachPreviousServiceDate() { return machPreviousServiceDate; }
    public String getMachNextServiceDate() { return machNextServiceDate; }
    public String getPurchaseCost() { return purchaseCost; }
    public String getRunningHours() { return runningHours; }
    public String getMachState() { return machState; }
    public String getCreatedAt() { return createdAt; }
    public String getJobIdis() { return jobIdis; }
    public String getMachAssembly() { return machAssembly; }
    public String getMachProduction() { return machProduction; }
    public String getCompanyId() { return companyId; }
    public String getPlantId() { return plantId; }

    // NEW GETTERS
    public String getMachineModel() { return machineModel; }
    public String getPurchaseDate() { return purchaseDate; }
    public String getInstallationDate() { return installationDate; }
    public String getWarrantyExpiry() { return warrantyExpiry; }

    /**
     * Returns a drawable resource for the status indicator colour.
     */
    public int getStatusIndicatorDrawable() {
        if (machStatus == null) return R.drawable.circle_status_green;
        switch (machStatus.toLowerCase()) {
            case "active":
            case "running":
                return R.drawable.circle_status_green;
            case "idle":
                return R.drawable.circle_status_yellow;
            case "fault":
            case "offline":
                return R.drawable.circle_status_red;
            default:
                return R.drawable.circle_status_green;
        }
    }
}