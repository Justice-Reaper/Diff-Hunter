package org.diffhunter.model;

/**
 * Types of row differences based on request/response comparison.
 */
public enum RowDiffType {
    NONE,
    REQUEST_ONLY,
    RESPONSE_ONLY,
    BOTH
}
