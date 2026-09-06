package org.kkaemok.skkaemok.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.NameRewriteService;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class OutgoingMessageRewriteListener {
    private static final int MAX_STRUCTURE_DEPTH = 4;

    private final JavaPlugin plugin;
    private final NameRewriteService nameRewriteService;
    private final ProtocolManager protocolManager;
    private final PacketAdapter packetAdapter;

    public OutgoingMessageRewriteListener(JavaPlugin plugin,
                                          NameRewriteService nameRewriteService) {
        if (plugin == null || nameRewriteService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }

        this.plugin = plugin;
        this.nameRewriteService = nameRewriteService;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.packetAdapter = new PacketAdapter(plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.DISGUISED_CHAT,
                PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                rewritePacket(event);
            }
        };
    }

    public void register() {
        protocolManager.addPacketListener(packetAdapter);
    }

    public void unregister() {
        protocolManager.removePacketListener(packetAdapter);
    }

    private void rewritePacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet == null) {
            return;
        }

        boolean debugEnabled = isDebugEnabled();
        Player viewer = event.getPlayer();
        String viewerName = viewer == null ? "<none>" : viewer.getName();
        RewriteDebug debug = new RewriteDebug(packet.getType().name(), viewerName);
        try {
            if (debugEnabled) {
                collectTopLevelFields(packet, debug);
            }
            rewriteTopLevelChatTypeBounds(packet, debug, viewer);
            if (shouldRewritePacketComponents(packet)) {
                rewriteStructure(packet, 0, debug, viewer);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to rewrite outgoing chat packet.", e);
            debug.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (debugEnabled) {
                logDebug(debug);
            }
        }
    }

    private boolean shouldRewritePacketComponents(PacketContainer packet) {
        PacketType packetType = packet.getType();
        if (packetType == PacketType.Play.Server.CHAT
                || packetType == PacketType.Play.Server.DISGUISED_CHAT) {
            return plugin.getConfig().getBoolean("output-name-rewrite.rewrite-chat-message-content", false);
        }
        return true;
    }

    private void collectTopLevelFields(PacketContainer packet, RewriteDebug debug) {
        StructureModifier<Object> fields = packet.getModifier();
        debug.genericSlots = fields.size();
        for (int i = 0; i < fields.size(); i++) {
            try {
                debug.addFieldSample(i, fields.readSafely(i));
            } catch (Exception e) {
                debug.addReadError("field", i, e);
            }
        }
    }

    private void rewriteTopLevelChatTypeBounds(PacketContainer packet, RewriteDebug debug, Player viewer) {
        StructureModifier<Object> fields = packet.getModifier();
        for (int i = 0; i < fields.size(); i++) {
            Object original = readModifier(fields, i, debug, "field");
            Object rewritten = rewriteChatTypeBound(original, debug, viewer);
            if (rewritten != null) {
                writeModifier(fields, i, rewritten, debug, "field");
                debug.boundChatTypeRewrites++;
                debug.addSample("bound-rewritten field[" + i + "]=" + original.getClass().getName());
            }
        }
    }

    private Object rewriteChatTypeBound(Object value, RewriteDebug debug, Player viewer) {
        if (value == null || !isLikelyChatTypeBound(value)) {
            return null;
        }
        Class<?> type = value.getClass();
        if (!type.isRecord()) {
            debug.addSample("bound-skip non-record=" + type.getName());
            return null;
        }

        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] constructorTypes = new Class<?>[components.length];
        Object[] constructorArgs = new Object[components.length];
        boolean changed = false;

        try {
            for (int i = 0; i < components.length; i++) {
                RecordComponent recordComponent = components[i];
                constructorTypes[i] = recordComponent.getType();
                Object originalValue = recordComponent.getAccessor().invoke(value);
                Object rewrittenValue = rewriteRecordComponentValue(originalValue, debug, viewer);
                if (rewrittenValue != null) {
                    constructorArgs[i] = rewrittenValue;
                    changed = true;
                } else {
                    constructorArgs[i] = originalValue;
                }
            }

            if (!changed) {
                return null;
            }

            Constructor<?> constructor = type.getDeclaredConstructor(constructorTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(constructorArgs);
        } catch (Exception e) {
            debug.addSample("bound-error " + type.getName() + "="
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private boolean isLikelyChatTypeBound(Object value) {
        String className = value.getClass().getName();
        return className.contains("network.chat.ChatType")
                && className.toLowerCase().contains("bound");
    }

    private Object rewriteRecordComponentValue(Object value, RewriteDebug debug, Player viewer) {
        if (value == null) {
            return null;
        }
        if (isNmsChatComponent(value)) {
            Object rewritten = rewriteComponentHandle(value, debug, viewer);
            return rewritten == null ? null : rewritten;
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return null;
            }
            Object optionalValue = optional.get();
            if (!isNmsChatComponent(optionalValue)) {
                return null;
            }
            Object rewritten = rewriteComponentHandle(optionalValue, debug, viewer);
            return rewritten == null ? null : Optional.of(rewritten);
        }
        return null;
    }

    private boolean isNmsChatComponent(Object value) {
        return MinecraftReflection.getIChatBaseComponentClass().isInstance(value);
    }

    private void rewriteStructure(AbstractStructure structure, int depth, RewriteDebug debug, Player viewer) {
        if (structure == null || depth > MAX_STRUCTURE_DEPTH) {
            return;
        }

        rewriteChatComponents(structure, debug, viewer);
        rewriteChatComponentArrays(structure, debug, viewer);
        rewriteNestedStructures(structure, depth, debug, viewer);
        rewriteOptionalNestedStructures(structure, depth, debug, viewer);
    }

    private void rewriteChatComponents(AbstractStructure structure, RewriteDebug debug, Player viewer) {
        StructureModifier<WrappedChatComponent> components = structure.getChatComponents();
        debug.componentSlots += components.size();
        for (int i = 0; i < components.size(); i++) {
            WrappedChatComponent original = readModifier(components, i, debug, "component");
            WrappedChatComponent rewritten = rewriteWrappedComponent(original, debug, viewer);
            if (rewritten != null) {
                writeModifier(components, i, rewritten, debug, "component");
            }
        }
    }

    private void rewriteChatComponentArrays(AbstractStructure structure, RewriteDebug debug, Player viewer) {
        StructureModifier<WrappedChatComponent[]> componentArrays = structure.getChatComponentArrays();
        debug.componentArraySlots += componentArrays.size();
        for (int i = 0; i < componentArrays.size(); i++) {
            WrappedChatComponent[] original = readModifier(componentArrays, i, debug, "component-array");
            if (original == null || original.length == 0) {
                continue;
            }

            debug.arrayComponents += original.length;
            boolean changed = false;
            WrappedChatComponent[] rewritten = original.clone();
            for (int j = 0; j < rewritten.length; j++) {
                WrappedChatComponent rewrittenComponent = rewriteWrappedComponent(rewritten[j], debug, viewer);
                if (rewrittenComponent != null) {
                    rewritten[j] = rewrittenComponent;
                    changed = true;
                }
            }

            if (changed) {
                writeModifier(componentArrays, i, rewritten, debug, "component-array");
            }
        }
    }

    private void rewriteNestedStructures(AbstractStructure structure, int depth, RewriteDebug debug, Player viewer) {
        StructureModifier<InternalStructure> structures = getNestedStructures(structure);
        if (structures == null) {
            return;
        }
        debug.structureSlots += structures.size();
        for (int i = 0; i < structures.size(); i++) {
            InternalStructure nested = readModifier(structures, i, debug, "structure");
            if (nested != null) {
                rewriteStructure(nested, depth + 1, debug, viewer);
            }
        }
    }

    private void rewriteOptionalNestedStructures(AbstractStructure structure, int depth, RewriteDebug debug, Player viewer) {
        StructureModifier<Optional<InternalStructure>> structures = getOptionalNestedStructures(structure);
        if (structures == null) {
            return;
        }
        debug.optionalStructureSlots += structures.size();
        for (int i = 0; i < structures.size(); i++) {
            Optional<InternalStructure> nested = readModifier(structures, i, debug, "optional-structure");
            if (nested != null) {
                nested.ifPresent(internalStructure -> rewriteStructure(internalStructure, depth + 1, debug, viewer));
            }
        }
    }

    private <T> T readModifier(StructureModifier<T> modifier, int index, RewriteDebug debug, String source) {
        try {
            return modifier.readSafely(index);
        } catch (Exception e) {
            debug.addReadError(source, index, e);
            return null;
        }
    }

    private <T> void writeModifier(StructureModifier<T> modifier,
                                   int index,
                                   T value,
                                   RewriteDebug debug,
                                   String source) {
        try {
            modifier.writeSafely(index, value);
        } catch (Exception e) {
            debug.addReadError(source + "-write", index, e);
        }
    }

    private StructureModifier<InternalStructure> getNestedStructures(AbstractStructure structure) {
        if (structure instanceof PacketContainer packet) {
            return packet.getStructures();
        }
        if (structure instanceof InternalStructure internalStructure) {
            return internalStructure.getStructures();
        }
        return null;
    }

    private StructureModifier<Optional<InternalStructure>> getOptionalNestedStructures(AbstractStructure structure) {
        if (structure instanceof PacketContainer packet) {
            return packet.getOptionalStructures();
        }
        if (structure instanceof InternalStructure internalStructure) {
            return internalStructure.getOptionalStructures();
        }
        return null;
    }

    private WrappedChatComponent rewriteWrappedComponent(WrappedChatComponent wrapped, RewriteDebug debug, Player viewer) {
        if (wrapped == null) {
            return null;
        }
        String json = wrapped.getJson();
        if (json == null || json.isBlank()) {
            return null;
        }

        debug.componentJsons++;
        debug.addSample(json);
        Component original = GsonComponentSerializer.gson().deserialize(json);
        Component rewritten = nameRewriteService.rewriteOnlinePlayerNames(original, viewer);
        if (original.equals(rewritten)) {
            return null;
        }

        debug.changedComponents++;
        String rewrittenJson = GsonComponentSerializer.gson().serialize(rewritten);
        debug.addSample("rewritten=" + rewrittenJson);
        return WrappedChatComponent.fromJson(rewrittenJson);
    }

    private Object rewriteComponentHandle(Object componentHandle, RewriteDebug debug, Player viewer) {
        WrappedChatComponent wrapped = WrappedChatComponent.fromHandle(componentHandle);
        WrappedChatComponent rewritten = rewriteWrappedComponent(wrapped, debug, viewer);
        return rewritten == null ? null : rewritten.getHandle();
    }

    private boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("output-name-rewrite.debug", false);
    }

    private void logDebug(RewriteDebug debug) {
        plugin.getLogger().info("[output-name-rewrite debug] packet=" + debug.packetType
                + " viewer=" + debug.viewerName
                + " genericSlots=" + debug.genericSlots
                + " componentSlots=" + debug.componentSlots
                + " componentArraySlots=" + debug.componentArraySlots
                + " arrayComponents=" + debug.arrayComponents
                + " structureSlots=" + debug.structureSlots
                + " optionalStructureSlots=" + debug.optionalStructureSlots
                + " componentJsons=" + debug.componentJsons
                + " changedComponents=" + debug.changedComponents
                + " boundChatTypeRewrites=" + debug.boundChatTypeRewrites
                + " readErrors=" + debug.readErrors
                + (debug.error == null ? "" : " error=" + debug.error));

        for (String sample : debug.samples) {
            plugin.getLogger().info("[output-name-rewrite debug] " + sample);
        }
    }

    private static final class RewriteDebug {
        private static final int MAX_SAMPLES = 24;
        private static final int MAX_SAMPLE_LENGTH = 1000;

        private final String packetType;
        private final String viewerName;
        private final List<String> samples = new ArrayList<>();

        private int componentSlots;
        private int componentArraySlots;
        private int arrayComponents;
        private int structureSlots;
        private int optionalStructureSlots;
        private int componentJsons;
        private int changedComponents;
        private int boundChatTypeRewrites;
        private int genericSlots;
        private int readErrors;
        private String error;

        private RewriteDebug(String packetType, String viewerName) {
            this.packetType = packetType;
            this.viewerName = viewerName;
        }

        private void addSample(String json) {
            if (samples.size() >= MAX_SAMPLES) {
                return;
            }
            String normalized = json.replace('\n', ' ').replace('\r', ' ');
            if (normalized.length() > MAX_SAMPLE_LENGTH) {
                normalized = normalized.substring(0, MAX_SAMPLE_LENGTH) + "...";
            }
            samples.add("component=" + normalized);
        }

        private void addFieldSample(int index, Object value) {
            if (samples.size() >= MAX_SAMPLES) {
                return;
            }
            if (value == null) {
                samples.add("field[" + index + "]=null");
                return;
            }

            String type = value.getClass().getName();
            String valueString = value.toString().replace('\n', ' ').replace('\r', ' ');
            if (valueString.length() > MAX_SAMPLE_LENGTH) {
                valueString = valueString.substring(0, MAX_SAMPLE_LENGTH) + "...";
            }
            samples.add("field[" + index + "]=" + type + " value=" + valueString);
        }

        private void addReadError(String source, int index, Exception e) {
            readErrors++;
            if (samples.size() >= MAX_SAMPLES) {
                return;
            }
            samples.add("read-error " + source + "[" + index + "]="
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
