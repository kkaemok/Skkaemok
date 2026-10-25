package org.kkaemok.skkaemok.command.nickname;

import com.github.retrooper.packetevents.protocol.chat.Node;
import com.github.retrooper.packetevents.protocol.chat.Parsers;
import com.github.retrooper.packetevents.resources.ResourceLocation;

import java.util.*;

/** Adds alias branches; never replaces the native selector/profile/score-holder branches. */
public final class NicknameCommandTree {
    private static final ResourceLocation ASK = new ResourceLocation("minecraft:ask_server");
    private static final Set<ResourceLocation> LOCAL = Set.of(new ResourceLocation("minecraft:available_sounds"), new ResourceLocation("minecraft:summonable_entities"));

    public List<Node> transform(List<Node> nodes, boolean postEffectsLocal) {
        List<Node> result = new ArrayList<>(nodes);
        Map<Integer, List<Integer>> aliases = new HashMap<>();
        Map<Integer, Node> templates = new HashMap<>();
        Set<String> names = new HashSet<>();
        nodes.forEach(node -> node.getName().ifPresent(names::add));
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if ((node.getFlags() & Node.TYPE_MASK) != Node.TYPE_ARGUMENT) continue;
            var parser = node.getParser().orElse(null);
            if (parser != Parsers.ENTITY && parser != Parsers.GAME_PROFILE && parser != Parsers.SCORE_HOLDER) continue;
            String name = node.getName().orElse("target") + "__skkaemok_alias_" + index;
            if (node.getName().orElse("").contains("__skkaemok_alias_") || names.contains(name + "_0")) continue;
            List<?> properties = node.getProperties().orElse(List.of());
            if (parser != Parsers.GAME_PROFILE && (properties.isEmpty() || !(properties.getFirst() instanceof Byte))) continue;
            List<Integer> added = new ArrayList<>();
            byte flags = (byte) (node.getFlags() & ~Node.FLAG_CUSTOM_SUGGESTIONS);
            if (parser == Parsers.ENTITY) {
                boolean single = (((Byte) properties.getFirst()) & 1) != 0;
                added.add(result.size());
                result.add(new Node(flags, node.getChildren(), node.getRedirectNodeIndex(), name + "_0", Parsers.SCORE_HOLDER,
                        List.of((byte) (single ? 0 : 1)), null));
            }
            Node quoted = new Node(flags, node.getChildren(), node.getRedirectNodeIndex(), name + "_" + added.size(), Parsers.BRIGADIER_STRING, List.of(1), null);
            added.add(result.size()); result.add(quoted);
            aliases.put(index, added); templates.put(index, quoted);
        }
        if (aliases.isEmpty()) return nodes;
        Map<Integer, Integer> serverAliases = new HashMap<>();
        for (int index = 0; index < result.size(); index++) {
            Node node = result.get(index);
            Integer target = node.getChildren().stream().filter(aliases::containsKey).findFirst().orElse(null);
            if (target == null) continue;
            boolean alreadyAsks = node.getChildren().stream().map(result::get).anyMatch(child -> {
                ResourceLocation provider = child.getSuggestionsType().orElse(null);
                return (child.getFlags() & Node.FLAG_CUSTOM_SUGGESTIONS) != 0 && (provider == null || !LOCAL.contains(provider))
                        && !(postEffectsLocal && new ResourceLocation("minecraft:post_effects").equals(provider));
            });
            Integer serverAlias = null;
            if (!alreadyAsks) {
                serverAlias = serverAliases.get(target);
                if (serverAlias == null) {
                    Node template = templates.get(target);
                    serverAlias = result.size(); serverAliases.put(target, serverAlias);
                    result.add(new Node((byte) (template.getFlags() | Node.FLAG_CUSTOM_SUGGESTIONS), template.getChildren(),
                            template.getRedirectNodeIndex(), template.getName().orElseThrow() + "_server",
                            template.getParser().orElse(null), template.getProperties().orElse(null), ASK));
                }
            }
            List<Integer> children = new ArrayList<>();
            for (Integer child : node.getChildren()) {
                children.add(child);
                for (Integer alias : aliases.getOrDefault(child, List.of())) {
                    children.add(child.equals(target) && alias.equals(aliases.get(child).getLast()) && serverAlias != null ? serverAlias : alias);
                }
            }
            result.set(index, new Node(node.getFlags(), children, node.getRedirectNodeIndex(), node.getName().orElse(null),
                    node.getParser().orElse(null), node.getProperties().orElse(null), node.getSuggestionsType().orElse(null)));
        }
        return result;
    }
}
