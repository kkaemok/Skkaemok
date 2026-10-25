package org.kkaemok.skkaemok.appearance;

public record ChangeResult(Status status, String message) {
    public enum Status {
        SUCCESS,
        UNCHANGED,
        PENDING,
        INVALID_NAME,
        TOO_LONG,
        RESERVED_ALIAS,
        AMBIGUOUS_ALIAS,
        INVALID_SKIN,
        INVALID_DURATION,
        UNKNOWN_PRESET,
        CANCELLED,
        BUSY,
        CLOSED,
        STORAGE_ERROR,
        APPLY_FAILED
    }

    public boolean success() {
        return status == Status.SUCCESS || status == Status.UNCHANGED || status == Status.PENDING;
    }

    public static ChangeResult ok() {
        return new ChangeResult(Status.SUCCESS, "Appearance updated.");
    }
}
