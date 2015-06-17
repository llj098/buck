/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.event.FakeBuckEventListener;
import com.facebook.buck.model.BuildTarget;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

public class DefaultStepRunnerTest {

  @Test
  public void testEventsFired() throws StepFailedException, InterruptedException, IOException {
    Step passingStep = new FakeStep("step1", "fake step 1", 0);
    Step failingStep = new FakeStep("step1", "fake step 1", 1);

    // The EventBus should be updated with events indicating how the steps were run.
    BuckEventBus eventBus = BuckEventBusFactory.newInstance();
    FakeBuckEventListener listener = new FakeBuckEventListener();
    eventBus.register(listener);

    ExecutionContext context = TestExecutionContext.newBuilder()
        .setEventBus(eventBus)
        .build();
    DefaultStepRunner runner = new DefaultStepRunner(context);
    runner.runStepForBuildTarget(passingStep, Optional.<BuildTarget>absent());
    try {
      runner.runStepForBuildTarget(failingStep, Optional.<BuildTarget>absent());
      fail("Failing step should have thrown an exception");
    } catch (StepFailedException e) {
      assertEquals(e.getStep(), failingStep);
    }

    ImmutableList<StepEvent> events = FluentIterable.from(listener.getEvents())
        .filter(StepEvent.class)
        .toList();
    assertEquals(4, events.size());

    assertTrue(events.get(0) instanceof StepEvent.Started);
    assertEquals(events.get(0).getShortStepName(), "step1");
    assertEquals(events.get(0).getDescription(), "fake step 1");

    assertTrue(events.get(1) instanceof StepEvent.Finished);
    assertEquals(events.get(1).getShortStepName(), "step1");
    assertEquals(events.get(1).getDescription(), "fake step 1");

    assertTrue(events.get(2) instanceof StepEvent.Started);
    assertEquals(events.get(2).getShortStepName(), "step1");
    assertEquals(events.get(2).getDescription(), "fake step 1");

    assertTrue(events.get(3) instanceof StepEvent.Finished);
    assertEquals(events.get(3).getShortStepName(), "step1");
    assertEquals(events.get(3).getDescription(), "fake step 1");

    assertTrue(events.get(0).isRelatedTo(events.get(1)));
    assertTrue(events.get(2).isRelatedTo(events.get(3)));

    assertFalse(events.get(0).isRelatedTo(events.get(2)));
    assertFalse(events.get(0).isRelatedTo(events.get(3)));
    assertFalse(events.get(1).isRelatedTo(events.get(2)));
    assertFalse(events.get(1).isRelatedTo(events.get(3)));
  }

  @Test(expected = StepFailedException.class, timeout = 5000)
  public void testParallelStepFailure()
      throws StepFailedException, InterruptedException, IOException {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    steps.add(new SleepingStep(0, 0));
    steps.add(new SleepingStep(10, 1));
    // Add a step that will also fail, but taking longer than the test timeout to complete.
    // This tests the fail-fast behaviour of runStepsInParallelAndWait (that is, since the 10ms
    // step will fail so quickly, the result of the 5000ms step will not be observed).
    steps.add(new SleepingStep(5000, 1));

    ListeningExecutorService service =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3));
    DefaultStepRunner runner = new DefaultStepRunner(TestExecutionContext.newInstance());
    runner.runStepsInParallelAndWait(
        steps.build(),
        Optional.<BuildTarget>absent(),
        service,
        StepRunner.NOOP_CALLBACK);

    // Success if the test timeout is not reached.
  }

  @Test
  public void testExplodingStep() throws InterruptedException, IOException {
    ExecutionContext context = TestExecutionContext.newInstance();

    DefaultStepRunner runner = new DefaultStepRunner(context);
    try {
      runner.runStepForBuildTarget(new ExplosionStep(), Optional.<BuildTarget>absent());
      fail("Should have thrown a StepFailedException!");
    } catch (StepFailedException e) {
      assertTrue(e.getMessage().startsWith("Failed on step explode with an exception:\n#yolo"));
    }
  }

  private static class ExplosionStep implements Step {
    @Override
    public int execute(ExecutionContext context) {
      throw new RuntimeException("#yolo");
    }

    @Override
    public String getShortName() {
      return "explode";
    }

    @Override
    public String getDescription(ExecutionContext context) {
      return "MOAR EXPLOSIONS!!!!";
    }
  }

  private static class SleepingStep implements Step {
    private final long sleepMillis;
    private final int exitCode;

    public SleepingStep(long sleepMillis, int exitCode) {
      this.sleepMillis = sleepMillis;
      this.exitCode = exitCode;
    }

    @Override
    public int execute(ExecutionContext context) throws InterruptedException {
      Thread.sleep(sleepMillis);
      return exitCode;
    }

    @Override
    public String getShortName() {
      return "sleep";
    }

    @Override
    public String getDescription(ExecutionContext context) {
      return String.format("%s %d, then %s",
          getShortName(),
          sleepMillis,
          exitCode == 0 ? "success" : "fail");
    }
  }
}
