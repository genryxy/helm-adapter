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

import com.artipie.asto.Key;
import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.http.misc.TokenizerFlatProc;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import io.reactivex.Flowable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Reader of `index.yaml` file which does not read the entire file into memory.
 * @since 0.3
 * @todo #112:90min Replace parser with reactive version to avoid copying index
 *  file to temp storage. This parser should be replaced with converter from Publisher#ByteBuffer
 *  to another Publisher#ByteBuffer which is splitted by breaks (based on implementation
 *  of org.reactivestreams.Processor)
 */
public interface Index {
    /**
     * Obtains versions for packages which exist in the index file.
     * @param idxpath Path to index file
     * @return Map where key is a package name, value is represented versions.
     */
    CompletionStage<Map<String, Set<String>>> versionsByPackages(Key idxpath);

    /**
     * Reader of `index.yaml` which contains break lines.
     * This file looks like:
     * <pre>
     * apiVersion: v1
     * entries:
     *   ark:
     *   - apiVersion: v1
     *     version: 0.1.0
     * </pre>
     * @since 0.3
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    final class WithBreaks implements Index {
        /**
         * Versions.
         */
        static final String VRSNS = "version:";

        /**
         * Entries.
         */
        static final String ENTRS = "entries:";

        /**
         * Storage with index file.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param storage Storage file
         */
        public WithBreaks(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public CompletionStage<Map<String, Set<String>>> versionsByPackages(final Key idx) {
            return this.storage.exists(idx)
                .thenCompose(
                    exists -> {
                        final CompletionStage<Map<String, Set<String>>> res;
                        if (exists) {
                            res = this.versionsByPckgs(idx);
                        } else {
                            res = CompletableFuture.completedFuture(new HashMap<>());
                        }
                        return res;
                    }
                );
        }

        /**
         * Obtains last position of space from beginning before meeting any character.
         * @param line Text line
         * @return Last position of space from beginning before meeting any character.
         */
        private static int lastPosOfSpaceInBegin(final String line) {
            return line.length() - line.replaceAll("^\\s*", "").length();
        }

        /**
         * Parses index file and extracts versions for packages. The general idea of this parser
         * is next. All info about charts is located in `entries:` section. When we enter
         * in this section (e. g. read a line which is equal to `entries`), we started to
         * search a string which ends with colon and has required indent (usually it is
         * equal to 2). This string represents chart name. We've read such string, we
         * started to read info about saved versions for this chart. When we meet
         * a line which starts with `version:`, the version in map by chart name as key
         * is added.
         * @param idx Key of index file
         * @return Parsed versions of packages from index file.
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private CompletionStage<Map<String, Set<String>>> versionsByPckgs(final Key idx) {
            final TokenizerFlatProc target = new TokenizerFlatProc("\n");
            return this.storage.value(idx).thenAccept(
                cont -> cont.subscribe(target)
            ).thenCompose(
                noth -> Flowable.fromPublisher(target)
                    .map(buf -> new String(new Remaining(buf).bytes()))
                    .scan(
                        new ScanContext(),
                        (ctx, curr) -> {
                            final int pos = WithBreaks.lastPosOfSpaceInBegin(curr);
                            if (pos > 0 && ctx.indent == 0) {
                                ctx.setIndent(pos);
                            }
                            if (curr.startsWith(WithBreaks.ENTRS)) {
                                ctx.setEntries(true);
                            } else if (pos == 0) {
                                ctx.setEntries(false);
                            }
                            if (new ParsedChartName(curr).valid() && pos == ctx.indent) {
                                ctx.setName(curr.replace(":", "").trim());
                            } else if (ctx.inentries) {
                                if (curr.trim().startsWith(WithBreaks.VRSNS)) {
                                    ctx.addChartVersion(curr.replace(WithBreaks.VRSNS, "").trim());
                                }
                            } else {
                                ctx.setName("");
                            }
                            return ctx;
                        }
                    )
                .to(FlowableInterop.last())
                .thenApply(ScanContext::chartVersions)
            );
        }

        /**
         * Class for saving context during processing of index file.
         * It is not thread safe but {@code scan()} operation serially
         * processes file line by line.
         * @since 1.1.0
         */
        private static final class ScanContext {
            /**
             * Charts and their versions which are contained in index file.
             */
            private final Map<String, Set<String>> vrsns = new HashMap<>();

            /**
             * Is it an entries section?
             */
            private boolean inentries;

            /**
             * Indent in yaml file.
             */
            private int indent;

            /**
             * Latest valid parsed name of chart from index file.
             */
            private String name;

            /**
             * Update value of location of latest written line.
             * @param inentrs Is it an entries section?
             */
            private void setEntries(final boolean inentrs) {
                this.inentries = inentrs;
            }

            /**
             * Update value of indent.
             * @param cindent New indent
             */
            private void setIndent(final int cindent) {
                this.indent = cindent;
            }

            /**
             * Update value of name.
             * @param cname New name
             */
            private void setName(final String cname) {
                this.name = cname;
            }

            /**
             * Add a version for chart by name which is saved in current context.
             * @param version New version
             */
            private void addChartVersion(final String version) {
                final Set<String> existed = this.vrsns.computeIfAbsent(
                    this.name, none -> new HashSet<>()
                );
                existed.add(version);
                this.vrsns.put(this.name, existed);
            }

            /**
             * Obtains versions for charts from index file.
             * @return Charts and their versions which are contained in index file.
             */
            private Map<String, Set<String>> chartVersions() {
                return Collections.unmodifiableMap(this.vrsns);
            }
        }
    }
}
