package com.sandevistan;

public enum Phase {
    READY, ACTIVE, COOLDOWN;

    public static Phase of(int ordinal) {
        Phase[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : READY;
    }
}
