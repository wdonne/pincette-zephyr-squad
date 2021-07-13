package net.pincette.zephyr.squad;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toSet;
import static net.pincette.util.Collections.difference;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.StreamUtil.composeAsyncSuppliers;
import static net.pincette.zephyr.squad.Execution.IN_PROGRESS;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Uploads a collection of testcases to Zephyr Squad in a generated test cycle. Testcases with keys
 * that can't be found as Zephyr test issues are not uploaded.
 *
 * @author Werner Donn\u00e9
 */
public class Uploader {
  private static final String ID = "id";

  public final Set<String> components;
  public final Set<String> epics;
  public final String jiraEndpoint;
  public final String password;
  public final String project;
  public final String username;
  public final String version;
  public final String zephyrEndpoint;

  public Uploader() {
    this(null, null, null, null, null, null, null, null);
  }

  @SuppressWarnings("java:S107") // Immutable object. This is private.
  private Uploader(
      final String project,
      final String version,
      final Set<String> epics,
      Set<String> components,
      final String jiraEndpoint,
      final String zehpyrEndpoint,
      final String username,
      final String password) {
    this.project = project;
    this.version = version;
    this.epics = epics;
    this.components = components;
    this.jiraEndpoint = jiraEndpoint;
    this.zephyrEndpoint = zehpyrEndpoint;
    this.username = username;
    this.password = password;
  }

  private static CompletionStage<Optional<String>> sendTestExecution(
      final Cycle cycle, final Testcase testcase, final JiraConnection connection) {
    return connection
        .getTestIssue(testcase.key)
        .thenComposeAsync(
            issue ->
                issue
                    .map(i -> i.getString(ID, null))
                    .map(
                        id ->
                            connection
                                .createTestExecution(cycle, id, testcase)
                                .thenApply(result -> result.map(r -> testcase.key)))
                    .orElseGet(() -> completedFuture(Optional.empty())));
  }

  private static CompletionStage<Set<String>> sendTestExecutions(
      final Cycle cycle, final Stream<Testcase> testcases, final JiraConnection connection) {
    final Function<Testcase, Supplier<CompletionStage<Optional<String>>>> send =
        testcase -> () -> sendTestExecution(cycle, testcase, connection);

    return composeAsyncSuppliers(testcases.map(send))
        .thenApply(
            results -> results.filter(Optional::isPresent).map(Optional::get).collect(toSet()));
  }

  private CompletionStage<Boolean> addNotExecuted(
      final Set<String> executed, final Cycle cycle, final JiraConnection connection) {
    final Supplier<CompletionStage<Optional<Set<String>>>> tryComponents =
        () ->
            components != null
                ? connection.getTestIssueKeysForComponents(components)
                : completedFuture(Optional.empty());

    return (epics != null ? connection.getTestIssueKeysForEpics(epics) : tryComponents.get())
        .thenComposeAsync(
            keys ->
                keys.map(
                        key ->
                            difference(key, executed).stream()
                                .map(k -> new Testcase().withKey(k).withExecution(IN_PROGRESS)))
                    .map(
                        testcases ->
                            sendTestExecutions(cycle, testcases, connection)
                                .thenApply(result -> true))
                    .orElseGet(() -> completedFuture(true)));
  }

  /**
   * Creates a test cycle and puts the testcases in it as test executions.
   *
   * @param testcases the testcases.
   * @return An indication of success.
   */
  public CompletionStage<Boolean> upload(final Stream<Testcase> testcases) {
    final JiraConnection connection =
        new JiraConnection(jiraEndpoint, zephyrEndpoint, username, password);

    return connection
        .createTestCycleFromNames(project, version)
        .thenComposeAsync(
            cycle ->
                cycle
                    .map(c -> pair(c, testcases))
                    .map(
                        pair ->
                            sendTestExecutions(pair.first, pair.second, connection)
                                .thenComposeAsync(
                                    executed -> addNotExecuted(executed, pair.first, connection)))
                    .orElseGet(() -> completedFuture(false)));
  }

  /**
   * An optional set of component names. When given, all the Zephyr test issues that are related to
   * one of the components are considered. Those for which no testcase was provided will be marked
   * as <code>UNEXECUTED</code>.
   *
   * @param components the set of components.
   * @return A new uploader.
   */
  public Uploader withComponents(final Set<String> components) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * An optional set of epic names. When given, all the Zephyr test issues that are related to one
   * of the epics are considered. Related means that they are connected to a story issue that is
   * part of the epic. Those test issues for which no testcase was provided will be marked as <code>
   * UNEXECUTED</code>. When this option is set the <code>components</code> option will be ignored.
   *
   * @param epics the set of epics.
   * @return A new uploader.
   */
  public Uploader withEpics(final Set<String> epics) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The mandatory URL that represents the JIRA REST API.
   *
   * @param jiraEndpoint the URL.
   * @return A new uploader.
   */
  public Uploader withJiraEndpoint(final String jiraEndpoint) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The JIRA password.
   *
   * @param password the password.
   * @return A new uploader.
   */
  public Uploader withPassword(final String password) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The mandatory JIRA project name.
   *
   * @param project the project.
   * @return A new uploader.
   */
  public Uploader withProject(final String project) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The JIRA username.
   *
   * @param username the username.
   * @return A new uploader.
   */
  public Uploader withUsername(final String username) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The optional JIRA project version. When it is not given the test cycle will go under
   * "Unscheduled".
   *
   * @param version the version.
   * @return A new uploader.
   */
  public Uploader withVersion(final String version) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }

  /**
   * The mandatory URL that represents the Zephyr REST API on the JIRA server.
   *
   * @param zephyrEndpoint the URL.
   * @return A new uploader.
   */
  public Uploader withZephyrEndpoint(final String zephyrEndpoint) {
    return new Uploader(
        project, version, epics, components, jiraEndpoint, zephyrEndpoint, username, password);
  }
}
