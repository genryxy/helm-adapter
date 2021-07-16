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
import com.artipie.helm.misc.DateTimeNow;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates logic for obtaining some meta info for charts from
 * chart yaml file from tgz archive.
 * @since 0.3
 */
interface Charts {
    /**
     * Obtains versions by chart names from storage for passed keys.
     * @param charts Charts for which versions should be obtained
     * @return Versions by chart names
     */
    CompletionStage<Map<String, Set<String>>> versionsFor(Collection<Key> charts);

    /**
     * Obtains versions and chart yaml content by chart names from storage for passed keys.
     * @param charts Charts for which versions should be obtained
     * @return Versions and chart yaml content by chart names
     */
    CompletionStage<Map<String, Set<Pair<String, ChartYaml>>>> versionsAndYamlFor(
        Collection<Key> charts
    );

    /**
     * Implementation of {@link Charts} for abstract storage.
     * @since 0.3
     */
    final class Asto implements Charts {
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
        public CompletionStage<Map<String, Set<String>>> versionsFor(final Collection<Key> charts) {
            final Map<String, Set<String>> pckgs = new ConcurrentHashMap<>();
            return CompletableFuture.allOf(
                charts.stream().map(
                    key -> this.storage.value(key)
                        .thenApply(PublisherAs::new)
                        .thenCompose(PublisherAs::bytes)
                        .thenApply(TgzArchive::new)
                        .thenAccept(
                            tgz -> {
                                final ChartYaml chart = tgz.chartYaml();
                                pckgs.putIfAbsent(chart.name(), new HashSet<>());
                                pckgs.get(chart.name()).add(chart.version());
                            }
                        )
                ).toArray(CompletableFuture[]::new)
            ).thenApply(noth -> pckgs);
        }

        @Override
        public CompletionStage<Map<String, Set<Pair<String, ChartYaml>>>> versionsAndYamlFor(
            final Collection<Key> charts
        ) {
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs = new ConcurrentHashMap<>();
            return CompletableFuture.allOf(
                charts.stream().map(
                    key -> this.storage.value(key)
                        .thenApply(PublisherAs::new)
                        .thenCompose(PublisherAs::bytes)
                        .thenApply(TgzArchive::new)
                        .thenAccept(tgz -> Charts.Asto.addChartFromTgzToPackages(tgz, pckgs))
                ).toArray(CompletableFuture[]::new)
            ).thenApply(noth -> pckgs);
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
            pckgs.putIfAbsent(name, ConcurrentHashMap.newKeySet());
            pckgs.get(name).add(
                new ImmutablePair<>(chart.version(), chart)
            );
        }
    }
}
