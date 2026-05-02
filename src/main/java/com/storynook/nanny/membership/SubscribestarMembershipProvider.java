package com.storynook.nanny.membership;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.SavePlayerStats;
import com.storynook.nanny.MembershipProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subscribestar OAuth code-paste provider.
 * Authorize: https://www.subscribestar.com/oauth2/authorize
 * Token:     https://www.subscribestar.com/oauth2/token
 * GraphQL:   https://subscribestar.adult/api/graphql/v1
 *
 * Per spec: SS may not always issue refresh tokens. If we only have a short-lived bearer
 * and it expires, we set status=LAPSED and surface a chat prompt on next login.
 */
public class SubscribestarMembershipProvider implements MembershipProvider {

    public static final String PROVIDER_NAME = "SUBSCRIBESTAR";
    private static final String TOKEN_URL = "https://www.subscribestar.com/oauth2/token";
    private static final String AUTHORIZE_URL = "https://www.subscribestar.com/oauth2/authorize";
    private static final String GRAPHQL_URL = "https://subscribestar.adult/api/graphql/v1";
    private static final String SCOPES = "subscriber.read";
    private static final String VIEWER_QUERY =
            "{\"query\":\"{ viewer { email subscription { state tier { title } } } }\"}";

    private final Plugin plugin;
    private final OAuthHelper http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final List<String> requiredTiers;

    public SubscribestarMembershipProvider(Plugin plugin, OAuthHelper http,
                                           String clientId, String clientSecret, String redirectUri,
                                           List<String> requiredTiers) {
        this.plugin = plugin;
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.requiredTiers = requiredTiers == null ? Collections.<String>emptyList() : requiredTiers;
    }

    public String buildAuthorizeUrl(UUID playerUUID) {
        String state = http.generateState(playerUUID);
        try {
            return AUTHORIZE_URL
                    + "?response_type=code"
                    + "&client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8")
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8")
                    + "&scope=" + java.net.URLEncoder.encode(SCOPES, "UTF-8")
                    + "&state=" + state;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public boolean linkFromCode(UUID expectedPlayer, String code, String state) throws IOException {
        UUID stateOwner = http.consumeState(state);
        if (stateOwner == null || !stateOwner.equals(expectedPlayer)) {
            throw new IOException("Link code does not match the player. Run /nanny link subscribestar again.");
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirectUri);
        String resp = http.postForm(TOKEN_URL, form);
        String access = http.extractJsonString(resp, "access_token");
        String refresh = http.extractJsonString(resp, "refresh_token");
        if (access == null) throw new IOException("Subscribestar did not return an access token.");
        TierStatus ts = queryTier(access);
        savePlayerState(expectedPlayer, refresh == null ? "" : refresh, ts);
        return ts.unlocked;
    }

    @Override
    public boolean isUnlocked(UUID playerUUID) {
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        if (stats == null) return false;
        if (!PROVIDER_NAME.equals(stats.getNannyMembershipProvider())) return false;
        return "ACTIVE".equals(stats.getNannyMembershipStatus())
                && tierAllowed(stats.getNannyMembershipTier());
    }

    @Override
    public void refresh(UUID playerUUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() { refreshSync(playerUUID); }
        });
    }

    private void refreshSync(UUID playerUUID) {
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        if (stats == null) return;
        if (!PROVIDER_NAME.equals(stats.getNannyMembershipProvider())) return;
        String refreshToken = stats.getNannyMembershipRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            // SS bearer-only path: nothing to refresh; mark LAPSED and surface on next login.
            markLapsed(playerUUID);
            return;
        }
        try {
            Map<String, String> form = new HashMap<String, String>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", refreshToken);
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
            String resp = http.postForm(TOKEN_URL, form);
            String access = http.extractJsonString(resp, "access_token");
            String newRefresh = http.extractJsonString(resp, "refresh_token");
            if (access == null) { markLapsed(playerUUID); return; }
            TierStatus ts = queryTier(access);
            savePlayerState(playerUUID, newRefresh == null ? refreshToken : newRefresh, ts);
        } catch (IOException e) {
            plugin.getLogger().warning("[Subscribestar] refresh failed for " + playerUUID + ": " + e.getMessage());
        }
    }

    private TierStatus queryTier(String accessToken) throws IOException {
        String resp = http.postJsonWithBearer(GRAPHQL_URL, VIEWER_QUERY, accessToken);
        boolean active = resp.contains("\"state\":\"active\"") || resp.contains("\"state\":\"ACTIVE\"");
        String tierTitle = "";
        int tIdx = resp.indexOf("\"tier\"");
        if (tIdx >= 0) {
            String t = http.extractJsonString(resp.substring(tIdx), "title");
            if (t != null) tierTitle = t;
        }
        TierStatus ts = new TierStatus();
        ts.tier = tierTitle;
        ts.status = active ? "ACTIVE" : "LAPSED";
        ts.unlocked = active && tierAllowed(tierTitle);
        return ts;
    }

    private boolean tierAllowed(String tierTitle) {
        if (requiredTiers.isEmpty()) return true;
        for (String t : requiredTiers) if (t.equals(tierTitle)) return true;
        return false;
    }

    private void markLapsed(UUID playerUUID) {
        Player p = Bukkit.getPlayer(playerUUID);
        if (p == null) return;
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        if (stats == null) return;
        stats.setNannyMembershipStatus("LAPSED");
        stats.setNannyMembershipLastCheck(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        SavePlayerStats.savePlayerStats(p);
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Player online = Bukkit.getPlayer(playerUUID);
                if (online != null) {
                    online.sendMessage("§e[Nanny] Your Subscribestar link has expired. Run /nanny link subscribestar to renew.");
                }
            }
        });
    }

    private void savePlayerState(UUID playerUUID, String refreshToken, TierStatus ts) {
        Player p = Bukkit.getPlayer(playerUUID);
        if (p == null) return;
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        if (stats == null) return;
        stats.setNannyMembershipProvider(PROVIDER_NAME);
        stats.setNannyMembershipRefreshToken(refreshToken);
        stats.setNannyMembershipTier(ts.tier);
        stats.setNannyMembershipStatus(ts.status);
        stats.setNannyMembershipLastCheck(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        SavePlayerStats.savePlayerStats(p);
    }

    private static class TierStatus {
        String tier = "";
        String status = "LAPSED";
        boolean unlocked = false;
    }
}
