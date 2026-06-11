package com.test.cutipdo2026;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface CallMeBotApi {
    // CallMeBot triggers text alerts using a standard URL GET request layout
    @GET("whatsapp.php")
    Call<ResponseBody> sendWhatsAppMessage(
            @Query("phone") String phoneNumber, // Your phone number in international format (e.g., +62...)
            @Query("text") String messageText,  // The text message content
            @Query("apikey") String apiKey      // Your unique CallMeBot API key
    );
}