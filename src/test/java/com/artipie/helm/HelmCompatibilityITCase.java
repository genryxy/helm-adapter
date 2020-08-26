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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.rs.RsStatus;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

/**
 * Ensure that helm command line tool is compatible with this adapter.
 *
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 * @since 0.2
 */
@DisabledIfSystemProperty(named = "os.name", matches = "Windows.*")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class HelmCompatibilityITCase {

    /**
     * The vertx.
     */
    private Vertx vertx;

    /**
     * The helm.
     */
    private HelmCompatibilityITCase.HelmContainer helm;

    /**
     * The web.
     */
    private WebClient web;

    /**
     * The turl.
     */
    private String turl;

    /**
     * The server.
     */
    private VertxSliceServer server;

    @BeforeEach
    public void before() throws URISyntaxException, IOException {
        this.vertx = Vertx.vertx();
        final Storage fls = new InMemoryStorage();
        final int port = rndPort();
        this.turl = String.format("http://host.testcontainers.internal:%d/", port);
        Testcontainers.exposeHostPorts(port);
        this.server = new VertxSliceServer(
            this.vertx,
            new HelmSlice(fls, this.turl),
            port
        );
        final byte[] tomcat = Files.readAllBytes(
            Paths.get(
                Thread.currentThread()
                    .getContextClassLoader()
                    .getResource("tomcat-0.4.1.tgz")
                    .toURI()
            )
        );
        this.web = WebClient.create(this.vertx);
        this.helm = new HelmCompatibilityITCase.HelmContainer()
            .withCreateContainerCmdModifier(
                cmd -> cmd.withEntrypoint("/bin/sh").withCmd("-c", "while sleep 3600; do :; done")
            );
        this.server.start();
        final int code = this.web.post(port, "localhost", "/")
            .rxSendBuffer(Buffer.buffer(tomcat))
            .blockingGet()
            .statusCode();
        if (code != Integer.parseInt(RsStatus.OK.code())) {
            throw new IllegalStateException(
                String.format("Received code non-200 code:%d", code)
            );
        }
        this.helm.start();
    }

    @AfterEach
    public void after() {
        this.helm.stop();
        this.web.close();
        this.server.close();
        this.vertx.close();
    }

    /**
     * Helm add repo works.
     *
     * @throws IOException If fails
     * @throws InterruptedException If fails
     */
    @Test
    public void helmRepoAddAndUpateWorks() throws IOException, InterruptedException {
        exec(this.helm, "helm", "init", "--client-only", "--debug");
        MatcherAssert.assertThat(
            "helm repo add failed",
            exec(this.helm, "helm", "repo", "add", "test", this.turl),
            new IsEqual<>(0)
        );
        MatcherAssert.assertThat(
            "helm repo update failed",
            exec(this.helm, "helm", "repo", "update"),
            new IsEqual<>(0)
        );
    }

    private int exec(
        final HelmCompatibilityITCase.HelmContainer helmc,
        final String... cmd) throws IOException, InterruptedException {
        final String joined = String.join(" ", cmd);
        LoggerFactory.getLogger(EnsureIndexIsGoodITCase.class).info("Executing:\n{}", joined);
        final Container.ExecResult exec = helmc.execInContainer(cmd);
        LoggerFactory.getLogger(EnsureIndexIsGoodITCase.class)
            .info("STDOUT:\n{}\nSTDERR:\n{}", exec.getStdout(), exec.getStderr());
        final int code = exec.getExitCode();
        if (code != 0) {
            LoggerFactory.getLogger(EnsureIndexIsGoodITCase.class)
                .error("'{}' failed with {} code", joined, code);
        }
        return code;
    }

    /**
     * Obtain a random port.
     *
     * @return The random port.
     */
    private static int rndPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Inner subclass to instantiate Helm container.
     *
     * @since 0.2
     */
    private static class HelmContainer extends
        GenericContainer<HelmCompatibilityITCase.HelmContainer> {
        HelmContainer() {
            super("alpine/helm:2.12.1");
        }
    }
}
