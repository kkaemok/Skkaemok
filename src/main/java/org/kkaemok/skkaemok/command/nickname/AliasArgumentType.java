package org.kkaemok.skkaemok.command.nickname;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Returns the original native result type and retains ranges in the user's original input. */
public final class AliasArgumentType<T> implements ArgumentType<T> {
    final ArgumentType<T> delegate;
    private final BiFunction<String, Object, String> alias;

    public AliasArgumentType(ArgumentType<T> delegate, Function<String, String> alias) {
        this(delegate, (input, source) -> alias.apply(input));
    }

    public AliasArgumentType(ArgumentType<T> delegate, BiFunction<String, Object, String> alias) {
        this.delegate = delegate;
        this.alias = alias;
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(StringReader reader) throws CommandSyntaxException;
    }

    @Override
    public T parse(StringReader reader) throws CommandSyntaxException {
        return parseAlias(reader, null, delegate::parse);
    }

    @Override
    public <S> T parse(StringReader reader, S source) throws CommandSyntaxException {
        return parseAlias(reader, source, next -> delegate.parse(next, source));
    }

    private T parseAlias(StringReader reader, Object source, Parser<T> parse)
            throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() == '@') return parse.parse(reader);
        StringReader probe = new StringReader(reader);
        String input;
        if (probe.peek() == '"' || probe.peek() == '\'') input = probe.readString();
        else {
            int start = probe.getCursor();
            while (probe.canRead() && probe.peek() != ' ') probe.skip();
            input = probe.getString().substring(start, probe.getCursor());
        }
        String account = alias.apply(input, source);
        if (account == null) return parse.parse(reader);
        T result = parse.parse(new StringReader(account));
        reader.setCursor(probe.getCursor());
        return result;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context, SuggestionsBuilder builder) {
        return delegate.listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return delegate.getExamples();
    }

    static String suggestion(String name, String remaining) {
        boolean quote =
                remaining.startsWith("\"")
                        || remaining.startsWith("'")
                        || name.chars()
                                .anyMatch(c -> !StringReader.isAllowedInUnquotedString((char) c));
        return quote ? "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : name;
    }
}
