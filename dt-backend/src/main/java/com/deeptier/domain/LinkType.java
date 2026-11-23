package com.deeptier.domain;

/**
 * Supplier-link evidence quality used in the tier map.
 * DECLARED   - supplier self-declared (highest trust)
 * INFERRED   - derived from BOM, trade data, or ML (carry confidence 0-1)
 * ASSUMED    - structural placeholder, lowest trust
 */
public enum LinkType {
    DECLARED,
    INFERRED,
    ASSUMED
}
