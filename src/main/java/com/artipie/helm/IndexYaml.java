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

import com.artipie.asto.Concatenation;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.asto.rx.RxStorage;
import com.artipie.asto.rx.RxStorageWrapper;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Index.yaml file. The main file in a chart repo.
 *
 * @todo #1:30min Ensure that generation work
 *  In order to check the file generation works fine, we need to create a test, which will install
 *  pushed before chart and ensure successful installation.
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 * @checkstyle NonStaticMethodCheck (500 lines)
 */
@SuppressWarnings({"PMD.UnusedFormalParameter",
    "PMD.UnusedPrivateField",
    "PMD.ArrayIsStoredDirectly",
    "PMD.UnusedFormalParameter",
    "PMD.AvoidDuplicateLiterals",
    "PMD.SingularField"})
final class IndexYaml {

    /**
     * The index.yalm string.
     */
    private static final Key INDEX_YAML = new Key.From("index.yaml");

    /**
     * An example of time this formatter produces: 2016-10-06T16:23:20.499814565-06:00 .
     */
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnnZZZZZ");

    /**
     * The storage.
     */
    private final Storage storage;

    /**
     * The base path for urls field.
     */
    private final String base;

    /**
     * Ctor.
     * @param storage The storage.
     * @param base The base path for urls field.
     */
    IndexYaml(final Storage storage, final String base) {
        this.storage = storage;
        this.base = base;
    }

    /**
     * Update the index file.
     * @param arch New archive in a repo for which metadata is missing.
     * @return The operation result
     */
    public Completable update(final TgzArchive arch) {
        final RxStorage rxs = new RxStorageWrapper(this.storage);
        return rxs.exists(IndexYaml.INDEX_YAML)
            .flatMap(
                exist -> {
                    final Single<Map<String, Object>> result;
                    if (exist) {
                        result = rxs.value(IndexYaml.INDEX_YAML)
                            .flatMap(content -> new Concatenation(content).single())
                            .map(buf -> new String(new Remaining(buf).bytes()))
                            .map(content -> new Yaml().load(content));
                    } else {
                        result = Single.just(IndexYaml.empty());
                    }
                    return result;
                })
            .map(
                idx -> {
                    this.update(idx, arch);
                    return idx;
                })
            .flatMapCompletable(
                idx -> {
                    final DumperOptions options = new DumperOptions();
                    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                    options.setPrettyFlow(true);
                    return rxs.save(
                        IndexYaml.INDEX_YAML,
                        new Content.From(
                            new Yaml(options).dump(idx).getBytes(StandardCharsets.UTF_8)
                        )
                    );
                }
            );
    }

    /**
     * Return an empty Index mappings.
     * @return The empty yaml mappings.
     */
    private static Map<String, Object> empty() {
        final Map<String, Object> res = new HashMap<>(3);
        res.put("apiVersion", "v1");
        res.put("entries", new HashMap<String, Object>(0));
        res.put("generated", ZonedDateTime.now().format(IndexYaml.TIME_FORMATTER));
        return res;
    }

    /**
     * Perform an update.
     * @param index The index yaml mappings.
     * @param tgz The archive.
     */
    @SuppressWarnings("unchecked")
    private void update(final Map<String, Object> index, final TgzArchive tgz) {
        final ChartYaml chart = tgz.chartYaml();
        final String version = "version";
        final Map<String, Object> entries = (Map<String, Object>) index.get("entries");
        final String name = (String) chart.field("name");
        if (!entries.containsKey(name)) {
            entries.put(name, new ArrayList<Map<String, Object>>(0));
        }
        final ArrayList<Map<String, Object>> versions = (ArrayList<Map<String, Object>>)
            entries.get(name);
        if (versions.stream().noneMatch(map -> map.get(version).equals(chart.field(version)))) {
            final Map<String, Object> newver = new HashMap<>();
            newver.put("created", ZonedDateTime.now().format(IndexYaml.TIME_FORMATTER));
            newver.put(
                "urls",
                new ArrayList<>(
                    Collections.singleton(String.format("%s%s", this.base, tgz.name()))
                )
            );
            newver.put("digest", tgz.digest());
            newver.putAll(chart.fields());
            // @todo #32:30min Create a unit test for digest field
            //  One of the fields Index.yaml require is "digest" field. The test should make verify
            //  that field has been generated correctly.
            // @todo #32:30min Create a unit test for urls field
            //  One of the fields Index.yaml require is "urls" field. The test should make verify
            //  that field has been generated correctly.
            versions.add(newver);
        }
    }
}
