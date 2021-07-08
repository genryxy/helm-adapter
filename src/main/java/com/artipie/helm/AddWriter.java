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

import com.artipie.asto.ArtipieIOException;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.metadata.ParsedChartName;
import com.artipie.helm.metadata.YamlWriter;
import com.artipie.helm.misc.DateTimeNow;
import com.artipie.helm.misc.EmptyIndex;
import com.artipie.helm.misc.LineWriter;
import com.artipie.http.misc.TokenizerFlatProc;
import hu.akarnokd.rxjava2.interop.CompletableInterop;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Add writer of info about charts to index file.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle CyclomaticComplexityCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 * @checkstyle NPathComplexityCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.NPathComplexity"})
interface AddWriter {
    /**
     * Add info about charts to index. If index contains a chart with the same
     * version, the exception should be generated.
     * @param source Key to source index file
     * @param out Path to temporary file in which new index would be written
     * @param pckgs Packages collection which contains info about passed packages for
     *  adding to index file. There is a version and chart yaml for each package.
     * @return Result of completion
     */
    CompletionStage<Void> add(
        Key source,
        Path out,
        Map<String, Set<Pair<String, ChartYaml>>> pckgs
    );

    /**
     * Add info about charts to index. It does not make sense to check existence
     * of charts which are added because a new index file is generated.
     * It is important that archives from repo read one by one to avoid saving
     * everything at once to memory.
     * @param out Path to temporary file in which new index would be written
     * @param charts Collection of keys of archives with charts
     * @return Result of completion
     */
    CompletionStage<Void> addTrustfully(Path out, SortedSet<Key> charts);

    /**
     * Implementation of {@link AddWriter} for abstract storage.
     * @since 0.3
     */
    final class Asto implements AddWriter {
        /**
         * Versions.
         */
        static final String VRSNS = "version:";

        /**
         * Entries.
         */
        static final String ENTRS = "entries:";

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

        // @checkstyle NoJavadocForOverriddenMethodsCheck (15 lines)
        // @checkstyle JavadocParameterOrderCheck (15 lines)
        /**
         * It has the next implementation.
         * Read index file line by line. If we are in the `entries:` section, we will check
         * whether the line is a name of chart (e.g. line has correct indent and ends
         * with colon). It copy source index file line by line and if the line with
         * version is met, the existence of this version in packages would be checked
         * to avoid adding existed package. If the new name of chart is met, it will
         * write remained versions from packages. When we read next line after end of
         * `entries:` section from source index, we write info about remained charts
         * in packages.
         */
        @Override
        @SuppressWarnings("PMD.AssignmentInOperand")
        public CompletionStage<Void> add(
            final Key source,
            final Path out,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            return CompletableFuture.allOf().thenCompose(
                none -> {
                    try {
                        final BufferedWriter bufw = new BufferedWriter(
                            new OutputStreamWriter(Files.newOutputStream(out))
                        );
                        final TokenizerFlatProc target = new TokenizerFlatProc("\n");
                        final AtomicReference<String> name = new AtomicReference<>();
                        final AtomicBoolean entrs = new AtomicBoolean(false);
                        final AtomicReference<YamlWriter> writer = new AtomicReference<>();
                        writer.set(new YamlWriter(bufw, 2));
                        final AtomicReference<LineWriter> linewrtr = new AtomicReference<>();
                        linewrtr.set(new LineWriter(writer.get()));
                        return this.storage.exists(source)
                            .thenCompose(
                                exists -> {
                                    final CompletionStage<Content> res;
                                    if (exists) {
                                        res = this.storage.value(source);
                                    } else {
                                        res = CompletableFuture.completedFuture(
                                            new EmptyIndex().asContent()
                                        );
                                    }
                                    return res;
                                }
                            ).thenAccept(cont -> cont.subscribe(target))
                            .thenCompose(
                                noth -> Flowable.fromPublisher(target)
                                    .map(buf -> new String(new Remaining(buf).bytes()))
                                    .flatMapCompletable(
                                        line -> {
                                            final String trimmed = line.trim();
                                            final int lastposspace = lastPosOfSpaceInBegin(line);
                                            entrs.compareAndSet(false, trimmed.equals(Asto.ENTRS));
                                            if (entrs.get() && new ParsedChartName(line).valid()) {
                                                if (name.get() == null) {
                                                    writer.set(new YamlWriter(bufw, lastposspace));
                                                    linewrtr.set(new LineWriter(writer.get()));
                                                }
                                                if (lastposspace == writer.get().indent()) {
                                                    writeRemainedVersionsOfChart(
                                                        name.get(), pckgs, writer.get()
                                                    );
                                                    name.set(trimmed.replace(":", ""));
                                                }
                                            }
                                            if (entrs.get()) {
                                                throwIfVersionExists(trimmed, name.get(), pckgs);
                                            }
                                            if (entrs.get() && name.get() != null
                                                && lastposspace == 0) {
                                                writeRemainedVersionsOfChart(
                                                    name.get(), pckgs, writer.get()
                                                );
                                                writeRemainedChartsAfterCopyIndex(
                                                    pckgs, writer.get()
                                                );
                                                entrs.set(false);
                                            }
                                            linewrtr.get().writeAndReplaceTagGenerated(line);
                                            return Completable.complete();
                                        }
                                    ).to(CompletableInterop.await())
                                    .thenCompose(
                                        nothing -> {
                                            if (entrs.get()) {
                                                writeRemainedChartsAfterCopyIndex(
                                                    pckgs, writer.get()
                                                );
                                            }
                                            try {
                                                bufw.close();
                                            } catch (final IOException exc) {
                                                throw new ArtipieIOException(exc);
                                            }
                                            return CompletableFuture.allOf();
                                        }
                                    )
                            );
                    } catch (final IOException exc) {
                        throw new ArtipieIOException(exc);
                    }
                }
            );
        }

        @Override
        public CompletionStage<Void> addTrustfully(final Path out, final SortedSet<Key> charts) {
            return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        final BufferedWriter bufw = new BufferedWriter(
                            new OutputStreamWriter(Files.newOutputStream(out))
                        );
                        final YamlWriter writer = new YamlWriter(bufw, 2);
                        final String[] lines = new EmptyIndex().asString().split("\n");
                        for (final String line : lines) {
                            if (!line.isEmpty()) {
                                writer.writeLine(line, 0);
                            }
                        }
                        final CompletableFuture<Void> result = new CompletableFuture<>();
                        this.writeChartsToIndex(charts, writer).handle(
                            (noth, thr) -> {
                                if (thr == null) {
                                    result.complete(null);
                                } else {
                                    result.completeExceptionally(thr);
                                }
                                try {
                                    bufw.close();
                                } catch (final IOException exc) {
                                    throw new ArtipieIOException(exc);
                                }
                                return null;
                            }
                        );
                        return result;
                    } catch (final IOException exc) {
                        throw new ArtipieIOException(exc);
                    }
                }
            ).thenCompose(Function.identity());
        }

        /**
         * Write info about charts from archives to index file.
         * @param charts Collection of keys of archives with charts
         * @param writer Yaml writer
         * @return Result of completion.
         */
        private CompletableFuture<Void> writeChartsToIndex(
            final SortedSet<Key> charts, final YamlWriter writer
        ) {
            final AtomicReference<String> prev = new AtomicReference<>();
            CompletableFuture<Void> future = CompletableFuture.allOf();
            for (final Key key: charts) {
                future = future.thenCompose(
                    noth -> this.storage.value(key)
                        .thenApply(PublisherAs::new)
                        .thenCompose(PublisherAs::bytes)
                        .thenApply(TgzArchive::new)
                        .thenAccept(
                            tgz -> {
                                final Map<String, Object> fields;
                                fields = new HashMap<>(tgz.chartYaml().fields());
                                fields.putAll(tgz.metadata(Optional.empty()));
                                fields.put("created", new DateTimeNow().asString());
                                final String name = (String) fields.get("name");
                                try {
                                    if (!name.equals(prev.get())) {
                                        writer.writeLine(String.format("%s:", name), 1);
                                    }
                                    prev.set(name);
                                    writer.writeLine("-", 1);
                                    final String[] splitted = new ChartYaml(fields)
                                        .toString()
                                        .split("\n");
                                    for (final String line : splitted) {
                                        writer.writeLine(line, 2);
                                    }
                                } catch (final IOException exc) {
                                    throw new ArtipieIOException(exc);
                                }
                            }
                        )
                );
            }
            return future;
        }

        /**
         * Generates an exception if version of chart which contains in trimmed
         * line exists in packages.
         * @param trimmed Trimmed line from index file
         * @param name Name of chart
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         */
        private static void throwIfVersionExists(
            final String trimmed,
            final String name,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            if (trimmed.startsWith(Asto.VRSNS)) {
                final String vers = trimmed.replace(Asto.VRSNS, "").trim();
                if (pckgs.containsKey(name) && pckgs.get(name).stream().anyMatch(
                    pair -> pair.getLeft().equals(vers)
                )) {
                    throw new IllegalStateException(
                        String.format("Failed to write to index `%s` with version `%s`", name, vers)
                    );
                }
            }
        }

        /**
         * Write remained versions of passed chart in collection in case of their existence.
         * @param name Chart name for which remained versions are checked
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param writer Yaml writer
         * @throws IOException In case of exception during writing
         */
        private static void writeRemainedVersionsOfChart(
            final String name,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final YamlWriter writer
        ) throws IOException {
            if (name != null && pckgs.containsKey(name)) {
                for (final Pair<String, ChartYaml> pair : pckgs.get(name)) {
                    writer.writeLine("-", 2);
                    final String str = new IndexYamlMapping(pair.getRight().fields()).toString();
                    for (final String entry : str.split("[\\n\\r]+")) {
                        // @checkstyle MagicNumberCheck (1 line)
                        writer.writeLine(entry, 3);
                    }
                }
                pckgs.remove(name);
            }
        }

        /**
         * Write remained versions for all charts in collection in case of their existence.
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param writer Yaml writer
         */
        private static void writeRemainedChartsAfterCopyIndex(
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final YamlWriter writer
        ) {
            pckgs.forEach(
                (chart, pairs) -> {
                    try {
                        writer.writeLine(String.format("%s:", chart), 1);
                        for (final Pair<String, ChartYaml> pair : pairs) {
                            writer.writeLine("- ", 2);
                            final String yaml;
                            yaml = new IndexYamlMapping(pair.getRight().fields()).toString();
                            final String[] lines = yaml.split("[\\n\\r]+");
                            for (final String line : lines) {
                                // @checkstyle MagicNumberCheck (1 line)
                                writer.writeLine(line, 3);
                            }
                        }
                    } catch (final IOException exc) {
                        throw  new ArtipieIOException(exc);
                    }
                }
            );
            pckgs.clear();
        }

        /**
         * Obtains last position of space from beginning before meeting any character.
         * @param line Text line
         * @return Last position of space from beginning before meeting any character.
         */
        private static int lastPosOfSpaceInBegin(final String line) {
            return line.length() - line.replaceAll("^\\s*", "").length();
        }
    }
}
