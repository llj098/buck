/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.rules;

import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.java.FakeJavaPackageFinder;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.step.DefaultStepRunner;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.timing.DefaultClock;
import com.google.common.collect.ImmutableList;

/**
 * Facilitates creating a fake {@link BuildContext} for unit tests.
 */
public class FakeBuildContext {

  /** Utility class: do not instantiate. */
  private FakeBuildContext() {}

  /** A BuildContext which doesn't touch the host filesystem or actually execute steps. */
  public static final BuildContext NOOP_CONTEXT = newBuilder(new FakeProjectFilesystem())
      .setActionGraph(new ActionGraph(ImmutableList.<BuildRule>of()))
      .setJavaPackageFinder(new FakeJavaPackageFinder())
      .setArtifactCache(new NoopArtifactCache())
      .build();

  /**
   * User still needs to invoke {@link ImmutableBuildContext.Builder#setActionGraph(ActionGraph)}
   * and {@link ImmutableBuildContext.Builder#setJavaPackageFinder(
   * com.facebook.buck.java.JavaPackageFinder)}
   * before the {@link ImmutableBuildContext.Builder#build()} method of the builder can be invoked.
   * @param projectFilesystem for the {@link BuildContext} and for the {@link ExecutionContext} that
   *     is passed to the {@link DefaultStepRunner} for the {@link BuildContext}.
   */
  public static ImmutableBuildContext.Builder newBuilder(ProjectFilesystem projectFilesystem) {
    ExecutionContext executionContext = TestExecutionContext
        .newBuilder()
        .setProjectFilesystem(projectFilesystem)
        .build();

    return ImmutableBuildContext.builder()
        .setStepRunner(new DefaultStepRunner(executionContext))
        .setProjectFilesystem(projectFilesystem)
        .setClock(new DefaultClock())
        .setBuildId(new BuildId())
        .setArtifactCache(new NoopArtifactCache())
        .setEventBus(BuckEventBusFactory.newInstance())
        .setBuildDependencies(BuildDependencies.FIRST_ORDER_ONLY);
  }
}
