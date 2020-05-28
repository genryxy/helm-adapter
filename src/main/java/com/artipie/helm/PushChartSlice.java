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
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import io.reactivex.Single;
import java.nio.ByteBuffer;
import java.util.Map;
import org.reactivestreams.Publisher;

/**
 * A Slice which accept archived charts, save them into a storage and trigger index.yml reindexing.
 * @todo #13:30min Create an integration test
 *  We need an integration test for this class with described logic of upload from client side
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 */
@SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField", "PMD.UnusedFormalParameter"})
public final class PushChartSlice implements Slice {

    /**
     * The Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage The storage.
     */
    public PushChartSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return new AsyncResponse(this.response(body));
    }

    /**
     * The reactive response.
     * @param body The body
     * @return The response
     */
    private Single<Response> response(final Publisher<ByteBuffer> body) {
        return memory(body).flatMapCompletable(
            arch -> arch.save(this.storage).flatMapCompletable(
                key -> new IndexYaml(this.storage).update(arch)
            )
        ).andThen(Single.just(new RsWithStatus(StandardRs.EMPTY, RsStatus.OK)));
    }

    /**
     * Loads bytes into the memory.
     * @param body The body.
     * @return Bytes in a single byte array
     */
    private static Single<TgzArchive> memory(final Publisher<ByteBuffer> body) {
        // @todo #12:30min Generate TgzArchive from byte flow
        //  For now this method is not implemented. Byte flow of archive data should be collected
        //  together in order to instantiate a TgzArchive instance.
        return Single.error(new IllegalStateException("Not Implemented"));
    }
}
