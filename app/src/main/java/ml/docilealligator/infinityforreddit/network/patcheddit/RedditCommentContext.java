/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Ported from Patcheddit v2.5.0 for Infinity for Reddit (GPLv3).
 */

package ml.docilealligator.infinityforreddit.network.patcheddit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded LRU cache mapping comment fullnames ({@code t1_<id>}) to parent post IDs
 * so {@code /api/info} and {@code /by_id} fallbacks can resolve comment lookups.
 */
final class RedditCommentContext {
    private static final Pattern POST_PATH = Pattern.compile(
            "(?:^|/)comments/([a-z0-9]+)(?:\\.json)?(?:/|$)", Pattern.CASE_INSENSITIVE);
    private final int capacity;
    private final LinkedHashMap<String, String> postIds = new LinkedHashMap<>(16, 0.75f, true);

    RedditCommentContext(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Invalid Reddit context capacity");
        this.capacity = capacity;
    }

    synchronized String postFor(String commentName) {
        return postIds.get(commentName.toLowerCase(Locale.ROOT));
    }

    private synchronized void remember(String commentName, String postId) {
        // Share only immutable ID relationships, never account-specific response data.
        postIds.put(commentName, postId);
        if (postIds.size() > capacity) {
            Iterator<String> oldest = postIds.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    void index(Object value, String fallbackPostId) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) index(array.opt(i), fallbackPostId);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject data = object.optJSONObject("data");
            String childPostId = fallbackPostId;
            if ("t1".equals(object.optString("kind")) && data != null) {
                String id = idFromFullname(data.optString("name"), "t1_");
                if (id == null) id = validId(data.optString("id"));
                String postId = idFromFullname(data.optString("link_id"), "t3_");
                if (postId == null) postId = idFromFullname(data.optString("parent_id"), "t3_");
                if (postId == null) postId = postFromLink(data.optString("context"));
                if (postId == null) postId = postFromLink(data.optString("permalink"));
                if (postId == null) postId = validId(fallbackPostId);
                if (id != null && postId != null) {
                    remember("t1_" + id, postId);
                    childPostId = postId;
                }
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) index(object.opt(keys.next()), childPostId);
        }
    }

    static String idFromFullname(String fullname, String prefix) {
        if (fullname == null) return null;
        String normalized = fullname.toLowerCase(Locale.ROOT);
        return normalized.startsWith(prefix) ? validId(normalized.substring(prefix.length())) : null;
    }

    static String validId(String id) {
        return id != null && id.matches("[A-Za-z0-9]{1,64}")
                ? id.toLowerCase(Locale.ROOT) : null;
    }

    static String postFromPath(String path) {
        if (path == null) return null;
        Matcher match = POST_PATH.matcher(path);
        return match.find() ? validId(match.group(1)) : null;
    }

    static String postFromLink(String link) {
        if (link == null || link.isEmpty()) return null;
        try {
            URI uri = new URI(link);
            if ((uri.isAbsolute() || uri.getHost() != null)
                    && !RedditRequestParameters.isRedditHost(uri.getHost())) {
                return null;
            }
            return postFromPath(uri.getPath());
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}