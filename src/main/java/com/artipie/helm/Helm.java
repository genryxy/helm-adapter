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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.fs.FileStorage;
import com.artipie.helm.metadata.Index;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.misc.DateTimeNow;
import io.vertx.core.impl.ConcurrentHashSet;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Helm repository.
 * @since 0.3
 * @todo #109:30min Refactor Helm class.
 *  Now this class is too big, therefore it should be refactored
 *  by extracting some functionality. Probably to extract some classes which
 *  would be responsible for writing info about charts to index file.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle CyclomaticComplexityCheck (500 lines)
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
     * @return Result of completion
     */
    CompletionStage<Void> add(Collection<Key> charts);

    /**
     * Implementation of {@link Helm} for abstract storage.
     * @since 0.3
     */
    @SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.AvoidDeeplyNestedIfStmts"})
    final class Asto implements Helm {
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

        @Override
        public CompletionStage<Void> batchUpdate(final Key prefix) {
            throw new NotImplementedException("not implemented yet");
        }

        @Override
        public CompletionStage<Void> add(final Collection<Key> charts) {
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs = new ConcurrentHashMap<>();
            return CompletableFuture.allOf(
                charts.stream().map(
                    key -> this.storage.value(key)
                        .thenApply(PublisherAs::new)
                        .thenCompose(PublisherAs::bytes)
                        .thenApply(TgzArchive::new)
                        .thenAccept(tgz -> Asto.addChartFromTgzToPackages(tgz, pckgs))
                ).toArray(CompletableFuture[]::new)
            ).thenCompose(
                nothing -> {
                    try {
                        final String prefix = "index-";
                        final Path tmp = Files.createTempDirectory(prefix);
                        final Path file = Files.createTempFile(tmp, prefix, ".yaml");
                        final Storage tmpstrg = new FileStorage(tmp);
                        final Key tmpidx = new Key.From(file.getFileName().toString());
                        return this.storage.value(IndexYaml.INDEX_YAML)
                            .thenCompose(
                                cont -> tmpstrg.save(tmpidx, cont)
                            ).thenCompose(noth -> this.addChartsToIndex(file, pckgs))
                            .thenCompose(noth -> tmpstrg.value(tmpidx));
                    } catch (final IOException exc) {
                        throw new UncheckedIOException(exc);
                    }
                }
            ).thenCompose(index -> this.storage.save(IndexYaml.INDEX_YAML, index));
        }

        /**
         * Add info about charts to index. If index contains a chart with the same
         * version, the exception will be generated. It has the next implementation.
         * Read index file line by line. If we are in the `entries:` section, we will check
         * whether the line is a name of chart (e.g. line has correct indent and ends
         * with colon). It copy source index file line by line and if the line with
         * version is met, the existence of this version in packages would be checked
         * to avoid adding existed package. If the new name of chart is met, it will
         * write remained versions from packages. When we read next line after end of
         * `entries:` section from source index, we write info about remained charts
         * in packages.
         * @param file Path to temporary file with index
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @return Result of completion
         * @checkstyle NestedIfDepthCheck (70 lines)
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private CompletionStage<Void> addChartsToIndex(
            final Path file,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            return new Index.WithBreaks(this.storage)
                .versionsByPackages()
                .thenCompose(
                    vrsns -> {
                        final Path tmp = Paths.get(UUID.randomUUID().toString());
                        try (
                            BufferedReader br = new BufferedReader(
                                new InputStreamReader(Files.newInputStream(file))
                            );
                            BufferedWriter bufw = new BufferedWriter(
                                new OutputStreamWriter(Files.newOutputStream(tmp))
                            )
                        ) {
                            String line;
                            boolean entrs = false;
                            String name = null;
                            int indent = 2;
                            while ((line = br.readLine()) != null) {
                                final String trimmed = line.trim();
                                if (!entrs) {
                                    entrs = trimmed.equals(Asto.ENTRS);
                                }
                                if (entrs && trimmed.endsWith(":")
                                    && !trimmed.equals(Asto.ENTRS)
                                ) {
                                    if (name == null) {
                                        indent = Asto.lastPosOfSpaceInBegin(line);
                                    }
                                    if (Asto.lastPosOfSpaceInBegin(line) == indent) {
                                        if (name != null) {
                                            Asto.writeRemainedVersionsOfChartIfExist(
                                                indent, name, pckgs, bufw
                                            );
                                        }
                                        name = trimmed.replace(":", "");
                                    }
                                }
                                if (entrs) {
                                    Asto.throwIfVersionExists(trimmed, name, pckgs);
                                }
                                if (entrs && name != null
                                    && Asto.lastPosOfSpaceInBegin(line) == 0
                                ) {
                                    if (pckgs.containsKey(name)) {
                                        Asto.writeRemainedVersionsOfChartIfExist(
                                            indent, name, pckgs, bufw
                                        );
                                    }
                                    Asto.writeRemainedChartsAfterCopyIndex(indent, pckgs, bufw);
                                    entrs = false;
                                }
                                bufw.write(line);
                                bufw.newLine();
                            }
                        } catch (final IOException exc) {
                            throw new UncheckedIOException(exc);
                        }
                        return CompletableFuture.allOf();
                    }
                );
        }

        /**
         * Add chart from tgz archive to packages collection.
         * @param tgz Tgz archive with chart yaml file
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         */
        private static void addChartFromTgzToPackages(
            final TgzArchive tgz,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            final Map<String, Object> fields = new HashMap<>(tgz.chartYaml().fields());
            fields.putAll(tgz.metadata(Optional.empty()));
            fields.put("created", new DateTimeNow().asString());
            final ChartYaml chart = new ChartYaml(fields);
            final String name = chart.name();
            pckgs.putIfAbsent(name, new ConcurrentHashSet<>());
            pckgs.get(name).add(
                new ImmutablePair<>(chart.version(), chart)
            );
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
         * @param indent Required indent
         * @param name Chart name for which remained versions are checked
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param bufw Buffered writer
         * @throws IOException In case of exception during writing
         * @checkstyle ParameterNumberCheck (7 lines)
         */
        private static void writeRemainedVersionsOfChartIfExist(
            final int indent,
            final String name,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final BufferedWriter bufw
        ) throws IOException {
            for (final Pair<String, ChartYaml> pair : pckgs.get(name)) {
                final String prefix = StringUtils.repeat(' ', indent * 3);
                final String str;
                str = new IndexYamlMapping(pair.getRight().fields()).toString();
                bufw.write(String.format("%s-", StringUtils.repeat(' ', indent * 2)));
                bufw.newLine();
                for (final String entry : str.split("[\\n\\r]+")) {
                    bufw.write(String.format("%s%s", prefix, entry));
                    bufw.newLine();
                }
            }
            pckgs.remove(name);
        }

        /**
         * Write remained versions for all charts in collection in case of their existence.
         * @param indent Required indent
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param bufw Buffered writer
         */
        private static void writeRemainedChartsAfterCopyIndex(
            final int indent,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final BufferedWriter bufw
        ) {
            final char space = ' ';
            pckgs.forEach(
                (chart, pairs) -> {
                    try {
                        bufw.write(
                            String.format("%s%s:", StringUtils.repeat(space, indent), chart)
                        );
                        bufw.newLine();
                        for (final Pair<String, ChartYaml> pair : pairs) {
                            final String prefix = StringUtils.repeat(space, indent * 3);
                            bufw.write(
                                String.format("%s-", StringUtils.repeat(space, indent * 2))
                            );
                            bufw.newLine();
                            final String yaml;
                            yaml = new IndexYamlMapping(pair.getRight().fields()).toString();
                            final String[] lines = yaml.split("[\\n\\r]+");
                            for (final String line : lines) {
                                bufw.write(String.format("%s%s", prefix, line));
                                bufw.newLine();
                            }
                        }
                    } catch (final IOException exc) {
                        throw  new UncheckedIOException(exc);
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
