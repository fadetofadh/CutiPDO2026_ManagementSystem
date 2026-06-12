package com.test.cutipdo2026;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GoogleSheetsApi {

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
    Call<List<LeaveRequestData>> getPendingRequests(@Query("type") String type);

    // Pulls approved requests for the KADIV Cancel Portal
    @GET("exec")
    Call<List<LeaveRequestData>> getAllRequests(@Query("type") String type);

    // Sends your leave request batches out to the Apps Script backend
    @POST("exec")
    Call<ResponseBody> sendRequest(@Body LeaveRequest request);
}