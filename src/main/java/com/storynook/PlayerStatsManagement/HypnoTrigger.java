package com.storynook.PlayerStatsManagement;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HypnoTrigger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String word;        // trigger word
    private String type;        // "wetting" or "messing"
    private LocalDateTime expiry;
    private String casterUUID;  // UUID string of who applied this

    public HypnoTrigger(String word, String type, LocalDateTime expiry, String casterUUID) {
        this.word = word;
        this.type = type;
        this.expiry = expiry;
        this.casterUUID = casterUUID;
    }

    public String getWord() { return word; }
    public String getType() { return type; }
    public LocalDateTime getExpiry() { return expiry; }
    public void setExpiry(LocalDateTime expiry) { this.expiry = expiry; }
    public String getCasterUUID() { return casterUUID; }

    public boolean isExpired() {
        return expiry.isBefore(LocalDateTime.now());
    }

    // Serialize to pipe-delimited string: "word|type|expiry|casterUUID"
    public String serialize() {
        return word + "|" + type + "|" + expiry.format(FORMATTER) + "|" + casterUUID;
    }

    // Deserialize from pipe-delimited string; returns null if invalid
    public static HypnoTrigger deserialize(String s) {
        if (s == null) return null;
        String[] parts = s.split("\\|", 4);
        if (parts.length < 4) return null;
        try {
            LocalDateTime expiry = LocalDateTime.parse(parts[2], FORMATTER);
            return new HypnoTrigger(parts[0], parts[1], expiry, parts[3]);
        } catch (Exception e) {
            return null;
        }
    }
}
