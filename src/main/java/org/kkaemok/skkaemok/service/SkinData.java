package org.kkaemok.skkaemok.service;

public final class SkinData {
    private String value;
    private String signature;
    private String source;
    private long updatedAt;

    public SkinData() {
        // Required for Gson.
    }

    public SkinData(String value, String signature, String source, long updatedAt) {
        this.value = value;
        this.signature = signature;
        this.source = source;
        this.updatedAt = updatedAt;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public String getSource() {
        return source;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isValid() {
        return value != null && !value.isBlank();
    }
}
