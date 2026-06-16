package com.intellidesk.api.dto;

/**
 * Represents a source citation from the knowledge base.
 */
public record SourceReference(
        String documentTitle,
        String section
) {}
