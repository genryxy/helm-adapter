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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

/**
 * A test for {@link TgzArchive}.
 *
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class TgzArchiveTest {

    @Test
    public void nameIdentifiedCorrectly() throws IOException {
        MatcherAssert.assertThat(
            new TgzArchive(
                new TestResource("tomcat-0.4.1.tgz").asBytes()
            ).name(),
            new IsEqual<>("tomcat-0.4.1.tgz")
        );
    }

    @Test
    public void sizeHasCorrectValue() throws IOException {
        final TestResource file = new TestResource("tomcat-0.4.1.tgz");
        MatcherAssert.assertThat(
            new TgzArchive(
                file.asBytes()
            ).size().get(),
            new IsEqual<>(
                Files.size(file.asPath())
            )
        );
    }

    @Test
    public void savedCorrectly() throws IOException {
        final Storage storage = new InMemoryStorage();
        final String name = "tomcat-0.4.1.tgz";
        final byte[] file = new TestResource(name).asBytes();
        new TgzArchive(file)
            .save(storage).blockingGet();
        MatcherAssert.assertThat(
            new PublisherAs(storage.value(new Key.From(name)).join())
                .bytes()
                .toCompletableFuture().join(),
            new IsEqual<>(file)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasCorrectMetadata() {
        MatcherAssert.assertThat(
            new TgzArchive(
                new TestResource("tomcat-0.4.1.tgz").asBytes()
            ).metadata(Optional.empty()),
            new AllOf<>(
                new ListOf<>(
                    new IsMapContaining<>(
                        new IsEqual<>("urls"),
                        new IsEqual<>(Collections.singletonList("tomcat-0.4.1.tgz"))
                    ),
                    new IsMapContaining<>(
                        new IsEqual<>("digest"),
                        new IsInstanceOf(String.class)
                    )
                )
            )
        );
    }
}
