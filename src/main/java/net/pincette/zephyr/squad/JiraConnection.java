package net.pincette.zephyr.squad;

import static java.lang.String.join;
import static java.lang.String.valueOf;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.Base64.getEncoder;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static net.pincette.json.Factory.f;
import static net.pincette.json.Factory.o;
import static net.pincette.json.Factory.v;
import static net.pincette.json.JsonUtil.createObjectBuilder;
import static net.pincette.json.JsonUtil.getArray;
import static net.pincette.json.JsonUtil.getObject;
import static net.pincette.json.JsonUtil.getObjects;
import static net.pincette.json.JsonUtil.getString;
import static net.pincette.json.JsonUtil.objects;
import static net.pincette.util.Builder.create;
import static net.pincette.util.Collections.list;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Collections.set;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.Util.tryToGetRethrow;
import static net.pincette.zephyr.squad.Execution.FAILED;
import static net.pincette.zephyr.squad.Execution.IN_PROGRESS;
import static net.pincette.zephyr.squad.Execution.NOT_EXECUTED;
import static net.pincette.zephyr.squad.Execution.SUCCESS;
import static net.pincette.zephyr.squad.Http.getJson;
import static net.pincette.zephyr.squad.Http.postJson;
import static net.pincette.zephyr.squad.Http.putJson;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import net.pincette.json.JsonUtil;
import net.pincette.util.AsyncBuilder;
import net.pincette.zephyr.squad.Http.JsonResponse;

class JiraConnection {
  private static final String COMMENT = "comment";
  private static final String CYCLE_ID = "cycleId";
  private static final String END_DATE = "endDate";
  private static final String FAIL = "FAIL";
  private static final String ID = "id";
  private static final String ISSUES = "issues";
  private static final String ISSUE_ID = "issueId";
  private static final String ISSUE_TYPE = "testcaseIssueTypeId";
  private static final String KEY = "key";
  private static final String LABEL = "label";
  private static final String NAME = "name";
  private static final String OPTIONS = "options";
  private static final String OUTWARD_ISSUE = "outwardIssue";
  private static final String PASS = "PASS";
  private static final String PROJECT_ID = "projectId";
  private static final String RELEASED_VERSIONS = "releasedVersions";
  private static final String START_DATE = "startDate";
  private static final String STATUS = "status";
  private static final String TEST = "Test";
  private static final String UNEXECUTED = "UNEXECUTED";
  private static final String UNRELEASED_VERSIONS = "unreleasedVersions";
  private static final String VALUE = "value";
  private static final String VERSION_ID = "versionId";
  private static final String WIP = "WIP";
  private static final Map<String, Execution> EXECUTION_MAP =
      map(
          pair(FAIL, FAILED),
          pair(PASS, SUCCESS),
          pair(UNEXECUTED, NOT_EXECUTED),
          pair(WIP, IN_PROGRESS));
  private static final Set<String> KNOWN_EXECUTIONS = set(FAIL, PASS, UNEXECUTED, WIP);

  private final Map<String, List<String>> authorization;
  private final String jiraEndpoint;
  private final String zephyrEndpoint;

  JiraConnection(
      final String jiraEndpoint,
      final String zephyrEndpoint,
      final String username,
      final String password) {
    this.jiraEndpoint = removeTrailingSlash(jiraEndpoint);
    this.zephyrEndpoint = removeTrailingSlash(zephyrEndpoint);
    this.authorization = authorization(username, password);
  }

  private static JsonObject addDetails(
      final JsonObject testExecution,
      final Testcase testcase,
      final Map<Execution, Integer> statuses) {
    return create(() -> createObjectBuilder(testExecution))
        .update(b -> b.add(STATUS, valueOf(statuses.get(testcase.execution))))
        .updateIf(() -> ofNullable(testcase.message), (b, v) -> b.add(COMMENT, v))
        .build()
        .build();
  }

  private static Optional<JsonArray> asArray(final JsonResponse response) {
    return ok(response).filter(JsonUtil::isArray).map(JsonValue::asJsonArray);
  }

  private static Optional<JsonObject> asObject(final JsonResponse response) {
    return ok(response).filter(JsonUtil::isObject).map(JsonValue::asJsonObject);
  }

  private static Map<String, List<String>> authorization(
      final String username, final String password) {
    return map(
        pair(
            "Authorization",
            list(
                "Basic "
                    + getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8)))));
  }

  private static String componentQuery(final Set<String> components) {
    return "component in (" + join(",", components) + ")";
  }

  private static String day(final Instant instant) {
    return new SimpleDateFormat("d/MMM/yy").format(new Date(instant.toEpochMilli()));
  }

  private static String encodedQuery(final String query) {
    return tryToGetRethrow(() -> encode(query, "UTF-8")).orElse(null);
  }

  private static String epicQuery(final Set<String> epics) {
    return "\"epic link\" in (" + join(",", epics) + ")";
  }

  private static Optional<String> findProjectId(final JsonObject projects, final String name) {
    return idFromName(getObjects(projects, OPTIONS), name);
  }

  private static Optional<JsonObject> findTestExecution(final JsonObject executions) {
    return ofNullable(executions)
        .map(Map::values)
        .map(Collection::stream)
        .flatMap(Stream::findFirst)
        .filter(JsonUtil::isObject)
        .map(JsonValue::asJsonObject);
  }

  private static Optional<String> findVersionId(final JsonObject versions, final String name) {
    return idFromName(
        concat(getObjects(versions, RELEASED_VERSIONS), getObjects(versions, UNRELEASED_VERSIONS)),
        name);
  }

  private static String getTestIssueKey(final JsonObject issueLink) {
    return getObject(issueLink, "/" + OUTWARD_ISSUE)
        .filter(
            outward ->
                getString(outward, "/fields/issuetype/name")
                    .map(name -> name.equals(TEST))
                    .orElse(false))
        .map(outward -> outward.getString(KEY, null))
        .orElse(null);
  }

  private static Optional<String> idFromName(final Stream<JsonObject> objects, final String name) {
    return objects
        .filter(p -> name.equals(p.getString(LABEL, null)))
        .map(p -> p.getString(VALUE, null))
        .filter(Objects::nonNull)
        .findFirst();
  }

  private static Optional<JsonStructure> ok(final JsonResponse response) {
    return Optional.of(response).filter(r -> r.statusCode == 200).map(r -> r.json);
  }

  private static String versionId(final String id) {
    return ofNullable(id).orElse("-1");
  }

  private static String removeTrailingSlash(final String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static String uri(final String endpoint, final String path) {
    return endpoint + (!path.startsWith("/") ? "/" : "") + path;
  }

  CompletionStage<Optional<String>> createTestCycle(
      final String projectId, final String versionId) {
    final Instant now = now();

    return postJson(
            uri(zephyrEndpoint, "/cycle"),
            authorization,
            o(
                f(NAME, v(now.toString())),
                f(PROJECT_ID, v(projectId)),
                f(VERSION_ID, v(versionId(versionId))),
                f(START_DATE, v(day(now))),
                f(END_DATE, v(day(now)))))
        .thenApply(JiraConnection::asObject)
        .thenApply(cycle -> cycle.map(c -> c.getString(ID, null)));
  }

  CompletionStage<Optional<Cycle>> createTestCycleFromNames(
      final String project, final String version) {
    return AsyncBuilder.create(Cycle::new)
        .update(
            cycle ->
                getTestExecutionStatuses()
                    .thenApply(statuses -> statuses.map(cycle::withExecutionStatuses)))
        .update(cycle -> getProjectId(project).thenApply(id -> id.map(cycle::withProjectId)))
        .update(
            cycle ->
                getVersionId(cycle.projectId, version)
                    .thenApply(id -> id.map(cycle::withVersionId)))
        .update(
            cycle ->
                createTestCycle(cycle.projectId, cycle.versionId)
                    .thenApply(id -> id.map(cycle::withCycleId)))
        .build();
  }

  CompletionStage<Optional<Integer>> createTestExecution(
      final Cycle cycle, final String issueId, final Testcase testcase) {
    return postJson(
            uri(zephyrEndpoint, "/execution"),
            authorization,
            o(
                f(PROJECT_ID, v(cycle.projectId)),
                f(VERSION_ID, v(versionId(cycle.versionId))),
                f(ISSUE_ID, v(issueId)),
                f(CYCLE_ID, v(cycle.cycleId))))
        .thenApply(JiraConnection::asObject)
        .thenApply(exec -> findTestExecution(exec.orElse(null)))
        .thenComposeAsync(
            exec ->
                exec.filter(e -> e.containsKey(ID))
                    .map(e -> addDetails(e, testcase, cycle.executionStatuses))
                    .map(
                        e ->
                            putJson(
                                    uri(zephyrEndpoint, "/execution/" + e.getInt(ID) + "/execute"),
                                    authorization,
                                    e)
                                .thenApply(JiraConnection::asObject)
                                .thenApply(updated -> updated.map(o -> o.getInt(ID))))
                    .orElseGet(() -> completedFuture(Optional.empty())));
  }

  CompletionStage<Optional<JsonObject>> getIssue(final String key) {
    return getJson(uri(jiraEndpoint, "/issue/" + key + "?fields=id,key,issuetype"), authorization)
        .thenApply(JiraConnection::asObject);
  }

  CompletionStage<Optional<Map<Execution, Integer>>> getTestExecutionStatuses() {
    return getJson(uri(zephyrEndpoint, "/util/testExecutionStatus"), authorization)
        .thenApply(JiraConnection::asArray)
        .thenApply(
            statuses ->
                statuses.map(
                    s ->
                        objects(s)
                            .filter(o -> KNOWN_EXECUTIONS.contains(o.getString(NAME)))
                            .collect(
                                toMap(
                                    o -> EXECUTION_MAP.get(o.getString(NAME)),
                                    o -> o.getInt(ID)))));
  }

  CompletionStage<Optional<JsonObject>> getTestIssue(final String key) {
    return getIssue(key)
        .thenComposeAsync(
            issue ->
                issue
                    .map(
                        i ->
                            getTestIssueType()
                                .thenApply(type -> type.map(t -> pair(i, t)).orElse(null)))
                    .orElseGet(() -> completedFuture(null)))
        .thenApply(pair -> ofNullable(pair).map(p -> p.first));
  }

  CompletionStage<Optional<String>> getTestIssueType() {
    return getJson(uri(zephyrEndpoint, "/util/zephyrTestIssueType"), authorization)
        .thenApply(JiraConnection::asObject)
        .thenApply(object -> object.map(o -> o.getString(ISSUE_TYPE, null)));
  }

  CompletionStage<Optional<Set<String>>> getTestIssueKeysForComponents(
      final Set<String> components) {
    return getJson(
            uri(
                jiraEndpoint,
                "/search?fields=key&jql="
                    + encodedQuery(componentQuery(components))
                    + " AND type = Test"),
            authorization)
        .thenApply(JiraConnection::asObject)
        .thenApply(
            json ->
                json.map(
                    j ->
                        getObjects(j, ISSUES)
                            .map(o -> o.getString(KEY, null))
                            .filter(Objects::nonNull)
                            .collect(toSet())));
  }

  CompletionStage<Optional<Set<String>>> getTestIssueKeysForEpics(final Set<String> epics) {
    return getJson(
            uri(
                jiraEndpoint,
                "/search?fields=issuelinks&jql="
                    + encodedQuery(epicQuery(epics))
                    + " AND type = Story"),
            authorization)
        .thenApply(JiraConnection::asObject)
        .thenApply(
            json ->
                json.map(
                    j ->
                        getObjects(j, ISSUES)
                            .map(o -> getArray(o, "/fields/issuelinks"))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .flatMap(JsonArray::stream)
                            .filter(JsonUtil::isObject)
                            .map(JsonValue::asJsonObject)
                            .map(JiraConnection::getTestIssueKey)
                            .filter(Objects::nonNull)
                            .collect(toSet())));
  }

  CompletionStage<Optional<String>> getProjectId(final String name) {
    return getJson(uri(zephyrEndpoint, "/util/project-list"), authorization)
        .thenApply(JiraConnection::asObject)
        .thenApply(projects -> projects.flatMap(p -> findProjectId(p, name)));
  }

  CompletionStage<Optional<String>> getVersionId(final String projectId, final String name) {
    return ofNullable(name)
        .map(
            n ->
                getJson(
                        uri(zephyrEndpoint, "/util/versionBoard-list?projectId=" + projectId),
                        authorization)
                    .thenApply(JiraConnection::asObject)
                    .thenApply(versions -> versions.flatMap(v -> findVersionId(v, n))))
        .orElseGet(() -> completedFuture(Optional.of("-1")));
  }
}
