# Zephyr Squad Uploader

With this small library you can upload test report files to Zephyr Squad as test executions in a generated test cycle. Currently only JUnit is supported. Your testcases should match with Zephyr test issues. Therefore, the names of the test methods should start with an issue key, but with an underscore instead of a dash.

When no component or epic names are given, all matching testcases are uploaded as test executions with either PASS or FAIL status. Otherwise the test issues that are related to either the components or the epics are retrieved. Issues for which there is no testcase will be marked as UNEXECUTED. In the case of epics, the test issues should be related to story issues that are part of the epic. When epics are given, components are ignored.

Find more details in the [API documentation](https://www.javadoc.io/doc/net.pincette/pincette-zephyr-squad/latest/index.html).