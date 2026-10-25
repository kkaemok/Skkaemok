package org.kkaemok.skkaemok.appearance;

import org.kkaemok.skkaemok.name.NameIndex;

import java.util.function.Function;

public final class NameValidation {
    private NameValidation() {}

    public static ChangeResult validate(
            AppearanceField field,
            String raw,
            Function<String, String> plain,
            boolean rejectReserved) {
        if (raw == null || raw.isBlank()) return ChangeResult.ok();
        String visible;
        try {
            visible = plain.apply(raw);
        } catch (RuntimeException ex) {
            return new ChangeResult(
                    ChangeResult.Status.INVALID_NAME, "The name could not be formatted.");
        }
        if (visible.isBlank()
                || visible.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT))
            return new ChangeResult(
                    ChangeResult.Status.INVALID_NAME,
                    "Names must contain visible text and no control or invisible format"
                        + " characters.");
        if ((field == AppearanceField.NAMETAG && raw.length() > 16) || raw.length() > 1024)
            return new ChangeResult(
                    ChangeResult.Status.TOO_LONG,
                    field == AppearanceField.NAMETAG
                            ? "Nametags must be 16 characters or fewer."
                            : "Names must be 1024 characters or fewer.");
        if (rejectReserved && field != AppearanceField.TABLIST && NameIndex.reserved(visible))
            return new ChangeResult(
                    ChangeResult.Status.RESERVED_ALIAS,
                    "Names starting with @ or #, and *, are reserved for native command"
                        + " arguments.");
        return ChangeResult.ok();
    }
}
