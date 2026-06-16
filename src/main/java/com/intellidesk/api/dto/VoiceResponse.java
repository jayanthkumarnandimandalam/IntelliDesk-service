package com.intellidesk.api.dto;

import java.util.List;

/**
 * Response DTO for the voice chat endpoint.
 */
public record VoiceResponse(
        String transcript,
        String answer,
        List<SourceReference> sources,
        long latencyMs
) {}
