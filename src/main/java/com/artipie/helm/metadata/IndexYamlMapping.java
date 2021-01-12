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

import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Mapping for content from index.yaml file.
 *
 * @since 0.2
 */
@SuppressWarnings("unchecked")
public final class IndexYamlMapping {

    /**
     * Mapping for fields from index.yaml file.
     */
    private final Map<String, Object> mapping;

    /**
     * Ctor.
     * @param yaml Index.yaml file
     */
    public IndexYamlMapping(final String yaml) {
        this((Map<String, Object>) new Yaml().load(yaml));
    }

    /**
     * Ctor.
     * @param mapfromindex Mapping for fields from index.yaml file
     */
    public IndexYamlMapping(final Map<String, Object> mapfromindex) {
        this.mapping = mapfromindex;
    }

    /**
     * Obtain mapping for `entries`.
     * @return Mapping for `entries`.
     */
    public Map<String, Object> entries() {
        return (Map<String, Object>) this.mapping.get("entries");
    }

    /**
     * Obtain mapping for specified chart from `entries`.
     * @param chartname Chart name
     * @return Mapping for specified chart from `entries`.
     */
    public List<Map<String, Object>> entriesByChart(final String chartname) {
        return (List<Map<String, Object>>) this.entries().get(chartname);
    }
}
