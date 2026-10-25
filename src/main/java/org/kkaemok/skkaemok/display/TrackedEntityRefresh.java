package org.kkaemok.skkaemok.display;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native pairing boundary for explicitly shown entities; never changes visibility ownership. */
final class TrackedEntityRefresh {
    record Pairing(Object tracker, Object viewer, Method removePairing, Method addPairing) {
        void remove() throws ReflectiveOperationException { removePairing.invoke(tracker, viewer); }
        void add() throws ReflectiveOperationException { addPairing.invoke(tracker, viewer); }
    }

    static Pairing prepare(Player target, Player viewer) throws ReflectiveOperationException {
        Object level = target.getWorld().getClass().getMethod("getHandle").invoke(target.getWorld());
        Object chunks = level.getClass().getMethod("getChunkSource").invoke(level);
        Object chunkMap = field(chunks, "chunkMap");
        Object entities = field(chunkMap, "entityMap");
        Object tracked = entities.getClass().getMethod("get", int.class).invoke(entities, target.getEntityId());
        if (tracked == null) throw new IllegalStateException("The entity is no longer tracked");
        Object serverEntity = field(tracked, "serverEntity");
        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
        return new Pairing(serverEntity, handle, serverEntity.getClass().getMethod("removePairing", handle.getClass()),
                serverEntity.getClass().getMethod("addPairing", handle.getClass()));
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(instance.getClass().getName() + "." + name);
    }
}
