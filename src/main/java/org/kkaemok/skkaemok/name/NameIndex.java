package org.kkaemok.skkaemok.name;

import net.kyori.adventure.text.Component;

import java.text.Normalizer;
import java.util.*;

/** Immutable, viewer-specific identities, safe to read from packet and suggestion callbacks. */
public final class NameIndex {
    public record Identity(UUID id, String account, String preferred, Set<String> aliases, Component output, boolean visible) {
        public Identity { aliases = Set.copyOf(aliases); }
    }
    private final Map<UUID, Identity> identities;
    private final Map<String, UUID> accounts;
    private final Map<String, UUID> aliases;

    public NameIndex(Collection<Identity> values) {
        Map<UUID, Identity> identities = new LinkedHashMap<>();
        Map<String, UUID> accounts = new HashMap<>();
        Map<String, UUID> aliases = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Identity identity : values) {
            identities.put(identity.id(), identity);
            accounts.put(key(identity.account()), identity.id());
            if (!identity.visible()) continue;
            for (String alias : identity.aliases()) {
                if (reserved(alias)) continue;
                String key = key(alias);
                if (ambiguous.contains(key)) continue;
                UUID previous = aliases.putIfAbsent(key, identity.id());
                if (previous != null && !previous.equals(identity.id())) { aliases.remove(key); ambiguous.add(key); }
            }
        }
        this.identities = Map.copyOf(identities);
        this.accounts = Map.copyOf(accounts);
        this.aliases = Map.copyOf(aliases);
    }

    public Identity identity(UUID id) { return identities.get(id); }
    public Collection<Identity> identities() { return identities.values(); }
    public UUID accountId(String account) { return accounts.get(key(account)); }

    public Identity resolve(String input) {
        if (input == null || reserved(input)) return null;
        try { return identities.get(UUID.fromString(input)); }
        catch (IllegalArgumentException ignored) { }
        UUID id = accounts.get(key(input));
        if (id == null) id = aliases.get(key(input));
        return id == null ? null : identities.get(id);
    }

    public String aliasAccount(String input) {
        if (input == null || reserved(input) || input.matches("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}")) return null;
        Identity identity = resolve(input);
        return identity == null || identity.account().equalsIgnoreCase(input) ? null : identity.account();
    }

    public String preferred(Identity identity) {
        Identity resolved = resolve(identity.preferred());
        return resolved != null && resolved.id().equals(identity.id()) ? identity.preferred() : identity.account();
    }

    public static boolean reserved(String input) {
        return input == null || input.isBlank() || input.startsWith("@") || input.startsWith("#") || input.equals("*");
    }
    public static String key(String name) {
        return name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
