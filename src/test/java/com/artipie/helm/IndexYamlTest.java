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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.asto.test.TestResource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

/**
 * Test case for {@link IndexYaml}.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @since 0.2
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "unchecked"})
final class IndexYamlTest {

    /**
     * Chart name.
     */
    private static final String TOMCAT = "tomcat-0.4.1.tgz";

    /**
     * Chart name.
     */
    private static final String ARK = "ark-1.0.1.tgz";

    /**
     * Base string.
     */
    private static final String BASE = "http://central.artipie.com/helm/";

    /**
     * Storage.
     */
    private Storage storage;

    /**
     * Index yaml file.
     */
    private IndexYaml yaml;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
        this.yaml = new IndexYaml(this.storage, IndexYamlTest.BASE);
        this.update(IndexYamlTest.TOMCAT);
    }

    @Test
    void verifyDigestFromIndex() {
        final List<Map<String, Object>> tomcat = this.entries("tomcat");
        MatcherAssert.assertThat(
            tomcat.get(0).get("digest"),
            new IsEqual<>(
                DigestUtils.sha256Hex(new TestResource(IndexYamlTest.TOMCAT).asBytes())
            )
        );
    }

    @Test
    void notChangeForSameChartWithSameVersion() {
        final String tomcat = "tomcat";
        final Map<String, Object> old = this.entries(tomcat).get(0);
        this.update(IndexYamlTest.TOMCAT);
        final List<Map<String, Object>> updt = this.entries(tomcat);
        MatcherAssert.assertThat(
            "New version was not added",
            updt.size(),
            new IsEqual<>(1)
        );
        MatcherAssert.assertThat(
            "Metadata was not changed",
            old.equals(updt.get(0)),
            new IsEqual<>(true)
        );
    }

    @Test
    void addMetadataForSameChartWithNewVersion() {
        this.update(IndexYamlTest.ARK);
        this.update("ark-1.2.0.tgz");
        final List<Map<String, Object>> entries = this.entries("ark");
        MatcherAssert.assertThat(
            "New version was added",
            entries.size(),
            new IsEqual<>(2)
        );
        final String[] versions = entries.stream()
             .map(entry -> (String) entry.get("version"))
             .toArray(String[]::new);
        MatcherAssert.assertThat(
            "Contains both versions",
            versions,
            Matchers.arrayContainingInAnyOrder("1.0.1", "1.2.0")
        );
    }

    @Test
    void addMetadataForNewChartInExistingIndex() {
        this.update(IndexYamlTest.ARK);
        final Map<String, Object> ark = this.entries("ark").get(0);
        final Map<String, Object> chart = this.chartYaml(IndexYamlTest.ARK);
        final int numgenfields = 3;
        MatcherAssert.assertThat(
            "Index.yaml has required number of keys",
            ark.size(),
            new IsEqual<>(chart.size() + numgenfields)
        );
        MatcherAssert.assertThat(
            "Keys have correct values",
            ark,
            new AllOf<>(
                Arrays.asList(
                    this.matcher("appVersion", chart),
                    this.matcher("apiVersion", chart),
                    this.matcher("version", chart),
                    this.matcher("name", chart),
                    this.matcher("description", chart),
                    this.matcher("home", chart),
                    this.matcher("maintainers", chart),
                    Matchers.hasEntry(
                        "urls", Collections.singletonList(
                            String.format("%s%s", IndexYamlTest.BASE, IndexYamlTest.ARK)
                        )
                    ),
                    Matchers.hasEntry(
                        "sources", Collections.singletonList("https://github.com/heptio/ark")
                    ),
                    Matchers.hasKey("created"),
                    Matchers.hasKey("digest")
                )
            )
        );
    }

    private Matcher<Map<? extends String, ?>> matcher(final String key,
        final Map<String, Object> chart) {
        return Matchers.hasEntry(key, chart.get(key));
    }

    private Map<String, Object> chartYaml(final String file) {
        return new TgzArchive(
            new PublisherAs(
                new Content.From(new TestResource(file).asBytes())
            ).bytes()
            .toCompletableFuture().join()
        ).chartYaml()
        .fields();
    }

    private List<Map<String, Object>> entries(final String name) {
        final Map<String, Object> index = new Yaml().load(
            new PublisherAs(
                new RxStorageWrapper(this.storage)
                    .value(new Key.From("index.yaml"))
                    .blockingGet()
            ).asciiString().toCompletableFuture().join()
        );
        final Map<String, Object> entries = (Map<String, Object>) index.get("entries");
        return (List<Map<String, Object>>) entries.get(name);
    }

    private void update(final String chart) {
        this.yaml.update(
            new TgzArchive(
                new PublisherAs(
                    new Content.From(new TestResource(chart).asBytes())
                ).bytes()
                .toCompletableFuture().join()
            )
        ).blockingGet();
    }
}
