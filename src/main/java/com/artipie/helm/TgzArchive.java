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
import com.artipie.asto.rx.RxStorageWrapper;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * A .tgz archive file.
 * @todo #12:30min Test for TgzArchive
 *  For now this method is not implemented, but we definitely need a test for this class.
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 * @checkstyle NonStaticMethodCheck (500 lines)
 * @checkstyle AvoidInlineConditionalsCheck (500 lines)
 */
@SuppressWarnings({"PMD.UnusedFormalParameter",
    "PMD.UnusedPrivateField",
    "PMD.ArrayIsStoredDirectly",
    "PMD.UnusedFormalParameter",
    "PMD.AvoidDuplicateLiterals",
    "PMD.SingularField"})
final class TgzArchive {

    /**
     * The archive content.
     */
    private final byte[] content;

    /**
     * Ctor.
     * @param content The archive content.
     */
    TgzArchive(final byte[] content) {
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
        final int eightkb = 8 * 1024;
        final int last = this.content.length % eightkb == 0 ? 0 : 1;
        final int chunks = this.content.length / eightkb + last;
        final ArrayList<ByteBuffer> arr = new ArrayList<>(chunks);
        for (int idx = 0; idx < chunks; idx += 1) {
            final byte[] bytes;
            if (idx == chunks - 1 && last == 1) {
                bytes = new byte[this.content.length % eightkb];
            } else {
                bytes = new byte[eightkb];
            }
            System.arraycopy(this.content, idx * eightkb, bytes, 0, bytes.length);
            arr.add(ByteBuffer.wrap(bytes));
        }
        final Key.From key = new Key.From(this.name());
        return new RxStorageWrapper(storage)
            .save(key, new Content.From(Flowable.fromIterable(arr)))
            .andThen(Single.just(key));
    }

}
