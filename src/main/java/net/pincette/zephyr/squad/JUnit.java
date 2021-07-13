package net.pincette.zephyr.squad;

import static java.lang.Math.round;
import static java.util.Arrays.stream;
import static java.util.regex.Pattern.compile;
import static net.pincette.util.Builder.create;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.Util.tryToGetRethrow;
import static net.pincette.xml.Util.children;
import static net.pincette.xml.Util.secureDocumentBuilderFactory;
import static net.pincette.zephyr.squad.Execution.FAILED;
import static net.pincette.zephyr.squad.Execution.NOT_EXECUTED;
import static net.pincette.zephyr.squad.Execution.SUCCESS;

import java.io.File;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The test result loader for JUnit.
 *
 * @author Werner Donn\u00e9
 */
public class JUnit {
  private static final String ERROR = "error";
  private static final String FAILURE = "failure";
  private static final Pattern KEY = compile("([A-Z]+[_\\-][0-9]+).*");
  private static final String MESSAGE = "message";
  private static final String NAME = "name";
  private static final String SKIPPED = "skipped";
  private static final String SYSTEM_ERR = "system-err";
  private static final String SYSTEM_OUT = "system-out";
  private static final String TESTCASE = "testcase";
  private static final String TIME = "time";

  private JUnit() {}

  private static Execution execution(final Element testcase) {
    final Supplier<Execution> trySkipped = () -> isSkipped(testcase) ? NOT_EXECUTED : SUCCESS;

    return isError(testcase) ? FAILED : trySkipped.get();
  }

  private static Optional<Element> findElement(final Node node, final String name) {
    return children(node)
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .filter(n -> name.equals(n.getNodeName()))
        .findFirst();
  }

  private static Optional<Element> getError(final Node testcase) {
    return findElement(testcase, ERROR);
  }

  private static String getErrorMessage(final Element element) {
    return element.getAttribute(MESSAGE) + "\n" + element.getTextContent();
  }

  private static Optional<Duration> getExecutionTime(final Element testcase) {
    return Optional.of(testcase.getAttribute(TIME))
        .filter(t -> !t.equals(""))
        .map(Float::parseFloat)
        .map(t -> round(t * 1000))
        .map(Duration::ofMillis);
  }

  private static Optional<Element> getFailure(final Node testcase) {
    return findElement(testcase, FAILURE);
  }

  private static Optional<String> getMessage(final Element testcase) {
    return Optional.of(
            create(StringBuilder::new)
                .updateIf(
                    () -> getFailure(testcase), (b, v) -> b.append(getErrorMessage(v)).append('\n'))
                .updateIf(
                    () -> getError(testcase), (b, v) -> b.append(getErrorMessage(v)).append('\n'))
                .updateIf(
                    () -> findElement(testcase, SYSTEM_OUT),
                    (b, v) -> b.append(v.getTextContent()).append('\n'))
                .updateIf(
                    () -> findElement(testcase, SYSTEM_ERR),
                    (b, v) -> b.append(v.getTextContent()).append('\n'))
                .build()
                .toString())
        .filter(s -> s.length() > 0);
  }

  private static boolean isError(final Node testcase) {
    return getFailure(testcase).isPresent() || getError(testcase).isPresent();
  }

  private static boolean isSkipped(final Node testcase) {
    return findElement(testcase, SKIPPED).isPresent();
  }

  private static Stream<Testcase> loadResults(final File results) {
    return testcases(readResults(results))
        .map(e -> pair(testcaseKey(e.getAttribute(NAME)).orElse(null), e))
        .filter(pair -> pair.first != null)
        .map(
            pair ->
                new Testcase()
                    .withKey(pair.first)
                    .withExecution(execution(pair.second))
                    .withMessage(getMessage(pair.second).orElse(null))
                    .withExecutionTime(getExecutionTime(pair.second).orElse(null)));
  }

  /**
   * Creates a stream of testcases from the given files.
   *
   * @param results the test result files.
   * @return The testcase stream.
   */
  public static Stream<Testcase> loadTestcases(final File[] results) {
    return stream(results).flatMap(JUnit::loadResults);
  }

  private static Stream<Node> readResults(final File results) {
    return tryToGetRethrow(
            () ->
                secureDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(results)
                    .getDocumentElement())
        .map(net.pincette.xml.Util::children)
        .orElseGet(Stream::empty);
  }

  private static Optional<String> testcaseKey(final String name) {
    return Optional.of(KEY.matcher(name))
        .filter(Matcher::matches)
        .map(matcher -> matcher.group(1).replace('_', '-'));
  }

  private static Stream<Element> testcases(final Stream<Node> nodes) {
    return nodes
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .filter(n -> TESTCASE.equals(n.getNodeName()));
  }
}
