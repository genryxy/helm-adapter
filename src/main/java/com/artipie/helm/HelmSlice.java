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
import com.artipie.http.Slice;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;

/**
 * HelmSlice.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class HelmSlice extends Slice.Wrap {

    /**
     * Ctor.
     *
     * @param storage The storage.
     * @param base The base path the slice is expected to be accessed from. Example: https://central.artipie.com/helm
     */
    public HelmSlice(final Storage storage, final String base) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new RtRule.Any(
                        new RtRule.ByMethod(RqMethod.PUT),
                        new RtRule.ByMethod(RqMethod.POST)
                    ),
                    new PushChartSlice(storage, base)
                ),
                new RtRulePath(
                    new RtRule.ByMethod(RqMethod.GET),
                    new SliceDownload(storage)
                ),
                new RtRulePath(
                    RtRule.FALLBACK,
                    new SliceSimple(new RsWithStatus(RsStatus.METHOD_NOT_ALLOWED))
                )
            )
        );
    }
}
