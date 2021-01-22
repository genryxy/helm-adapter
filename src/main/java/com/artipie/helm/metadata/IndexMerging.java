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
import com.artipie.asto.ext.PublisherAs;
import java.util.concurrent.CompletionStage;

/**
 * Merging two `index.yaml` files in one file.
 * <ul>
 *     <li>If source `index.yaml` and remote have the same version of chart,
 *     data from remote chart should be taken</li>
 *     <li>If source `index.yaml` does not have some charts or versions of chart,
 *     `created` field for them should be updated during merge of the two indexes.</li>
 * </ul>
 * @since 0.2
 */
public final class IndexMerging {
    /**
     * Source `index.yaml` file. Another file will be merged to this one.
     */
    private final IndexYamlMapping source;

    /**
     * Ctor.
     * @param source File `index.yaml` that will be merged
     */
    public IndexMerging(final IndexYamlMapping source) {
        this.source = source;
    }

    /**
     * Merges passed index with source index file.
     * @param remote File `index.yaml` that will be merged
     * @return Merged `index.yaml`
     */
    public CompletionStage<Content> mergeWith(final Content remote) {
        return new PublisherAs(remote).asciiString()
            .thenApply(IndexYamlMapping::new)
            .thenApply(
                index -> {
                    for (final String name : index.entries().keySet()) {
                        this.source.addChartVersions(name, index.byChart(name));
                    }
                    return this.source;
                }
            ).thenApply(IndexYamlMapping::toContent)
            .thenApply(opt -> opt.orElse(Content.EMPTY));
    }
}
