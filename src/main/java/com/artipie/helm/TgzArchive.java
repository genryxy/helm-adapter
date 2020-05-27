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
import io.reactivex.Single;

/**
 * A .tgz archive file.
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
// @todo #12:30min Test for TgzArchive
//  For now this method is not implemented, but we definitely need a test for this class.
final class TgzArchive {

    /**
     * The archive content.
     */
    private final byte[] content;

    /**
     * Ctor.
     * @param content The archive content.
     */
    public TgzArchive(final byte[] content) {
        this.content = content;
    }

    /**
     * Obtain archive name.
     * @return How the archive should be named on the file system
     */
    public String name() {
        // @todo #12:30min TgzArchive name method
        //  For now this method is not implemented. We should decompress the archive, find the chart
        //  version and name, and, return it, binded together.
        throw new IllegalStateException("Not Implemented");
    }

    /**
     * Save archive in an asto storage.
     * @param storage The storage to save archive on.
     * @return Asto location, where archive is save.
     */
    public Single<Key> save(final Storage storage) {
        // @todo #12:30min Save the archive into Asto.
        //  For now this method is not implemented. The archive should be saved with a key name,
        //  obtained from TgzArchive#name().
        return Single.error(new IllegalStateException("Not Implemented"));
    }

}
