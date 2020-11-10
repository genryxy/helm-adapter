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
import io.reactivex.Single;

/**
 * The Chart.yaml file.
 *
 * @since 0.2
 */
public class ChartYaml {

    /**
     * The Yaml.
     */
    private final Single<YamlMapping> yaml;

    /**
     * Ctor.
     * @param yaml The yaml.
     */
    public ChartYaml(final String yaml) {
        this.yaml = Single.fromCallable(
            () -> Yaml.createYamlInput(yaml).readYamlMapping()
        ).cache();
    }

    /**
     * Obtain name from chart yaml.
     * @return Name.
     */
    public String name() {
        return this.string("name");
    }

    /**
     * Obtain version from chart yaml.
     * @return Version.
     */
    public String version() {
        return this.string("version");
    }

    /**
     * Obtain archive name.
     * @return How the archive should be named on the file system.
     */
    public String tgzName() {
        return String.format("%s-%s.tgz", this.name(), this.version());
    }

    /**
     * Return Chart.yaml as yaml mapping.
     * @return YamlMapping.
     */
    public YamlMapping yamlMapping() {
        return this.yaml.blockingGet();
    }

    /**
     * Obtain a string by name.
     * @param name The name of field to read
     * @return A string from the yaml file by specified name.
     */
    private String string(final String name) {
        return this.yaml.blockingGet().string(name);
    }
}
