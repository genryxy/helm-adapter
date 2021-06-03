/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.helm;

import com.artipie.ArtipieException;
import com.artipie.asto.ArtipieIOException;
import com.artipie.asto.Content;
import com.artipie.asto.Copy;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.misc.EmptyIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.cactoos.list.ListOf;

/**
 * Helm repository.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 */
public interface Helm {
    /**
     * Batch update of Helm files for repository.
     * @param prefix Repository prefix
     * @return Result of completion
     */
    CompletionStage<Void> batchUpdate(Key prefix);

    /**
     * Add info to index about charts. Suppose that these charts don't exist in
     * index file, but in any case it checks the existence of
     * passed charts. In case of existence info about them in index
     * file an exception would be thrown.
     * @param charts Keys for charts which should be added to index file
     * @param indexpath Path to index file
     * @return Result of completion
     */
    CompletionStage<Void> add(Collection<Key> charts, Key indexpath);

    /**
     * Remove info from index about charts.
     * @param charts Keys for charts which should be removed from index file. These keys
     *  should start with specified prefix
     * @param indexpath Path to index file
     * @return Result of completion
     */
    CompletionStage<Void> delete(Collection<Key> charts, Key indexpath);

    /**
     * Implementation of {@link Helm} for abstract storage.
     * @since 0.3
     */
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    final class Asto implements Helm {
        /**
         * Storage.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param storage Storage
         */
        Asto(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public CompletionStage<Void> batchUpdate(final Key prefix) {
            throw new NotImplementedException("not implemented yet");
        }

        @Override
        public CompletionStage<Void> add(final Collection<Key> charts, final Key indexpath) {
            final AtomicReference<Key> outidx = new AtomicReference<>();
            final AtomicReference<Path> dir = new AtomicReference<>();
            final Key keyidx = new Key.From(indexpath, IndexYaml.INDEX_YAML);
            final CompletableFuture<Void> result = new CompletableFuture<>();
            CompletableFuture.runAsync(
                () -> throwIfKeysInvalid(charts, indexpath)
            ).thenCompose(
                nothing -> new Charts.Asto(this.storage)
                    .versionsAndYamlFor(charts)
                    .thenCompose(
                        pckgs -> {
                            try {
                                final String prfx = "index-";
                                dir.set(Files.createTempDirectory(prfx));
                                final Path source = Files.createTempFile(dir.get(), prfx, ".yaml");
                                final Path out = Files.createTempFile(dir.get(), prfx, "-out.yaml");
                                final Storage tmpstrg = new FileStorage(dir.get());
                                outidx.set(new Key.From(out.getFileName().toString()));
                                return this.storage.exists(keyidx)
                                    .thenCompose(
                                        exists -> {
                                            final CompletionStage<Content> res;
                                            if (exists) {
                                                res = this.storage.value(keyidx);
                                            } else {
                                                res = CompletableFuture.completedFuture(
                                                    new EmptyIndex().asContent()
                                                );
                                            }
                                            return res;
                                        }
                                ).thenCompose(
                                    cont -> tmpstrg.save(
                                        new Key.From(source.getFileName().toString()), cont
                                    )
                                ).thenApply(noth -> new AddWriter.Asto(tmpstrg))
                                .thenCompose(writer -> writer.add(source, out, pckgs))
                                .thenCompose(
                                    noth -> this.moveFromTempStorageAndDelete(
                                        tmpstrg, outidx.get(), dir.get(), keyidx
                                    )
                                ).handle(
                                    (noth, thr) -> {
                                        if (thr != null) {
                                            FileUtils.deleteQuietly(out.getParent().toFile());
                                            result.completeExceptionally(thr);
                                        }
                                        return null;
                                    }
                                );
                            } catch (final IOException exc) {
                                throw new ArtipieIOException(exc);
                            }
                        }
                    )
            ).handle(
                (noth, thr) -> {
                    if (thr == null) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(thr);
                    }
                    return null;
                }
            );
            return result;
        }

        @Override
        public CompletionStage<Void> delete(final Collection<Key> charts, final Key indexpath) {
            final CompletionStage<Void> res;
            if (charts.isEmpty()) {
                res = CompletableFuture.allOf();
            } else {
                final AtomicReference<Path> dir = new AtomicReference<>();
                final Key keyidx = new Key.From(indexpath, IndexYaml.INDEX_YAML);
                res = this.storage.exists(keyidx)
                    .thenCompose(
                        exists -> {
                            throwIfKeysInvalid(charts, indexpath);
                            if (exists) {
                                try {
                                    final String prfx = "index-";
                                    final AtomicReference<Key> outidx = new AtomicReference<>();
                                    final AtomicReference<Path> src = new AtomicReference<>();
                                    final AtomicReference<Path> out = new AtomicReference<>();
                                    final AtomicReference<Storage> tmpstrg;
                                    tmpstrg = new AtomicReference<>();
                                    final CompletableFuture<Void> rslt = new CompletableFuture<>();
                                    this.checkAllChartsExistence(charts)
                                        .thenAccept(
                                            noth -> {
                                                try {
                                                    dir.set(Files.createTempDirectory(prfx));
                                                    // @checkstyle LineLengthCheck (2 lines)
                                                    src.set(Files.createTempFile(dir.get(), prfx, ".yaml"));
                                                    out.set(Files.createTempFile(dir.get(), prfx, "-out.yaml"));
                                                } catch (final IOException exc) {
                                                    throw new ArtipieIOException(exc);
                                                }
                                                tmpstrg.set(new FileStorage(dir.get()));
                                                outidx.set(
                                                    new Key.From(out.get().getFileName().toString())
                                                );
                                            }
                                        )
                                        .thenCompose(nothing -> this.storage.value(keyidx))
                                        .thenCompose(
                                            cont -> tmpstrg.get().save(
                                                new Key.From(src.get().getFileName().toString()),
                                                cont
                                            )
                                        ).thenCombine(
                                            new Charts.Asto(this.storage).versionsFor(charts),
                                            (noth, fromidx) -> new RemoveWriter.Asto(tmpstrg.get())
                                                .delete(src.get(), out.get(), fromidx)
                                        ).thenCompose(Function.identity())
                                        .thenCompose(
                                            noth -> this.moveFromTempStorageAndDelete(
                                                tmpstrg.get(), outidx.get(), dir.get(), keyidx
                                            )
                                        ).thenCompose(
                                            noth -> CompletableFuture.allOf(
                                                charts.stream()
                                                    .map(this.storage::delete)
                                                    .toArray(CompletableFuture[]::new)
                                            )
                                        ).handle(
                                            (noth, thr) -> {
                                                // @checkstyle NestedIfDepthCheck (10 lines)
                                                if (thr == null) {
                                                    rslt.complete(null);
                                                } else {
                                                    if (out.get() != null) {
                                                        FileUtils.deleteQuietly(
                                                            out.get().getParent().toFile()
                                                        );
                                                    }
                                                    rslt.completeExceptionally(thr);
                                                }
                                                return null;
                                            }
                                        );
                                    return rslt;
                                } catch (final IllegalStateException exc) {
                                    FileUtils.deleteQuietly(dir.get().toFile());
                                    throw new ArtipieException(exc);
                                }
                            } else {
                                throw new ArtipieException(
                                    "Failed to delete packages as index does not exist"
                                );
                            }
                        }
                    );
            }
            return res;
        }

        /**
         * Checks that keys for all charts exist in storage. In case of absence
         * one of them an exception will be thrown.
         * @param charts Charts of which existence should be checked
         * @return Result of completion
         */
        private CompletableFuture<Void> checkAllChartsExistence(final Collection<Key> charts) {
            final List<CompletionStage<Boolean>> futures = charts.stream()
                .map(this.storage::exists)
                .collect(Collectors.toList());
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenCompose(
                    nothing -> {
                        if (futures.stream().anyMatch(
                            res -> !res.toCompletableFuture().join().equals(true)
                        )) {
                            throw new ArtipieException(
                                new IllegalStateException(
                                    "Some of keys for deletion are absent in storage"
                                )
                            );
                        }
                        return CompletableFuture.allOf();
                    }
                );
        }

        /**
         * Moves index file from temporary storage to real and deletes this file
         * from temporary storage.
         * @param tmpstrg Temporary storage with index file
         * @param outidx Key to index file in temporary storage
         * @param tmpdir Temporary directory
         * @param idxtarget Target key to index file in source storage
         * @return Result of completion
         * @checkstyle ParameterNumberCheck (7 lines)
         */
        private CompletionStage<Void> moveFromTempStorageAndDelete(
            final Storage tmpstrg,
            final Key outidx,
            final Path tmpdir,
            final Key idxtarget
        ) {
            return new Copy(tmpstrg, new ListOf<>(outidx)).copy(this.storage)
                .thenCompose(noth -> this.storage.move(outidx, idxtarget))
                .thenApply(noth -> FileUtils.deleteQuietly(tmpdir.toFile()))
                .thenCompose(ignore -> CompletableFuture.allOf());
        }

        /**
         * Checks that all keys from collection start with specified prefix.
         * Otherwise an exception will be thrown.
         * @param keys Keys of archives with charts
         * @param prefix Prefix which is required for all keys
         */
        private static void throwIfKeysInvalid(final Collection<Key> keys, final Key prefix) {
            keys.forEach(
                key -> {
                    if (!key.string().startsWith(prefix.string())) {
                        throw new ArtipieException(
                            new IllegalStateException(
                                String.format(
                                    "Key `%s` does not start with prefix `%s`",
                                    key.string(),
                                    prefix.string()
                                )
                            )
                        );
                    }
                }
            );
        }
    }
}
