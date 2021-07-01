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
package com.artipie.helm.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test for {@link DeleteChartSlice}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class DeleteChartSliceTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"", "/chart?noname=no", "/chart?version=1.0.2", "/wrongPath?name=any"}
        )
    void returnBadRequest(final String rqline) {
        MatcherAssert.assertThat(
            new DeleteChartSlice(this.storage).response(
                new RequestLine(RqMethod.DELETE, rqline).toString(),
                Headers.EMPTY,
                Content.EMPTY
            ),
            new RsHasStatus(RsStatus.BAD_REQUEST)
        );
    }

    @Test
    void deleteAllVersionsByName() {
        final String arkone = "ark-1.0.1.tgz";
        final String arktwo = "ark-1.2.0.tgz";
        Stream.of("index.yaml", "ark-1.0.1.tgz", "ark-1.2.0.tgz", "tomcat-0.4.1.tgz")
            .forEach(source -> new TestResource(source).saveTo(this.storage));
        MatcherAssert.assertThat(
            "Response status is not 200",
            new DeleteChartSlice(this.storage).response(
                new RequestLine(RqMethod.DELETE, "/chart?name=ark").toString(),
                Headers.EMPTY,
                Content.EMPTY
            ),
            new RsHasStatus(RsStatus.OK)
        );
        MatcherAssert.assertThat(
            "Deleted chart is present in index",
            this.indexFromStrg().byChart("ark").isEmpty(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Archive of deleted chart remains",
            this.storage.exists(new Key.From(arkone)).join(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Archive of deleted chart remains",
            this.storage.exists(new Key.From(arktwo)).join(),
            new IsEqual<>(false)
        );
    }

    @Test
    void deleteByNameAndVersion() {
        Stream.of("index.yaml", "ark-1.0.1.tgz", "ark-1.2.0.tgz", "tomcat-0.4.1.tgz")
            .forEach(source -> new TestResource(source).saveTo(this.storage));
        MatcherAssert.assertThat(
            "Response status is not 200",
            new DeleteChartSlice(this.storage).response(
                new RequestLine(RqMethod.DELETE, "/chart?name=ark&version=1.0.1").toString(),
                Headers.EMPTY,
                Content.EMPTY
            ),
            new RsHasStatus(RsStatus.OK)
        );
        MatcherAssert.assertThat(
            "Deleted chart is present in index",
            this.indexFromStrg().byChartAndVersion("ark", "1.0.1").isPresent(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Second chart was also deleted",
            this.indexFromStrg().byChartAndVersion("ark", "1.2.0").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Archive of deleted chart remains",
            this.storage.exists(new Key.From("ark-1.0.1.tgz")).join(),
            new IsEqual<>(false)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"/chart?name=not-exist", "/chart?name=ark&version=0.0.0"})
    void failsToDeleteByNotExisted(final String rqline) {
        Stream.of("index.yaml", "ark-1.0.1.tgz", "ark-1.2.0.tgz", "tomcat-0.4.1.tgz")
            .forEach(source -> new TestResource(source).saveTo(this.storage));
        MatcherAssert.assertThat(
            new DeleteChartSlice(this.storage).response(
                new RequestLine(RqMethod.DELETE, rqline).toString(),
                Headers.EMPTY,
                Content.EMPTY
            ),
            new RsHasStatus(RsStatus.NOT_FOUND)
        );
    }

    private IndexYamlMapping indexFromStrg() {
        return new IndexYamlMapping(
            new PublisherAs(
                this.storage.value(IndexYaml.INDEX_YAML).join()
            ).asciiString()
            .toCompletableFuture().join()
        );
    }
}
