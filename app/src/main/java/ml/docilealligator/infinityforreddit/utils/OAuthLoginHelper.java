package ml.docilealligator.infinityforreddit.utils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class OAuthLoginHelper {
    private static final String BUILTIN_REDREADER_REDIRECT_URI = "redreader://rr_oauth_redir";
    private static final String BUILTIN_INFINITY_REDIRECT_URI = "infinity://localhost";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private OAuthLoginHelper() {
    }

    public static String generateOAuthState() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        char[] hexChars = new char[randomBytes.length * 2];
        for (int i = 0; i < randomBytes.length; i++) {
            int v = randomBytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean isMatchingOAuthState(String expected, String actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isOAuthCallbackUrl(String url, String configuredRedirectUri) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        URI actualUri = parseBaseUri(url);
        if (actualUri == null || actualUri.isOpaque() || actualUri.getScheme() == null) {
            return false;
        }

        return matchesRedirectUri(actualUri, configuredRedirectUri)
                || matchesRedirectUri(actualUri, BUILTIN_REDREADER_REDIRECT_URI)
                || matchesRedirectUri(actualUri, BUILTIN_INFINITY_REDIRECT_URI);
    }

    private static boolean matchesRedirectUri(URI actualUri, String redirectUriStr) {
        if (redirectUriStr == null || redirectUriStr.trim().isEmpty()) {
            return false;
        }
        URI candidateUri = parseBaseUri(redirectUriStr.trim());
        if (candidateUri == null || candidateUri.isOpaque() || candidateUri.getScheme() == null) {
            return false;
        }

        if (!candidateUri.getScheme().equalsIgnoreCase(actualUri.getScheme())) {
            return false;
        }

        if (!authoritiesMatch(candidateUri, actualUri)) {
            return false;
        }

        String candPath = candidateUri.getRawPath();
        String actualPath = actualUri.getRawPath();
        if (candPath == null || candPath.isEmpty() || "/".equals(candPath)) {
            return actualPath == null || actualPath.isEmpty() || actualPath.startsWith("/");
        }
        if (actualPath == null) {
            return false;
        }
        if (candPath.endsWith("/")) {
            return actualPath.startsWith(candPath);
        }
        return actualPath.equals(candPath) || actualPath.startsWith(candPath + "/");
    }

    private static boolean authoritiesMatch(URI candidateUri, URI actualUri) {
        String candHost = candidateUri.getHost();
        String actualHost = actualUri.getHost();
        if (candHost != null && actualHost != null) {
            String candUserInfo = candidateUri.getRawUserInfo();
            String actualUserInfo = actualUri.getRawUserInfo();
            boolean userInfoMatches = candUserInfo == null
                    ? actualUserInfo == null
                    : candUserInfo.equals(actualUserInfo);
            return userInfoMatches
                    && candHost.equalsIgnoreCase(actualHost)
                    && candidateUri.getPort() == actualUri.getPort();
        }
        String candAuth = candidateUri.getRawAuthority();
        String actualAuth = actualUri.getRawAuthority();
        if (candAuth == null || actualAuth == null) {
            return candAuth == null && actualAuth == null;
        }
        return candAuth.equalsIgnoreCase(actualAuth);
    }

    private static URI parseBaseUri(String rawUrl) {
        String stripped = stripQueryAndFragment(rawUrl);
        try {
            return new URI(stripped);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String stripQueryAndFragment(String url) {
        int qIdx = url.indexOf('?');
        int hashIdx = url.indexOf('#');
        int cutIdx = -1;
        if (qIdx >= 0 && hashIdx >= 0) {
            cutIdx = Math.min(qIdx, hashIdx);
        } else if (qIdx >= 0) {
            cutIdx = qIdx;
        } else if (hashIdx >= 0) {
            cutIdx = hashIdx;
        }
        return cutIdx >= 0 ? url.substring(0, cutIdx) : url;
    }

    public static String getQueryParam(String url, String paramName) {
        if (url == null || paramName == null || paramName.isEmpty()) {
            return null;
        }
        int hashIdx = url.indexOf('#');
        String withoutFragment = hashIdx >= 0 ? url.substring(0, hashIdx) : url;
        int qIdx = withoutFragment.indexOf('?');
        if (qIdx < 0 || qIdx == withoutFragment.length() - 1) {
            return null;
        }
        String query = withoutFragment.substring(qIdx + 1);
        String[] pairs = query.split("&", -1);
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eqIdx = pair.indexOf('=');
            String rawKey = eqIdx >= 0 ? pair.substring(0, eqIdx) : pair;
            String decodedKey = decodeUtf8(rawKey);
            if (paramName.equals(decodedKey)) {
                String rawValue = eqIdx >= 0 ? pair.substring(eqIdx + 1) : "";
                return decodeUtf8(rawValue);
            }
        }
        return null;
    }

    private static String decodeUtf8(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }
}