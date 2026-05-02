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
 * Patreon OAuth code-paste provider.
 * Token endpoint: https://www.patreon.com/api/oauth2/token
 * Identity endpoint: https://www.patreon.com/api/oauth2/v2/identity
 *   ?include=memberships.currently_entitled_tiers
 *   &fields[member]=patron_status
 *   &fields[tier]=title
 */
public class PatreonMembershipProvider implements MembershipProvider {

    public static final String PROVIDER_NAME = "PATREON";
    private static final String TOKEN_URL = "https://www.patreon.com/api/oauth2/token";
    private static final String IDENTITY_URL =
            "https://www.patreon.com/api/oauth2/v2/identity"
                    + "?include=memberships.currently_entitled_tiers"
                    + "&fields%5Bmember%5D=patron_status"
                    + "&fields%5Btier%5D=title";
    private static final String AUTHORIZE_URL = "https://www.patreon.com/oauth2/authorize";
    private static final String SCOPES = "identity identity.memberships";

    private final Plugin plugin;
    private final OAuthHelper http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final List<String> requiredTiers;

    public PatreonMembershipProvider(Plugin plugin, OAuthHelper http,
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Exchange a code+state for a refresh token; query the patron's tier; save state to PlayerStats.
     * Returns true on success (player is an active patron meeting tier requirement).
     * Throws IOException with a player-readable message on failure.
     */
    public boolean linkFromCode(UUID expectedPlayer, String code, String state) throws IOException {
        UUID stateOwner = http.consumeState(state);
        if (stateOwner == null || !stateOwner.equals(expectedPlayer)) {
            throw new IOException("Link code does not match the player who started linking. Run /nanny link patreon again.");
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirectUri);
        String tokenResp = http.postForm(TOKEN_URL, form);
        String accessToken = http.extractJsonString(tokenResp, "access_token");
        String refreshToken = http.extractJsonString(tokenResp, "refresh_token");
        if (accessToken == null) throw new IOException("Patreon did not return an access token.");

        TierStatus ts = queryTier(accessToken);
        savePlayerState(expectedPlayer, refreshToken == null ? "" : refreshToken, ts);
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
        if (refreshToken == null || refreshToken.isEmpty()) return;
        try {
            Map<String, String> form = new HashMap<String, String>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", refreshToken);
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
            String resp = http.postForm(TOKEN_URL, form);
            String access = http.extractJsonString(resp, "access_token");
            String newRefresh = http.extractJsonString(resp, "refresh_token");
            if (access == null) {
                plugin.getLogger().warning("[Patreon] No access_token in refresh response for " + playerUUID);
                return;
            }
            TierStatus ts = queryTier(access);
            savePlayerState(playerUUID, newRefresh == null ? refreshToken : newRefresh, ts);
        } catch (IOException e) {
            plugin.getLogger().warning("[Patreon] refresh failed for " + playerUUID + ": " + e.getMessage());
            // Keep last-known state per spec.
        }
    }

    private TierStatus queryTier(String accessToken) throws IOException {
        String resp = http.getWithBearer(IDENTITY_URL, accessToken);
        boolean activePatron = resp.contains("\"patron_status\":\"active_patron\"");
        // Naive: pull the first "title" we find in the included tiers section.
        String tierTitle = "";
        int tIdx = resp.indexOf("\"type\":\"tier\"");
        if (tIdx >= 0) {
            String slice = resp.substring(tIdx);
            String t = http.extractJsonString(slice, "title");
            if (t != null) tierTitle = t;
        }
        TierStatus ts = new TierStatus();
        ts.tier = tierTitle;
        ts.unlocked = activePatron && tierAllowed(tierTitle);
        ts.status = activePatron ? "ACTIVE" : "LAPSED";
        return ts;
    }

    private boolean tierAllowed(String tierTitle) {
        if (requiredTiers.isEmpty()) return true;
        for (String t : requiredTiers) if (t.equals(tierTitle)) return true;
        return false;
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
