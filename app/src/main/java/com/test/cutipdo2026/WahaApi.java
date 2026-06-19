package com.test.cutipdo2026;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WahaApi {
    /**
     * WAHA (WhatsApp HTTP API) uses a POST request with a JSON body to send messages.
     * Default port is usually 3000.
     */
    @POST("api/sendText")
    Call<ResponseBody> sendMessage(@Body WahaMessageRequest request);
}