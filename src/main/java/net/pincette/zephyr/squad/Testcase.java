package net.pincette.zephyr.squad;

import static net.pincette.zephyr.squad.Execution.NOT_EXECUTED;

import java.time.Duration;

/**
 * Represents a testcase.
 *
 * @author Werner Donn\u00e9
 */
public class Testcase {
  public final Execution execution;
  public final Duration executionTime;
  public final String message;
  public final String key;

  public Testcase() {
    this(null, NOT_EXECUTED, null, null);
  }

  private Testcase(
      final String key,
      final Execution execution,
      final String message,
      final Duration executionTime) {
    this.key = key;
    this.execution = execution;
    this.message = message;
    this.executionTime = executionTime;
  }

  /**
   * The execution status of the testcase.
   *
   * @param execution the status.
   * @return A new testcase.
   */
  public Testcase withExecution(final Execution execution) {
    return new Testcase(key, execution, message, executionTime);
  }

  public Testcase withExecutionTime(final Duration executionTime) {
    return new Testcase(key, execution, message, executionTime);
  }

  /**
   * The key of the corresponding Zephyr test issue. If it doesn't exist then the testcase won't be
   * uploaded.
   *
   * @param key the issue key.
   * @return A new testcase.
   */
  public Testcase withKey(final String key) {
    return new Testcase(key, execution, message, executionTime);
  }

  /**
   * This will be added as a comment in the test execution.
   *
   * @param message the message.
   * @return A new testcase.
   */
  public Testcase withMessage(final String message) {
    return new Testcase(key, execution, message, executionTime);
  }
}
