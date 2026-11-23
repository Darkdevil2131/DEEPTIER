package com.deeptier.api.dto;

public record ApprovalDecisionRequest(
        String reviewerName,
        String comment,
        String actor,
        String reason
) {
    public String effectiveReviewerName() {
        if (reviewerName != null && !reviewerName.isBlank()) {
            return reviewerName;
        }
        return actor != null && !actor.isBlank() ? actor : "system";
    }

    public String effectiveComment() {
        if (comment != null && !comment.isBlank()) {
            return comment;
        }
        return reason;
    }
}
