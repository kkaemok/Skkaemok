package org.kkaemok.skkaemok.command.nickname;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import com.mojang.brigadier.tree.*;

import org.bukkit.entity.Player;
import org.kkaemok.skkaemok.name.NameIndex;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Native field access is isolated here; serializers continue seeing the native argument types. */
final class NativeBrigadierAdapter {
    private static final Set<String> TYPES =
            Set.of(
                    "net.minecraft.commands.arguments.EntityArgument",
                    "net.minecraft.commands.arguments.GameProfileArgument",
                    "net.minecraft.commands.arguments.ScoreHolderArgument");
    private final Field typeField = accessible(ArgumentCommandNode.class, "type");
    private final Field suggestionsField =
            accessible(ArgumentCommandNode.class, "customSuggestions");

    private record Change(
            Field field,
            ArgumentType<?> original,
            SuggestionProvider<?> provider,
            ArgumentType<?> decorated,
            SuggestionProvider<?> suggestions) {}

    private final Map<ArgumentCommandNode<?, ?>, Change> changes = new IdentityHashMap<>();

    static UUID viewer(Object source) {
        if (source == null) return null;
        Object sender;
        if (source instanceof io.papermc.paper.command.brigadier.CommandSourceStack stack)
            sender = stack.getSender();
        else {
            try {
                sender = source.getClass().getMethod("getBukkitSender").invoke(source);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Unsupported native command source", ex);
            }
        }
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    synchronized void install(
            CommandNode<?> root, Function<Object, NameIndex> index, Consumer<String> debug) {
        walk(
                root,
                true,
                node -> {
                    if (!(node instanceof ArgumentCommandNode<?, ?> argument)
                            || changes.containsKey(argument)
                            || !TYPES.contains(argument.getType().getClass().getName())) return;
                    installArgument(argument, index, debug);
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void installArgument(
            ArgumentCommandNode<?, ?> node,
            Function<Object, NameIndex> index,
            Consumer<String> debug) {
        try {
            Field field =
                    node.getClass()
                                    .getName()
                                    .equals(
                                            "io.papermc.paper.command.brigadier.argument.WrappedArgumentCommandNode")
                            ? accessible(node.getClass(), "pureArgumentType")
                            : typeField;
            ArgumentType<Object> original = (ArgumentType<Object>) field.get(node);
            SuggestionProvider<Object> provider =
                    (SuggestionProvider<Object>) node.getCustomSuggestions();
            boolean uuid =
                    node.getType()
                                    .getClass()
                                    .getName()
                                    .equals("net.minecraft.commands.arguments.EntityArgument")
                            && !accessible(node.getType().getClass(), "playersOnly")
                                    .getBoolean(node.getType());
            AliasArgumentType<Object> decorated =
                    new AliasArgumentType<>(
                            original,
                            (input, source) -> {
                                NameIndex names = index.apply(source);
                                String account = names.aliasAccount(input);
                                return account != null && uuid
                                        ? names.resolve(input).id().toString()
                                        : account;
                            });
            AliasSuggestions suggestions = new AliasSuggestions(original, provider, index);
            field.set(node, decorated);
            suggestionsField.set(node, suggestions);
            changes.put(node, new Change(field, original, provider, decorated, suggestions));
            debug.accept(
                    "node=" + node.getName() + " parser=" + node.getType().getClass().getName());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not adapt native command argument", ex);
        }
    }

    synchronized void prepareClient(CommandNode<?> root) {
        walk(
                root,
                false,
                node -> {
                    if (!(node instanceof ArgumentCommandNode<?, ?> argument)) return;
                    try {
                        if (argument.getType() instanceof AliasArgumentType<?> alias)
                            typeField.set(argument, alias.delegate);
                        if (argument.getCustomSuggestions() instanceof AliasSuggestions alias)
                            suggestionsField.set(argument, alias.provider);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException(
                                "Could not restore client argument serialization", ex);
                    }
                });
    }

    synchronized void restore() {
        changes.forEach(
                (node, change) -> {
                    try {
                        if (change.field.get(node) == change.decorated)
                            change.field.set(node, change.original);
                        if (suggestionsField.get(node) == change.suggestions)
                            suggestionsField.set(node, change.provider);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException(
                                "Could not restore native command argument", ex);
                    }
                });
        changes.clear();
    }

    private static Field accessible(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Unsupported Brigadier field " + owner.getName() + "." + name, ex);
        }
    }

    private static void walk(CommandNode<?> root, boolean unwrap, Consumer<CommandNode<?>> action) {
        Set<CommandNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<CommandNode<?>> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            CommandNode<?> node = pending.removeFirst();
            Class<?> boundary = null;
            for (Class<?> type = node.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals("io.papermc.paper.command.brigadier.ApiMirrorRootNode")
                        || type.getName()
                                .equals("io.papermc.paper.command.brigadier.ShadowBrigNode")) {
                    boundary = type;
                    break;
                }
            }
            if (boundary != null) {
                if (!unwrap) continue;
                try {
                    node =
                            boundary.getName().endsWith("ApiMirrorRootNode")
                                    ? ((CommandDispatcher<?>)
                                                    boundary.getMethod("getDispatcher")
                                                            .invoke(node))
                                            .getRoot()
                                    : (CommandNode<?>) boundary.getMethod("getHandle").invoke(node);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Unsupported Paper command facade", ex);
                }
            }
            if (!visited.add(node)) continue;
            action.accept(node);
            pending.addAll(node.getChildren());
            if (node.getRedirect() != null) pending.add(node.getRedirect());
        }
    }

    private static final class AliasSuggestions implements SuggestionProvider<Object> {
        final ArgumentType<Object> original;
        final SuggestionProvider<Object> provider;
        final Function<Object, NameIndex> index;

        AliasSuggestions(
                ArgumentType<Object> original,
                SuggestionProvider<Object> provider,
                Function<Object, NameIndex> index) {
            this.original = original;
            this.provider = provider;
            this.index = index;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(
                CommandContext<Object> context, SuggestionsBuilder builder)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            CompletableFuture<Suggestions> nativeSuggestions =
                    provider == null
                            ? original.listSuggestions(context, builder)
                            : provider.getSuggestions(context, builder);
            if (NameIndex.reserved(builder.getRemaining()) && !builder.getRemaining().isEmpty())
                return nativeSuggestions;
            NameIndex names = index.apply(context.getSource());
            SuggestionsBuilder added =
                    new SuggestionsBuilder(builder.getInput(), builder.getStart());
            String query = NameIndex.key(builder.getRemaining().replaceFirst("^[\"']", ""));
            Set<String> hiddenAccounts = new HashSet<>();
            for (NameIndex.Identity identity : names.identities()) {
                if (!identity.visible()) continue;
                String preferred = names.preferred(identity);
                if (!preferred.equals(identity.account()))
                    hiddenAccounts.add(NameIndex.key(identity.account()));
                if (!NameIndex.key(preferred).startsWith(query)
                        && !NameIndex.key(identity.account()).startsWith(query)) continue;
                added.suggest(AliasArgumentType.suggestion(preferred, builder.getRemaining()));
            }
            Suggestions extra = added.build();
            return nativeSuggestions.thenApply(
                    nativeResult -> {
                        List<Suggestion> kept =
                                nativeResult.getList().stream()
                                        .filter(
                                                suggestion ->
                                                        !hiddenAccounts.contains(
                                                                NameIndex.key(
                                                                        suggestion
                                                                                .getText()
                                                                                .replaceAll(
                                                                                        "^\"|\"$",
                                                                                        ""))))
                                        .toList();
                        return Suggestions.merge(
                                builder.getInput(),
                                List.of(new Suggestions(nativeResult.getRange(), kept), extra));
                    });
        }
    }
}
