package org.kkaemok.skkaemok.appearance;

import java.util.*;

/** Tracks overlapping temporary layers independently for each appearance field. */
public final class TimedOverrides {
    public record Key(AppearanceScope scope, AppearanceField field) {}

    private record Layer(UUID token, Object value, long expiresAt) {}

    private static final class Stack {
        final Object base;
        final List<Layer> layers = new ArrayList<>();

        Stack(Object base) {
            this.base = base;
        }
    }

    private final Map<Key, Stack> stacks = new LinkedHashMap<>();

    public TimedOverrides copy() {
        TimedOverrides copy = new TimedOverrides();
        stacks.forEach(
                (key, stack) -> {
                    Stack next = new Stack(stack.base);
                    next.layers.addAll(stack.layers);
                    copy.stacks.put(key, next);
                });
        return copy;
    }

    public void push(Key key, Object base, Object value, UUID token, long expiresAt) {
        stacks.computeIfAbsent(key, ignored -> new Stack(base))
                .layers
                .add(new Layer(token, value, expiresAt));
    }

    public void permanent(Key key) {
        stacks.remove(key);
    }

    public Object base(Key key, Object current) {
        Stack stack = stacks.get(key);
        return stack == null ? current : stack.base;
    }

    public Object current(Key key) {
        Stack stack = stacks.get(key);
        return stack == null ? null : stack.layers.getLast().value();
    }

    public Map<Key, Object> expire(long now) {
        Map<Key, Object> updates = new LinkedHashMap<>();
        for (var iterator = stacks.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            Stack stack = entry.getValue();
            Object previous = stack.layers.getLast().value();
            stack.layers.removeIf(layer -> layer.expiresAt() <= now);
            Object next = stack.layers.isEmpty() ? stack.base : stack.layers.getLast().value();
            if (!Appearance.same(previous, next)) updates.put(entry.getKey(), next);
            if (stack.layers.isEmpty()) iterator.remove();
        }
        return updates;
    }

    public Map<Key, Object> restoreAll() {
        Map<Key, Object> result = new LinkedHashMap<>();
        stacks.forEach((key, stack) -> result.put(key, stack.base));
        stacks.clear();
        return result;
    }

    public Set<Key> keys() {
        return Set.copyOf(stacks.keySet());
    }

    public record Recovery(AppearanceScope scope, Appearance base, Set<AppearanceField> fields) {}

    public List<Recovery> recovery() {
        Map<AppearanceScope, Appearance> bases = new LinkedHashMap<>();
        Map<AppearanceScope, Set<AppearanceField>> fields = new LinkedHashMap<>();
        stacks.forEach(
                (key, stack) -> {
                    bases.put(
                            key.scope(),
                            bases.getOrDefault(key.scope(), Appearance.EMPTY)
                                    .with(key.field(), stack.base));
                    fields.computeIfAbsent(
                                    key.scope(), ignored -> EnumSet.noneOf(AppearanceField.class))
                            .add(key.field());
                });
        return bases.entrySet().stream()
                .map(
                        entry ->
                                new Recovery(
                                        entry.getKey(),
                                        entry.getValue(),
                                        Set.copyOf(fields.get(entry.getKey()))))
                .toList();
    }
}
