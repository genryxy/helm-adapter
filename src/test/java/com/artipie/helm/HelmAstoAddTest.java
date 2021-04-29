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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Helm.Asto#add(Collection)}.
 * @since 0.3
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class HelmAstoAddTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
        this.storage.save(
            IndexYaml.INDEX_YAML,
            new Content.From(new TestResource("index/index-one-ark.yaml").asBytes())
        ).join();
    }

    @Test
    @Disabled
    void addInfoAboutNewVersionOfPackageAndNewPackage() {
        final String tomcat = "tomcat-0.4.1.tgz";
        final String ark = "ark-1.2.0.tgz";
        this.saveToStorage(tomcat);
        this.saveToStorage(ark);
        this.addFilesToIndex(tomcat, ark);
    }

    @Test
    @Disabled
    void addInfoAboutNewPackage() {
        final String tomcat = "tomcat-0.4.1.tgz";
        this.saveToStorage(tomcat);
        this.addFilesToIndex(tomcat);
    }

    @Test
    @Disabled
    void addInfoAboutNewVersion() {
        final String ark = "ark-1.2.0.tgz";
        this.saveToStorage(ark);
        this.addFilesToIndex(ark);
    }

    @Test
    @Disabled
    void failsToAddInfoAboutExistedVersion() {
        final String ark = "ark-1.0.1.tgz";
        this.saveToStorage(ark);
        this.addFilesToIndex(ark);
    }

    private void saveToStorage(final String file) {
        this.storage.save(
            new Key.From(file),
            new Content.From(new TestResource(file).asBytes())
        ).join();
    }

    private void addFilesToIndex(final String... files) {
        final Collection<Key> keys = Arrays.stream(files)
            .map(Key.From::new)
            .collect(Collectors.toList());
        new Helm.Asto(this.storage).add(keys).toCompletableFuture().join();
    }
}
