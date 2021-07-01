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

import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.http.HelmSlice;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.misc.RandomFreePort;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.google.common.io.ByteStreams;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Chart name.
     */
    private static final String CHART = "tomcat-0.4.1.tgz";

    /**
     * Artipie server.
     */
    private VertxSliceServer server;

    /**
     * Storage.
     */
    private Storage storage;

    /**
     * Server port.
     */
    private int port;

    @BeforeEach
    void setUp() {
        this.port = new RandomFreePort().get();
        this.storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            HelmSliceIT.VERTX,
            new LoggingSlice(
                new HelmSlice(this.storage, String.format("http://localhost:%d/", this.port))
            ),
            this.port
        );
        this.server.start();
    }

    @AfterEach
    void tearDown() {
        this.server.close();
    }

    @AfterAll
    static void tearDownAll() {
        VERTX.close();
    }

    @Test
    void indexYamlIsCorrect() throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = this.putToLocalhost();
            MatcherAssert.assertThat(
                "Response status is 200",
                conn.getResponseCode(),
                new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
            );
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        MatcherAssert.assertThat(
            "Generated index does not contain required chart",
            new IndexYamlMapping(
                new PublisherAs(
                    this.storage.value(IndexYaml.INDEX_YAML).join()
                ).asciiString()
                    .toCompletableFuture().join()
            ).byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
    }

    private HttpURLConnection putToLocalhost() throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) new URL(
            String.format(
                "http://localhost:%s/%s/%s", this.port, "some-repo", HelmSliceIT.CHART
            )
        ).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        ByteStreams.copy(
            new TestResource(HelmSliceIT.CHART).asInputStream(),
            conn.getOutputStream()
        );
        return conn;
    }
}
