package com.test.cutipdo2026;

import android.content.Context;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS) // Increased to 60s for slow networks
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)      // 🛡️ Automatically retry if SSL handshakes fail
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectionPool(new okhttp3.ConnectionPool(0, 5, TimeUnit.MINUTES)) // 💡 Force fresh connections
                    .build();

            String baseUrl = context.getString(R.string.google_sheets_url);

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static GoogleSheetsApi getApi(Context context) {
        return getClient(context).create(GoogleSheetsApi.class);
    }
}
