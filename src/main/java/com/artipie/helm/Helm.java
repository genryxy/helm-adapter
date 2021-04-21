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
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Helm repository.
 * @since 0.3
 */
public interface Helm {
    /**
     * Batch update of Helm files for repository.
     * @param prefix Repository prefix
     * @return Result of completion
     */
    CompletionStage<Void> batchUpdate(Key prefix);

    /**
     * Implementation of {@link Helm} for abstract storage.
     * @since 0.3
     */
    final class Asto implements Helm {
        @Override
        public CompletionStage<Void> batchUpdate(final Key prefix) {
            throw new NotImplementedException("not implemented yet");
        }
    }
}
