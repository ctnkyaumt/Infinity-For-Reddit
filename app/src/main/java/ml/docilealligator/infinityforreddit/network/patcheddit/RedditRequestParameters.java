/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Ported from Patcheddit v2.5.0 for Infinity for Reddit (GPLv3).
 */

package ml.docilealligator.infinityforreddit.network.patcheddit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * Extracts query and form parameters and normalizes Reddit API hosts and paths.
 */
public final class RedditRequestParameters {
    private RedditRequestParameters() {}

    @NonNull
    public static Map<String, String> read(@NonNull Request request) throws IOException {
        Map<String, String> parameters = new HashMap<>();
        for (String name : request.url().queryParameterNames()) {
            String value = request.url().queryParameter(name);
            if (value != null) {
                parameters.put(name, value);
            }
        }

        RequestBody body = request.body();
        if (body instanceof FormBody) {
            FormBody form = (FormBody) body;
            for (int i = 0; i < form.size(); i++) {
                parameters.put(form.name(i), form.value(i));
            }
        } else if (body != null && !body.isOneShot()) {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            String form = new String(buffer.readByteArray(), StandardCharsets.UTF_8);
            try {
                for (String field : form.split("&")) {
                    if (field.isEmpty()) continue;
                    int separator = field.indexOf('=');
                    String name = separator < 0 ? field : field.substring(0, separator);
                    String value = separator < 0 ? "" : field.substring(separator + 1);
                    parameters.put(URLDecoder.decode(name, "UTF-8"),
                            URLDecoder.decode(value, "UTF-8"));
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid URL-encoded Reddit API form", exception);
            }
        }
        return parameters;
    }

    public static boolean isRedditHost(@Nullable String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("reddit.com") || normalized.endsWith(".reddit.com");
    }

    @NonNull
    public static String normalizePath(@Nullable String path) {
        if (path == null) return "";
        String normalized = path.toLowerCase(Locale.ROOT);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/.json")) {
            normalized = normalized.substring(0, normalized.length() - "/.json".length());
        } else if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("/r/")) {
            int end = normalized.indexOf('/', 3);
            if (end > 3) {
                String route = normalized.substring(end);
                if (route.startsWith("/api/") || route.startsWith("/by_id/")) {
                    normalized = route;
                }
            }
        }
        return normalized;
    }
}