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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.ChartYaml;
import com.artipie.helm.TgzArchive;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
     * Base url.
     */
    private final String baseurl;

    /**
     * Ctor.
     * @param storage Storage
     * @param directory Directory from which packaged charts are taken
     * @param baseurl Absolute url to the charts
     */
    public IndexByDirectory(final Storage storage, final Key directory,
        final Optional<String> baseurl) {
        this.storage = storage;
        this.directory = directory;
        this.baseurl = baseurl.orElse("");
    }

    /**
     * Obtains generated `index.yaml`.
     * @return Bytes of generated `index.yaml`, empty in case of absence of packaged charts.
     */
    public CompletionStage<Optional<Content>> value() {
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
                        .thenAccept(tgz -> this.addEntry(tgz, entries))
                    ).toArray(CompletableFuture[]::new)
            ).thenApply(nothing -> new IndexYamlMapping())
            .thenApply(index -> index.addEntries(entries))
            .thenApply(IndexYamlMapping::toContent)
        );
    }

    /**
     * Add entry to the existing entries.
     * @param tgz Tgz archive
     * @param entries Existing entries
     * @todo #90:60min Extract logic for creating a new version of chart for `index.yaml`.
     *  In this method and in `IndexYaml#update` the same logic for generating
     *  additional fields is used. It is necessary to extract this logic or
     *  to redesign `IndexYaml` class by removing interaction with storage
     *  or something else.
     */
    private void addEntry(final TgzArchive tgz, final Map<String, List<Object>> entries) {
        final ChartYaml chart = tgz.chartYaml();
        final Map<String, Object> newvers = new HashMap<>(chart.fields());
        newvers.put("digest", tgz.digest());
        newvers.put(
            "urls",
            new ArrayList<>(
                Collections.singleton(String.format("%s%s", this.baseurl, tgz.name()))
            )
        );
        newvers.put("created", ZonedDateTime.now().format(IndexYaml.TIME_FORMATTER));
        synchronized (entries) {
            final String name = chart.name();
            if (entries.containsKey(name)) {
                entries.get(name).add(newvers);
            } else {
                entries.put(name, new ArrayList<>(Collections.singletonList(newvers)));
            }
        }
    }
}
