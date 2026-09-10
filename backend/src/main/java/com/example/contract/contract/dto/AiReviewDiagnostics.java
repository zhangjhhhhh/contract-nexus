package com.example.contract.contract.dto;

public record AiReviewDiagnostics(
        boolean enabled,
        boolean endpointConfigured,
        boolean reviewAppIdConfigured,
        boolean apiKeyConfigured,
        String endpoint,
        boolean hasAiReview,
        int attachmentCount,
        int reviewableAttachmentCount,
        int extractedFileCount,
        int extractedTextLength
) {
}
