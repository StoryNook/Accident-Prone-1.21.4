package com.storynook.nanny.membership;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared OAuth utilities: CSRF state generation, HTTP POST/GET, naive JSON value extraction.
 * One instance per JVM — reused by Patreon and Subscribestar providers.
 */
public class OAuthHelper {

    private final SecureRandom random = new SecureRandom();
    /** Maps state token -> player UUID. Entries expire after 15 minutes. */
    private final Map<String, PendingState> pending = new ConcurrentHashMap<String, PendingState>();

    private static final long STATE_TTL_MS = 15L * 60 * 1000;

    /** Generates a CSRF state token bound to the player UUID. */
    public String generateState(UUID playerUUID) {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        pending.put(state, new PendingState(playerUUID, System.currentTimeMillis()));
        sweepExpired();
        return state;
    }

    /** @return the UUID this state belongs to, or null if unknown/expired. Consumes the state on success. */
    public UUID consumeState(String state) {
        sweepExpired();
        PendingState s = pending.remove(state);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.createdMs > STATE_TTL_MS) return null;
        return s.playerUUID;
    }

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Map.Entry<String, PendingState>> it = pending.entrySet().iterator(); it.hasNext();) {
            if (now - it.next().getValue().createdMs > STATE_TTL_MS) it.remove();
        }
    }

    /** form-encoded POST. Returns response body. Throws on non-2xx. */
    public String postForm(String url, Map<String, String> form) throws IOException {
        javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) new java.net.URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Accept", "application/json");
            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> e : form.entrySet()) {
                if (body.length() > 0) body.append('&');
                body.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
            try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + resp);
            return resp;
        } finally {
            c.disconnect();
        }
    }

    /** GET with bearer auth. */
    public String getWithBearer(String url, String accessToken) throws IOException {
        javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) new java.net.URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Authorization", "Bearer " + accessToken);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + resp);
            return resp;
        } finally {
            c.disconnect();
        }
    }

    /** POST a JSON body with bearer auth (used by SS GraphQL). */
    public String postJsonWithBearer(String url, String json, String accessToken) throws IOException {
        javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) new java.net.URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            if (accessToken != null && !accessToken.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + resp);
            return resp;
        } finally {
            c.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Naive JSON-string-value extractor for a flat key.
     * Use only for shallow OAuth token responses where a real JSON parser would be overkill.
     * Returns null if not found.
     */
    public String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (j >= json.length() || json.charAt(j) != '"') return null;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (j = j + 1; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (escape) { sb.append(ch); escape = false; continue; }
            if (ch == '\\') { escape = true; continue; }
            if (ch == '"') return sb.toString();
            sb.append(ch);
        }
        return null;
    }

    public Integer extractJsonInt(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        StringBuilder sb = new StringBuilder();
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) {
            sb.append(json.charAt(j));
            j++;
        }
        if (sb.length() == 0) return null;
        try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static final class PendingState {
        final UUID playerUUID;
        final long createdMs;
        PendingState(UUID p, long c) { this.playerUUID = p; this.createdMs = c; }
    }
}
