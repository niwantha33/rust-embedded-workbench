// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** HTTPS-only, fixed-origin, bounded, cancel-aware GitHub contents transport. */
public final class BoundedUpdateHttp {
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15)).build();

    public static void requireTrustedOrigin(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !"api.github.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getPath() == null
                || !uri.getPath().startsWith("/repos/niwantha33/rust-embedded-workbench/contents/"))
            throw new IOException("Untrusted update source.");
    }

    public byte[] get(URI uri, int limit, Duration timeout, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        requireTrustedOrigin(uri);
        if (limit < 1 || limit > UpdateManifest.MAX_FILE_BYTES
                || timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0)
            throw new IllegalArgumentException("Invalid update transfer bounds.");
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("Update cancelled.");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET().timeout(timeout)
                .header("Accept", "application/vnd.github.raw+json")
                .header("User-Agent", "Rust-Embedded-Workbench")
                .build();
        var future = client.sendAsync(request, info -> new LimitedBody(limit));
        long started = System.nanoTime();
        try {
            while (true) {
                if (cancelled.getAsBoolean()) throw new InterruptedIOException("Update cancelled.");
                if (System.nanoTime() - started > timeout.toNanos())
                    throw new IOException("Update transfer timed out.");
                try {
                    var response = future.get(100, TimeUnit.MILLISECONDS);
                    if (response.statusCode() != 200)
                        throw new IOException("Update download failed with HTTP " + response.statusCode()
                                + ". Redirects are not followed.");
                    return response.body();
                } catch (TimeoutException pending) {
                    // Check deadline and cancellation while transfer proceeds.
                } catch (ExecutionException error) {
                    throw new IOException("Secure update transfer failed.", error.getCause());
                }
            }
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> done = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return done; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > max) {
                    subscription.cancel();
                    done.completeExceptionally(new IOException("Update exceeds download size limit."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { done.completeExceptionally(error); }
        @Override public void onComplete() { done.complete(bytes.toByteArray()); }
    }
}
