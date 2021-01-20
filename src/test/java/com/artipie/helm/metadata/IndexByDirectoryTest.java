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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.google.common.base.Throwables;
import java.util.Optional;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link IndexByDirectory}.
 * @since 0.2
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class IndexByDirectoryTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @Test
    void worksProperlyForManyVersions() {
        final Key prefix = new Key.From("path/nested/");
        Stream.of("ark-1.0.1.tgz", "ark-1.2.0.tgz", "tomcat-0.4.1.tgz", "index.yaml")
            .forEach(name -> this.saveByPrefix(prefix, name));
        this.storage.save(new Key.From("newpath/ark-0.2.2.tgz"), Content.EMPTY).join();
        this.storage.save(new Key.From(prefix, "not.txt"), Content.EMPTY).join();
        this.storage.save(new Key.From(prefix, "deep/sth-0.4.1.tgz"), Content.EMPTY).join();
        final IndexYamlMapping mapping = new IndexYamlMapping(
            new String(
                new IndexByDirectory(this.storage, prefix)
                    .value()
                    .toCompletableFuture().join()
                    .get()
            )
        );
        MatcherAssert.assertThat(
            "Contains required charts",
            mapping.entries().keySet(),
            Matchers.containsInAnyOrder("ark", "tomcat")
        );
        MatcherAssert.assertThat(
            "Contains both versions of chart",
            mapping.entriesByChart("ark").size(),
            new IsEqual<>(2)
        );
    }

    @Test
    void returnsEmptyForEmptyDirectory() {
        this.saveByPrefix(Key.ROOT, "index.yaml");
        MatcherAssert.assertThat(
            new IndexByDirectory(this.storage, new Key.From("my/prefix"))
                .value()
                .toCompletableFuture().join()
                .isPresent(),
            new IsEqual<>(false)
        );
    }

    @Test
    void throwsExceptionWhenIndexYamlNotFound() {
        final Key dir = new Key.From("my/dir");
        this.saveByPrefix(dir, "ark-1.0.1.tgz");
        new IndexByDirectory(this.storage, dir)
            .value()
            .handle(
                (res, exc) -> {
                    MatcherAssert.assertThat(
                        Throwables.getRootCause(exc),
                        new IsInstanceOf(IllegalStateException.class)
                    );
                    return Optional.empty();
                }
            ).toCompletableFuture()
            .join();
    }

    @Test
    void throwsExceptionWhenTgzIsMalformed() {
        final Key dir = new Key.From("some/directory");
        this.saveByPrefix(dir, "index.yaml");
        this.storage.save(new Key.From(dir, "malformed-0.2.2.tgz"), Content.EMPTY).join();
        new IndexByDirectory(this.storage, dir)
            .value()
            .handle(
                (res, exc) -> {
                    MatcherAssert.assertThat(
                        Throwables.getRootCause(exc).getMessage(),
                        new IsEqual<>("Input is not in the .gz format")
                    );
                    return Optional.empty();
                }
            ).toCompletableFuture()
            .join();
    }

    private void saveByPrefix(final Key prefix, final String name) {
        new TestResource(name).saveTo(this.storage, new Key.From(prefix, name));
    }
}
