package com.test.cutipdo2026;

import android.content.Context;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
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
