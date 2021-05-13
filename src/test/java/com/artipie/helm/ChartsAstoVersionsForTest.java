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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cactoos.map.MapEntry;
import org.cactoos.map.MapOf;
import org.cactoos.scalar.Unchecked;
import org.cactoos.set.SetOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Charts.Asto}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class ChartsAstoVersionsForTest {
    @Test
    void getsVersionsForPassedCharts() {
        final Storage storage = new InMemoryStorage();
        final String arkone = "ark-1.0.1.tgz";
        final String arktwo = "ark-1.2.0.tgz";
        final String tomcat = "tomcat-0.4.1.tgz";
        Stream.of(arkone, arktwo, tomcat)
            .forEach(tgz -> new TestResource(tgz).saveTo(storage));
        MatcherAssert.assertThat(
            new Charts.Asto(storage)
                .versionsFor(
                    Stream.of(arkone, arktwo, tomcat)
                        .map(Key.From::new)
                        .collect(Collectors.toList())
                ).toCompletableFuture().join(),
            new IsEqual<>(
                new MapOf<>(
                    new MapEntry<>(
                        "tomcat",
                        new Unchecked<>(() -> new SetOf<>("0.4.1")).value()
                    ),
                    new MapEntry<>(
                        "ark",
                        new Unchecked<>(() -> new SetOf<>("1.2.0", "1.0.1")).value()
                    )
                )
            )
        );
    }
}
