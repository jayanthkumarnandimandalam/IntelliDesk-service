package com.intellidesk.config;

/**
 * Configuration properties for security settings (rate limiting, message limits, CORS).
 */
public class SecurityProperties {

    private int rateLimitPerMinute = 20;
    private int maxMessageLength = 4000;
    private int maxAudioSizeMb = 10;
    private int maxAudioDurationSec = 60;
    private String corsAllowedOrigins = "http://localhost:5173";

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxAudioSizeMb() {
        return maxAudioSizeMb;
    }

    public void setMaxAudioSizeMb(int maxAudioSizeMb) {
        this.maxAudioSizeMb = maxAudioSizeMb;
    }

    public int getMaxAudioDurationSec() {
        return maxAudioDurationSec;
    }

    public void setMaxAudioDurationSec(int maxAudioDurationSec) {
        this.maxAudioDurationSec = maxAudioDurationSec;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }
}
