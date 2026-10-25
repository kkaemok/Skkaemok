package org.kkaemok.skkaemok.appearance;

import java.util.Objects;
import java.util.UUID;

public record AppearanceScope(UUID target, UUID viewer) {
    public AppearanceScope {
        Objects.requireNonNull(target, "target");
    }
}
