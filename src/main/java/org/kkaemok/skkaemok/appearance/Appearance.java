package org.kkaemok.skkaemok.appearance;

import org.kkaemok.skkaemok.skin.SkinData;

import java.util.Objects;

/** Raw values for one scope. Null removes that scope's override and restores inheritance. */
public record Appearance(String nametag, String chat, String tablist, SkinData skin) {
    public static final Appearance EMPTY = new Appearance(null, null, null, null);

    public Object value(AppearanceField field) {
        return switch (field) {
            case NAMETAG -> nametag;
            case CHAT -> chat;
            case TABLIST -> tablist;
            case SKIN -> skin;
        };
    }

    public Appearance with(AppearanceField field, Object value) {
        return switch (field) {
            case NAMETAG -> new Appearance((String) value, chat, tablist, skin);
            case CHAT -> new Appearance(nametag, (String) value, tablist, skin);
            case TABLIST -> new Appearance(nametag, chat, (String) value, skin);
            case SKIN -> new Appearance(nametag, chat, tablist, (SkinData) value);
        };
    }

    public static boolean same(Object first, Object second) {
        if (first instanceof SkinData a && second instanceof SkinData b)
            return Objects.equals(a.getValue(), b.getValue())
                    && Objects.equals(a.getSignature(), b.getSignature());
        return Objects.equals(first, second);
    }
}
