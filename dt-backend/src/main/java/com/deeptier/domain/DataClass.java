package com.deeptier.domain;

/**
 * Data-honesty envelope: every computed or stored value must carry one of these.
 * SYNTHETIC  - demo/seed data, not from a real source
 * OBSERVED   - from a live integration (S/4HANA, carrier API)
 * ESTIMATED  - ML/statistical inference with stated confidence
 * ASSUMED    - fallback constant used when real data unavailable
 */
public enum DataClass {
    SYNTHETIC,
    OBSERVED,
    ESTIMATED,
    ASSUMED
}
