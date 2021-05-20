package com.artipie.helm;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Simple implementation of Storage that holds all data in memory.
 * It does not really delete item from storage. Just marks in map
 * that a specified item was removed.
 *
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class BenchStorage implements Storage {
    /**
     * Provided storage implementation.
     */
    private final Storage storage;

    /**
     * Storage content. If value is true, the key exists. Otherwise the key does not exist.
     */
    private final Map<Key, Boolean> items;

    /**
     * Ctor.
     */
    public BenchStorage() {
        this(new InMemoryStorage());
    }

    /**
     * Ctor.
     * @param storage Storage.
     */
    public BenchStorage(final Storage storage) {
        this.storage = storage;
        this.items = new HashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> exists(final Key key) {
        return CompletableFuture.supplyAsync(
            () -> {
                synchronized (this.items) {
                    return this.items.containsKey(key) && this.items.get(key);
                }
            }
        );
    }

    @Override
    public CompletableFuture<Collection<Key>> list(final Key prefix) {
        synchronized (this.storage) {
            return this.storage.list(prefix);
        }
    }

    @Override
    public CompletableFuture<Void> save(final Key key, final Content content) {
        synchronized (this.storage) {
            return this.storage.save(key, content)
                .thenCompose(
                    noth -> {
                        synchronized (this.items) {
                            this.items.put(key, true);
                            return CompletableFuture.allOf();
                        }
                    }
                );
        }
    }

    @Override
    public CompletableFuture<Void> move(final Key source, final Key destination) {
        return CompletableFuture.supplyAsync(
            () -> {
                synchronized (this.items) {
                    if (this.items.containsKey(source) && this.items.get(source)) {
                        synchronized (this.storage) {
                            return this.storage.move(source, destination);
                        }
                    }
                    throw new IllegalArgumentException(
                        String.format("No value for source key: %s", source.string())
                    );
                }
            }
        ).thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<Long> size(final Key key) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public CompletableFuture<Content> value(final Key key) {
        synchronized (this.storage) {
            return this.storage.value(key);
        }
    }

    @Override
    public CompletableFuture<Void> delete(final Key key) {
        return CompletableFuture.runAsync(
            () -> {
                synchronized (this.items) {
                    if (!this.items.containsKey(key)) {
                        throw new IllegalArgumentException(
                            String.format("Key does not exist: %s", key.string())
                        );
                    }
                    this.items.put(key, false);
                }
            }
        );
    }

    @Override
    public <T> CompletionStage<T> exclusively(
        final Key key,
        final Function<Storage, CompletionStage<T>> operation
    ) {
        throw new NotImplementedException("not implemented");
    }

    public CompletionStage<Void> reset() {
        return CompletableFuture.runAsync(
            () -> {
                synchronized (this.items) {
                    this.items.keySet().forEach(
                        key -> this.items.put(key, true)
                    );
                }
            }
        );
    }
}
