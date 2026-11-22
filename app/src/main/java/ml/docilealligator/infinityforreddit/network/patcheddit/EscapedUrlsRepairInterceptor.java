/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Ported from Patcheddit v2.5.0 for Infinity for Reddit (GPLv3).
 */

package ml.docilealligator.infinityforreddit.network.patcheddit;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp interceptor that repairs escaped underscores, asterisks, and tildes in URLs
 * inside successful Reddit API JSON responses.
 */
public final class EscapedUrlsRepairInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (!RedditRequestParameters.isRedditHost(request.url().host()) || !response.isSuccessful()) {
            return response;
        }

        ResponseBody body = response.body();
        if (body == null) {
            return response;
        }
        MediaType contentType = body.contentType();
        if (contentType == null || !contentType.toString().toLowerCase(Locale.ROOT).contains("json")) {
            return response;
        }
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
            return response;
        }
        if (body.contentLength() > EscapedUrls.MAX_BODY_BYTES) {
            return response;
        }

        final String original;
        try (ResponseBody peek = response.peekBody(EscapedUrls.MAX_BODY_BYTES + 1)) {
            if (peek.contentLength() > EscapedUrls.MAX_BODY_BYTES) {
                return response;
            }
            original = peek.string();
        }

        String repaired = EscapedUrls.repairRedditResponse(original);
        if (repaired.equals(original)) {
            return response;
        }

        body.close();
        return response.newBuilder()
                .removeHeader("Content-Length")
                .body(ResponseBody.create(repaired, contentType))
                .build();
    }
}