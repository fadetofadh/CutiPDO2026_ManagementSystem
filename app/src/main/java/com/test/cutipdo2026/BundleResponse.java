package com.test.cutipdo2026;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class BundleResponse {
    @SerializedName("balances")
    private ArrayList<EmployeeBalance> balances;

    @SerializedName("names")
    private ArrayList<String> names;

    @SerializedName("approved")
    private ArrayList<LeaveRequestData> approved;

    public ArrayList<EmployeeBalance> getBalances() { return balances; }
    public ArrayList<String> getNames() { return names; }
    public ArrayList<LeaveRequestData> getApproved() { return approved; }
}