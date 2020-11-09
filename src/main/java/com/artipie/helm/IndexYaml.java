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

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlMappingBuilder;
import com.amihaiemil.eoyaml.YamlNode;
import com.amihaiemil.eoyaml.YamlSequenceBuilder;
import com.artipie.asto.Concatenation;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.asto.rx.RxStorage;
import com.artipie.asto.rx.RxStorageWrapper;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Index.yaml file. The main file in a chart repo.
 *
 * @since 0.2
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
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
                    final Single<YamlMapping> result;
                    if (exist) {
                        result = rxs.value(IndexYaml.INDEX_YAML)
                            .flatMap(content -> new Concatenation(content).single())
                            .map(buf -> new String(new Remaining(buf).bytes()))
                            .map(content -> Yaml.createYamlInput(content).readYamlMapping());
                    } else {
                        result = Single.just(IndexYaml.empty());
                    }
                    return result;
                })
            .map(idx -> this.update(idx, arch))
            .flatMapCompletable(
                idx -> rxs.save(
                    IndexYaml.INDEX_YAML,
                    new Content.From(idx.toString().getBytes())
                )
            );
    }

    /**
     * Return an empty Index mappings.
     * @return The empty yaml mappings.
     */
    private static YamlMapping empty() {
        return Yaml.createYamlMappingBuilder()
            .add("apiVersion", "v1")
            .add("generated", ZonedDateTime.now().format(IndexYaml.TIME_FORMATTER))
            .build();
    }

    /**
     * Perform an update.
     * @param index The index yaml mappings.
     * @param tgz The archive.
     * @return Yaml mapping with updates.
     */
    private YamlMapping update(final YamlMapping index, final TgzArchive tgz) {
        final ChartYaml chart = tgz.chartYaml();
        final YamlMapping resyaml;
        YamlMappingBuilder indexbldr = Yaml.createYamlMappingBuilder();
        indexbldr = IndexYaml.addNodesExceptEntries(index, indexbldr);
        final Optional<YamlNode> entriesnode = IndexYaml.entriesNode(index);
        if (entriesnode.isPresent()) {
            final YamlMapping entries = entriesnode.get().asMapping();
            final Optional<YamlNode> chartnode = IndexYaml.chartNode(index, chart.name());
            if (chartnode.isPresent()
                && IndexYaml.notMatchVersion(chartnode.get(), chart.version())
            ) {
                resyaml = indexbldr.add(
                    "entries",
                    this.nodePresentVersionNotMatch(entries, tgz, chart)
                ).build();
            } else if (chartnode.isEmpty()) {
                resyaml = indexbldr.add(
                    "entries",
                    this.nodeNotPresent(entries, tgz, chart)
                ).build();
            } else {
                resyaml = index;
            }
        } else {
            YamlMappingBuilder tmp = Yaml.createYamlMappingBuilder();
            tmp = IndexYaml.addNodesExceptEntries(index, tmp);
            tmp = tmp.add(
                "entries", Yaml.createYamlMappingBuilder()
                    .add(
                        chart.name(), Yaml.createYamlSequenceBuilder()
                            .add(this.newChart(tgz, chart).build())
                            .build()
                    ).build()
            );
            resyaml = tmp.build();
        }
        return resyaml;
        // @checkstyle @checkstyle MethodBodyCommentsCheck (8 lines)
        // @todo #32:30min Create a unit test for digest field
        //  One of the fields Index.yaml require is "digest" field. The test should make verify
        //  that field has been generated correctly.
        // @todo #32:30min Create a unit test for urls field
        //  One of the fields Index.yaml require is "urls" field. The test should make verify
        //  that field has been generated correctly.
    }

    /**
     * Creates a new instance of `entries` node with a new version
     * for the chart that is present.
     * @param entries Previous values for `entries` node
     * @param tgz Tgz archive
     * @param chart Chart with yaml for a new version
     * @return Yaml mapping of the new `entities` node.
     */
    private YamlMapping nodePresentVersionNotMatch(final YamlMapping entries, final TgzArchive tgz,
        final ChartYaml chart) {
        final YamlMappingBuilder chartbldr = this.newChart(tgz, chart);
        YamlMappingBuilder newentries = Yaml.createYamlMappingBuilder();
        for (final YamlNode node : entries.keys()) {
            final String name = node.asScalar().value();
            if (name.equals(chart.name())) {
                YamlSequenceBuilder seq = Yaml.createYamlSequenceBuilder()
                    .add(chartbldr.build());
                for (final YamlNode vers : entries.value(name).asSequence()) {
                    seq = seq.add(vers);
                }
                newentries = newentries.add(name, seq.build());
            } else {
                newentries = newentries.add(name, entries.value(name));
            }
        }
        return newentries.build();
    }

    /**
     * Creates a new instance of `entries` node for chart that
     * is not present.
     * @param entries Previous values for `entries` node
     * @param tgz Tgz archive
     * @param chart Chart with yaml for a new version
     * @return Yaml mapping of the new `entities` node.
     */
    private YamlMapping nodeNotPresent(final YamlMapping entries, final TgzArchive tgz,
        final ChartYaml chart) {
        final YamlMappingBuilder chartbldr = this.newChart(tgz, chart);
        YamlMappingBuilder newentries = Yaml.createYamlMappingBuilder();
        for (final YamlNode node : entries.keys()) {
            final String name = node.asScalar().value();
            newentries = newentries.add(name, entries.value(name));
        }
        return newentries.add(chart.name(), chartbldr.build()).build();
    }

    /**
     * Creates yaml mapping builder for the chart.
     * @param tgz Tgz archive
     * @param chart Chart with yaml for a new version
     * @return Yaml mapping builder for the chart.
     */
    private YamlMappingBuilder newChart(final TgzArchive tgz, final ChartYaml chart) {
        final YamlMappingBuilder builder = Yaml.createYamlMappingBuilder()
            .add("created", ZonedDateTime.now().format(IndexYaml.TIME_FORMATTER))
            .add("digest", tgz.digest())
            .add(
                "urls", Yaml.createYamlSequenceBuilder()
                    .add(String.format("%s%s", this.base, chart.name()))
                    .build()
            );
        return IndexYaml.addNodesExceptEntries(chart.yamlMapping(), builder);
    }

    /**
     * Creates yaml mapping builder with all nodes from yaml,
     * except `entries` node.
     * @param yaml Source yaml
     * @param builder Builder
     * @return Yaml mapping builder
     */
    private static YamlMappingBuilder addNodesExceptEntries(final YamlMapping yaml,
        final YamlMappingBuilder builder) {
        YamlMappingBuilder res = builder;
        final Set<YamlNode> nodes = yaml.keys().stream()
            .filter(node -> !node.asScalar().value().equals("entries"))
            .collect(Collectors.toSet());
        for (final YamlNode node : nodes) {
            final String name = node.asScalar().value();
            if (name.equals("appVersion")) {
                res = res.add(name, String.format("'%s'", yaml.string(name)));
            } else {
                res = res.add(name, yaml.value(name));
            }
        }
        return res;
    }

    /**
     * Optional for `entries` section.
     * @param indexyaml Source yaml
     * @return Optional for `entries` section.
     */
    private static Optional<YamlNode> entriesNode(final YamlMapping indexyaml) {
        final Optional<YamlNode> res;
        if (indexyaml.keys().stream()
            .anyMatch(node -> node.asScalar().value().equals("entries"))
        ) {
            res = Optional.of(indexyaml.value("entries"));
        } else {
            res = Optional.empty();
        }
        return res;
    }

    /**
     * Search a specified chart name among entries from `entries` section.
//     * @param entries Chart names from `entries` section
     * @param indexyaml Source yaml
     * @param chartname Searched chart name
     * @return Optional for chart name.
     */
    private static Optional<YamlNode> chartNode(final YamlMapping indexyaml,
        final String chartname) {
        final Optional<YamlNode> res;
        if (indexyaml.value("entries").asMapping()
            .keys().stream()
            .anyMatch(node -> node.asScalar().value().equals(chartname))
        ) {
            res = Optional.of(indexyaml.value("entries").asMapping().value(chartname));
        } else {
            res = Optional.empty();
        }
        return res;
    }

    /**
     * Checks whether the specified version exists in the chart node.
     * @param chartnode Chart node
     * @param version Target version
     * @return True - none chart's version matches a specified version, false - otherwise.
     */
    private static boolean notMatchVersion(final YamlNode chartnode, final String version) {
        return chartnode.asSequence()
            .values().stream()
            .noneMatch(
                node -> node.asMapping()
                    .string("version")
                    .equals(version)
            );
    }
}
