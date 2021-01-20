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
package com.artipie.helm.metadata;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.ChartYaml;
import com.artipie.helm.TgzArchive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generate `index.yaml` file  given a directory containing packaged charts.
 * See <a href="https://v2.helm.sh/docs/helm/#helm-repo-index">repo index</a>.
 * @since 0.2
 */
public class IndexByDirectory {
    /**
     * Storage.
     */
    private final Storage storage;

    /**
     * Directory.
     */
    private final Key directory;

    /**
     * Ctor.
     * @param storage Storage
     * @param directory Directory from which packaged charts are taken
     */
    public IndexByDirectory(final Storage storage, final Key directory) {
        this.storage = storage;
        this.directory = directory;
    }

    /**
     * Obtains generated `index.yaml`.
     * @return Bytes of generated `index.yaml`, empty in case of absence of packaged charts.
     */
    public CompletionStage<Optional<byte[]>> value() {
        final Map<String, List<Object>> entries = new ConcurrentHashMap<>();
        return this.storage.list(this.directory).thenCompose(
            keys -> CompletableFuture.allOf(
                keys.stream()
                    .map(key -> key.string().substring(this.directory.string().length() + 1))
                    .map(key -> key.split("/"))
                    .filter(parts -> parts.length == 1 && parts[0].endsWith(".tgz"))
                    .map(parts -> parts[0])
                    .map(
                        key -> this.storage.value(
                            new Key.From(this.directory, key)
                        ).thenApply(PublisherAs::new)
                        .thenCompose(PublisherAs::bytes)
                        .thenApply(TgzArchive::new)
                        .thenApply(TgzArchive::chartYaml)
                        .thenAcceptBoth(
                            this.index(),
                            (chart, index) -> IndexByDirectory.addEntry(chart, index, entries)
                        )
                    ).toArray(CompletableFuture[]::new)
            ).thenApply(nothing -> new IndexYamlMapping())
            .thenApply(index -> index.addEntries(entries))
            .thenApply(IndexYamlMapping::toBytes)
        );
    }

    /**
     * Obtains content of `index.yaml` which is generated automatically.
     * @return Mapping for fields from `index.yaml` file.
     */
    private CompletionStage<IndexYamlMapping> index() {
        return this.storage.list(Key.ROOT)
            .thenCompose(
                keys -> this.storage.value(
                    keys.stream()
                        .filter(key -> key.string().endsWith(IndexYaml.INDEX_YAML.string()))
                        .findFirst()
                        .orElseThrow(
                            () -> new IllegalStateException("'index.yaml' was not found in storage")
                        )
                ).thenApply(PublisherAs::new)
                .thenCompose(PublisherAs::asciiString)
            )
        .thenApply(IndexYamlMapping::new);
    }

    /**
     * Add entry to the existing entries.
     * @param chart Chart yaml
     * @param index Mapping for fields from `index.yaml` file
     * @param entries Existing entries
     */
    private static void addEntry(
        final ChartYaml chart,
        final IndexYamlMapping index,
        final Map<String, List<Object>> entries
    ) {
        synchronized (entries) {
            final String name = chart.name();
            final Map<String, Object> fromidx = index.entriesByChart(name).stream()
                .filter(entry -> entry.get("version").equals(chart.version()))
                .findFirst()
                .orElseThrow(
                    () -> new IllegalStateException(
                        String.format(
                            "'index.yaml' does not contain `%s-%s`", name, chart.version()
                        )
                    )
                );
            if (entries.containsKey(name)) {
                entries.get(name).add(fromidx);
            } else {
                entries.put(name, new ArrayList<>(Collections.singletonList(fromidx)));
            }
        }
    }
}
