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
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.http.HelmSlice;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Push helm chart and ensure if index.yaml is generated properly.
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@DisabledIfSystemProperty(named = "os.name", matches = "Windows.*")
final class HelmSliceIT {
    /**
     * Chart name.
     */
    private static final String CHART = "tomcat-0.4.1.tgz";

    @Test
    void indexYamlIsCorrect() {
        final Storage storage = new InMemoryStorage();
        MatcherAssert.assertThat(
            "Returned status is not 200",
            new HelmSlice(storage, "http://anyhost:123/").response(
                new RequestLine(
                    RqMethod.PUT,
                    String.format("/%s", HelmSliceIT.CHART)
                ).toString(),
                Headers.EMPTY,
                new Content.From(new TestResource(HelmSliceIT.CHART).asBytes())
            ),
            new RsHasStatus(RsStatus.OK)
        );
        MatcherAssert.assertThat(
            "Generated index does not contain required chart",
            new IndexYamlMapping(
                new PublisherAs(
                    storage.value(IndexYaml.INDEX_YAML).join()
                ).asciiString()
                .toCompletableFuture().join()
            ).byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
    }
}
