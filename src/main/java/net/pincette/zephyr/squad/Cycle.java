package net.pincette.zephyr.squad;

import java.util.Map;

class Cycle {
  final String cycleId;
  final Map<Execution, Integer> executionStatuses;
  final String projectId;
  final String versionId;

  Cycle() {
    this(null, null, null, null);
  }

  private Cycle(
      final String projectId,
      final String versionId,
      final String cycleId,
      final Map<Execution, Integer> executionStatuses) {
    this.projectId = projectId;
    this.versionId = versionId;
    this.cycleId = cycleId;
    this.executionStatuses = executionStatuses;
  }

  Cycle withCycleId(final String cycleId) {
    return new Cycle(projectId, versionId, cycleId, executionStatuses);
  }

  Cycle withExecutionStatuses(final Map<Execution, Integer> executionStatuses) {
    return new Cycle(projectId, versionId, cycleId, executionStatuses);
  }

  Cycle withProjectId(final String projectId) {
    return new Cycle(projectId, versionId, cycleId, executionStatuses);
  }

  Cycle withVersionId(final String versionId) {
    return new Cycle(projectId, versionId, cycleId, executionStatuses);
  }
}
