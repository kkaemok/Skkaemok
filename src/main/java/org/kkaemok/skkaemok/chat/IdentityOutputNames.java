package org.kkaemok.skkaemok.chat;

import com.github.retrooper.packetevents.protocol.chat.*;
import com.github.retrooper.packetevents.protocol.chat.message.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import org.kkaemok.skkaemok.name.NameIndex;

import java.util.*;

/** Rewrites identified player labels, not arbitrary words or the signed message body. */
public final class IdentityOutputNames {
    private static final Key PLAYER = Key.key("minecraft:player");
    private final NameIndex names;
    public IdentityOutputNames(NameIndex names) { this.names = names; }

    public Component rewrite(Component component) { return rewrite(component, null); }
    private Component rewrite(Component component, UUID inherited) {
        var hover = component.hoverEvent();
        HoverEvent.ShowEntity entity = hover != null && hover.value() instanceof HoverEvent.ShowEntity value ? value : null;
        UUID id = entity == null ? inherited : PLAYER.equals(entity.type()) ? entity.id() : null;
        NameIndex.Identity identity = id == null ? null : names.identity(id);
        Component result = component;
        boolean replaced = identity != null && identity.visible() && component instanceof TextComponent text
                && text.content().equalsIgnoreCase(identity.account()) && !identity.output().equals(Component.text(identity.account()));
        if (replaced) result = identity.output().style(identity.output().style().merge(component.style(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
        List<Component> children = new ArrayList<>();
        if (replaced) children.addAll(result.children());
        boolean childChanged = replaced;
        for (Component child : component.children()) {
            Component next = rewrite(child, id); children.add(next); childChanged |= next != child;
        }
        if (childChanged) result = result.children(children);
        if (component instanceof TranslatableComponent translated) {
            List<TranslationArgument> arguments = new ArrayList<>();
            boolean changed = false;
            for (TranslationArgument argument : translated.arguments()) {
                if (argument.value() instanceof Component value) {
                    Component next = rewrite(value, id);
                    arguments.add(next == value ? argument : TranslationArgument.component(next)); changed |= next != value;
                } else arguments.add(argument);
            }
            if (changed) result = ((TranslatableComponent) result).arguments(arguments);
        }
        if (entity != null && id != null && entity.name() != null) {
            Component next = rewrite(entity.name(), id);
            if (next != entity.name()) result = result.hoverEvent(HoverEvent.showEntity(entity.name(next)));
        }
        return result;
    }

    private Component name(Component component, UUID sender, boolean accountFallback) {
        UUID id = sender;
        if (id == null && accountFallback && component instanceof TextComponent text) id = names.accountId(text.content());
        return rewrite(component, id);
    }

    public ChatType.Bound rewrite(ChatType.Bound bound, UUID sender) {
        Component nextSender = name(bound.getName(), sender, false);
        boolean whisper = bound.getType().equals(ChatTypes.MSG_COMMAND_INCOMING) || bound.getType().equals(ChatTypes.MSG_COMMAND_OUTGOING);
        Component target = bound.getTargetName();
        Component nextTarget = target == null ? null : name(target, null, whisper);
        return nextSender == bound.getName() && nextTarget == target ? bound : new ChatType.Bound(bound.getType(), nextSender, nextTarget);
    }

    public ChatMessage rewrite(ChatMessage message) {
        if (!(message instanceof ChatMessage_v1_19_3 signed)
                || (message.getClass() != ChatMessage_v1_19_3.class && message.getClass() != ChatMessage_v1_21_5.class)) return message;
        ChatType.Bound bound = rewrite(signed.getChatFormatting(), signed.getSenderUUID());
        if (bound == signed.getChatFormatting()) return message;
        if (signed instanceof ChatMessage_v1_21_5 modern) return new ChatMessage_v1_21_5(modern.getGlobalIndex(), signed.getSenderUUID(),
                signed.getIndex(), signed.getSignature(), signed.getPlainContent(), signed.getTimestamp(), signed.getSalt(),
                signed.getLastSeenMessagesPacked(), signed.getUnsignedChatContent().orElse(null), signed.getFilterMask(), bound);
        return new ChatMessage_v1_19_3(signed.getSenderUUID(), signed.getIndex(), signed.getSignature(), signed.getPlainContent(),
                signed.getTimestamp(), signed.getSalt(), signed.getLastSeenMessagesPacked(), signed.getUnsignedChatContent().orElse(null), signed.getFilterMask(), bound);
    }
}
