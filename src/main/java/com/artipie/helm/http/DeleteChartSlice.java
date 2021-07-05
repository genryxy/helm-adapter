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
package com.artipie.helm.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.ChartYaml;
import com.artipie.helm.TgzArchive;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import io.reactivex.Single;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.reactivestreams.Publisher;

/**
 * Endpoint for removing chart by name or by name and version.
 * @since 0.3
 */
final class DeleteChartSlice implements Slice {
    /**
     * Pattern for endpoint.
     */
    static final Pattern PTRN_DEL_CHART = Pattern.compile(
        "^/charts/(?<name>[a-zA-Z\\-\\d.]+)/?(?<version>[a-zA-Z\\-\\d.]*)$"
    );

    /**
     * The Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage The storage.
     */
    DeleteChartSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        final URI uri = new RequestLineFrom(line).uri();
        final Matcher matcher = DeleteChartSlice.PTRN_DEL_CHART.matcher(uri.getPath());
        final Response res;
        if (matcher.matches()) {
            final String chart = matcher.group("name");
            final String vers = matcher.group("version");
            if (vers.isEmpty()) {
                res = new AsyncResponse(
                    new IndexYaml(this.storage)
                        .deleteByName(chart)
                        .andThen(this.deleteArchives(chart, Optional.empty()))
                );
            } else {
                res = new AsyncResponse(
                    new IndexYaml(this.storage)
                        .deleteByNameAndVersion(chart, vers)
                        .andThen(this.deleteArchives(chart, Optional.of(vers)))
                );
            }
        } else {
            res = new RsWithStatus(RsStatus.BAD_REQUEST);
        }
        return res;
    }

    /**
     * Delete archives from storage which contain chart with specified name and version.
     * @param name Name of chart.
     * @param vers Version of chart. If it is empty, all versions will be deleted.
     * @return OK - archives were successfully removed, NOT_FOUND - in case of absence.
     */
    private Single<Response> deleteArchives(final String name, final Optional<String> vers) {
        final AtomicBoolean wasdeleted = new AtomicBoolean();
        return Single.fromFuture(
            this.storage.list(Key.ROOT)
                .thenApply(
                    keys -> keys.stream()
                        .filter(key -> key.string().endsWith(".tgz"))
                        .collect(Collectors.toList())
                )
                .thenCompose(
                    keys -> CompletableFuture.allOf(
                        keys.stream().map(
                            key -> this.storage.value(key)
                                .thenApply(PublisherAs::new)
                                .thenCompose(PublisherAs::bytes)
                                .thenApply(TgzArchive::new)
                                .thenCompose(
                                    tgz -> {
                                        final CompletionStage<Void> res;
                                        final ChartYaml chart = tgz.chartYaml();
                                        if (chart.name().equals(name)) {
                                            res = this.wasChartDeleted(chart, vers, key)
                                                .thenCompose(
                                                    wasdel -> {
                                                        wasdeleted.compareAndSet(false, wasdel);
                                                        return CompletableFuture.allOf();
                                                    }
                                                );
                                        } else {
                                            res = CompletableFuture.allOf();
                                        }
                                        return res;
                                    }
                                )
                        ).toArray(CompletableFuture[]::new)
                    ).thenApply(
                        noth -> {
                            final Response resp;
                            if (wasdeleted.get()) {
                                resp = StandardRs.OK;
                            } else {
                                resp = StandardRs.NOT_FOUND;
                            }
                            return resp;
                        }
                    )
                )
            );
    }

    /**
     * Checks that chart has required version and delete archive from storage in
     * case of existence of the key.
     * @param chart Chart yaml.
     * @param vers Version which should be deleted. If it is empty, all versions should be deleted.
     * @param key Key to archive which will be deleted in case of compliance.
     * @return Was chart by passed key deleted?
     */
    private CompletionStage<Boolean> wasChartDeleted(
        final ChartYaml chart,
        final Optional<String> vers,
        final Key key
    ) {
        final CompletionStage<Boolean> res;
        if (!vers.isPresent() || chart.version().equals(vers.get())) {
            res = this.storage.exists(key).thenCompose(
                exists -> {
                    final CompletionStage<Boolean> result;
                    if (exists) {
                        result = this.storage.delete(key).thenApply(noth -> true);
                    } else {
                        result = CompletableFuture.completedFuture(false);
                    }
                    return result;
                }
            );
        } else {
            res = CompletableFuture.completedFuture(false);
        }
        return res;
    }
}
