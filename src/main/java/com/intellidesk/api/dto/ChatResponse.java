package com.intellidesk.api.dto;

import java.util.List;

/**
 * Response DTO for the text chat endpoint.
 */
public record ChatResponse(
        String answer,
        List<SourceReference> sources,
        String groundingNote,
        long latencyMs
) {}
