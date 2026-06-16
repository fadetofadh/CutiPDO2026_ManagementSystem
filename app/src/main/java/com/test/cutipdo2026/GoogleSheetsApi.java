package com.test.cutipdo2026;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GoogleSheetsApi {

    // 🔄 NEW: Connects to the update engine block in your Apps Script via a GET request
    @GET("exec")
    Call<UpdateResponse> checkAppUpdate(
            @Query("type") String type,          // This will pass "checkUpdate"
            @Query("cb") String cacheBuster      // Guarantees live sheet tracking bypasses local cache
    );

    // Pulls the list of employees for the KADIV spinner dropdown
    @GET("exec")
    Call<List<String>> getEmployees(
            @Query("type") String type,
            @Query("filterClass") String filterClass
    );

    // Pulls employee balances
    @GET("exec")
    Call<List<EmployeeBalance>> getBalances(
            @Query("type") String type,
            @Query("filterClass") String filterClass
    );

    // 🌟 THE FIX: This adds the missing getPendingRequests method that SupervisorActivity is looking for!
    @GET("exec")
    Call<List<LeaveRequestData>> getPendingRequests(
            @Query("type") String type,
            @Query("filterClass") String filterClass
    );

    // Pulls approved requests for the KADIV Cancel Portal
    @GET("exec")
    Call<List<LeaveRequestData>> getAllRequests(
            @Query("type") String type,
            @Query("filterClass") String filterClass,
            @Query("cb") String cacheBuster
    );

    // Sends your leave request batches out to the Apps Script backend
    @POST("exec")
    Call<ResponseBody> sendRequest(@Body LeaveRequest request);
}