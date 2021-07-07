package net.pincette.zephyr.squad;

import static java.lang.System.getProperty;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Logger.getGlobal;
import static net.pincette.json.JsonUtil.createReader;
import static net.pincette.json.JsonUtil.createWriter;
import static net.pincette.json.JsonUtil.string;
import static net.pincette.util.StreamUtil.stream;
import static net.pincette.util.Util.must;
import static net.pincette.util.Util.tryToGetRethrow;
import static net.pincette.util.Util.tryToGetWithSilent;
import static org.asynchttpclient.Dsl.asyncHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.RequestBuilder;

class Http {
  private static final String JSON = "application/json";
  private static final String OCTET_STREAM = "application/octet-stream";
  private static final boolean TRACE = Boolean.parseBoolean(getProperty("http.trace"));

  private static final AsyncHttpClient client = asyncHttpClient();

  private Http() {}

  private static RequestBuilder addBody(
      final RequestBuilder builder, final Consumer<OutputStream> body, final String contentType) {
    final Supplier<String> type = () -> contentType != null ? contentType : OCTET_STREAM;

    return body != null
        ? builder.addHeader("Content-Type", type.get()).setBody(getContent(body))
        : builder;
  }

  static CompletionStage<Response> delete(
      final String uri, final Map<String, List<String>> headers) {
    return request(uri, "DELETE", headers, out -> {}, null);
  }

  static CompletionStage<Response> get(final String uri, final Map<String, List<String>> headers) {
    return request(uri, "GET", headers, out -> {}, null);
  }

  private static byte[] getContent(final Consumer<OutputStream> body) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    body.accept(out);

    return out.toByteArray();
  }

  static CompletionStage<JsonResponse> getJson(
      final String uri, final Map<String, List<String>> headers) {
    return requestJson(uri, "GET", headers, null);
  }

  static CompletionStage<Response> head(final String uri, final Map<String, List<String>> headers) {
    return request(uri, "HEAD", headers, out -> {}, null);
  }

  private static Response logError(final String uri, final String method, final Response response) {
    if (response.statusCode >= 400) {
      getGlobal()
          .log(
              SEVERE,
              "{0} on {1} failed with status code {2}.",
              new Object[] {method, uri, response.statusCode});
    }

    return response;
  }

  static CompletionStage<Response> options(
      final String uri, final Map<String, List<String>> headers) {
    return request(uri, "OPTIONS", headers, out -> {}, null);
  }

  static CompletionStage<Response> patch(
      final String uri,
      final Map<String, List<String>> headers,
      final Consumer<OutputStream> body,
      final String contentType) {
    return request(uri, "PATCH", headers, body, contentType);
  }

  static CompletionStage<JsonResponse> patchJson(
      final String uri, final Map<String, List<String>> headers, final JsonStructure body) {
    return requestJson(uri, "PATCH", headers, body);
  }

  static CompletionStage<Response> post(
      final String uri,
      final Map<String, List<String>> headers,
      final Consumer<OutputStream> body,
      final String contentType) {
    return request(uri, "POST", headers, body, contentType);
  }

  static CompletionStage<JsonResponse> postJson(
      final String uri, final Map<String, List<String>> headers, final JsonStructure body) {
    return requestJson(uri, "POST", headers, body);
  }

  static CompletionStage<Response> put(
      final String uri,
      final Map<String, List<String>> headers,
      final Consumer<OutputStream> body,
      final String contentType) {
    return request(uri, "PUT", headers, body, contentType);
  }

  static CompletionStage<JsonResponse> putJson(
      final String uri, final Map<String, List<String>> headers, final JsonStructure body) {
    return requestJson(uri, "PUT", headers, body);
  }

  private static Map<String, List<String>> reduceHeaders(
      final Stream<Map.Entry<String, String>> headers) {
    return headers.collect(
        HashMap::new,
        (map, entry) ->
            map.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue()),
        Map::putAll);
  }

  static CompletionStage<Response> request(
      final String uri,
      final String method,
      final Map<String, List<String>> headers,
      final Consumer<OutputStream> body,
      final String contentType) {
    return request(uri, method, headers, body, contentType, false);
  }

  static CompletionStage<Response> request(
      final String uri,
      final String method,
      final Map<String, List<String>> headers,
      final Consumer<OutputStream> body,
      final String contentType,
      final boolean followRedirect) {
    return tryToGetRethrow(
            () ->
                client
                    .executeRequest(
                        addBody(
                                new RequestBuilder()
                                    .setUrl(uri)
                                    .setHeaders(headers)
                                    .setFollowRedirect(followRedirect)
                                    .setMethod(method),
                                body,
                                contentType)
                            .build())
                    .toCompletableFuture()
                    .thenApply(
                        response ->
                            new Response(
                                response.getStatusCode(),
                                reduceHeaders(stream(response.getHeaders().iteratorAsString())),
                                response.getResponseBodyAsStream(),
                                response.getContentType()))
                    .thenApply(response -> logError(uri, method, response))
                    .thenApply(response -> must(response, r -> r.statusCode < 400)))
        .orElseGet(() -> completedFuture(new Response(500, new HashMap<>(), null, null)));
  }

  static CompletionStage<JsonResponse> requestJson(
      final String uri,
      final String method,
      final Map<String, List<String>> headers,
      final JsonStructure json) {
    trace(() -> method + " " + uri + "\n" + (json != null ? string(json) : ""));

    return request(
            uri,
            method,
            headers,
            json != null ? out -> createWriter(out).write(json) : null,
            json != null ? JSON : null)
        .thenApply(JsonResponse::new);
  }

  private static void trace(final Supplier<String> message) {
    if (TRACE) {
      getGlobal().info(message);
    }
  }

  static class JsonResponse {
    final JsonStructure json;
    final Map<String, List<String>> headers;
    final int statusCode;

    private JsonResponse(final Response response) {
      statusCode = response.statusCode;
      headers = response.headers;
      json =
          ofNullable(response.contentType)
              .filter(type -> type.startsWith(JSON))
              .flatMap(
                  type -> tryToGetWithSilent(() -> createReader(response.body), JsonReader::read))
              .orElse(null);
      trace(() -> "Status code: " + statusCode + "\n" + (json != null ? string(json) : ""));
    }
  }

  static class Response {
    final InputStream body;
    final String contentType;
    final Map<String, List<String>> headers;
    final int statusCode;

    private Response(
        final int statusCode,
        final Map<String, List<String>> headers,
        final InputStream body,
        final String contentType) {
      this.statusCode = statusCode;
      this.headers = headers;
      this.body = body;
      this.contentType = contentType;
    }
  }
}
