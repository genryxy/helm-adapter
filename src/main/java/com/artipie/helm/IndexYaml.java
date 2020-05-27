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
import io.reactivex.Completable;

/**
 * Index.yaml file. The main file in a chart repo.
 *
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 * @checkstyle NonStaticMethodCheck (500 lines)
 */
@SuppressWarnings({"PMD.UnusedFormalParameter",
    "PMD.UnusedPrivateField",
    "PMD.ArrayIsStoredDirectly",
    "PMD.UnusedFormalParameter",
    "PMD.AvoidDuplicateLiterals",
    "PMD.SingularField"})
class IndexYaml {

    /**
     * The storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage The storage.
     */
    public IndexYaml(final Storage storage) {
        this.storage = storage;
    }

    /**
     * Update the index file.
     * @param arch New archive in a repo for which metadata is missing.
     * @return The operation result
     */
    public Completable update(final TgzArchive arch) {
        // @todo #8:30min Update Index file operation
        //  For now this method is not implemented. Index.yml file should be created if not existed
        //  before and update should be performed afterwards.
        return Completable.error(new IllegalStateException("Not implemented."));
    }
}
