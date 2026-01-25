package com.quantor.exchange;

import okhttp3.*;
import java.io.IOException;

/**
 * Simple wrapper around OkHttp for GET/POST requests.
 */
public class HttpClient {

    private final OkHttpClient client = new OkHttpClient();

    public String get(String url, Headers headers) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " => " +
                        (resp.body() != null ? resp.body().string() : ""));
            }
            return resp.body() != null ? resp.body().string() : "";
        }
    }

    public String post(String url, RequestBody body, Headers headers) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .headers(headers)
                .post(body)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " => " +
                        (resp.body() != null ? resp.body().string() : ""));
            }
            return resp.body() != null ? resp.body().string() : "";
        }
    }
}