/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.rules.keys;

import static org.junit.Assert.assertThat;

import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.shell.ExportFileBuilder;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class InputBasedRuleKeyBuilderFactoryTest {

  @Test
  public void ruleKeyDoesNotChangeWhenOnlyDependencyRuleKeyChanges() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);

    Path depOutput = Paths.get("output");
    FakeBuildRule dep =
        resolver.addToIndex(
            new FakeBuildRule(BuildTargetFactory.newInstance("//:dep"), pathResolver));
    dep.setOutputFile(depOutput.toString());

    FakeFileHashCache hashCache =
        new FakeFileHashCache(
            ImmutableMap.of(depOutput, HashCode.fromInt(0)));

    BuildRule rule =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setOut("out")
            .setDeps(ImmutableSortedSet.of(dep.getBuildTarget()))
            .build(resolver);

    dep.setRuleKey(new RuleKey("aaaa"));
    RuleKey inputKey1 =
        new InputBasedRuleKeyBuilderFactory(hashCache, pathResolver).newInstance(rule).build();

    dep.setRuleKey(new RuleKey("bbbb"));
    RuleKey inputKey2 =
        new InputBasedRuleKeyBuilderFactory(hashCache, pathResolver).newInstance(rule).build();

    assertThat(
        inputKey1,
        Matchers.equalTo(inputKey2));
  }

  @Test
  public void ruleKeyChangesIfInputContentsFromDependencyChanges() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();

    BuildRule dep =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setOut("out")
            .build(resolver, filesystem);

    BuildRule rule =
        ExportFileBuilder.newExportFileBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setOut("out")
            .setSrc(new BuildTargetSourcePath(filesystem, dep.getBuildTarget()))
            .build(resolver, filesystem);

    // Build a rule key with a particular hash set for the output for the above genrule.
    FakeFileHashCache hashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                Preconditions.checkNotNull(dep.getPathToOutput()),
                HashCode.fromInt(0)));
    RuleKey inputKey1 =
        new InputBasedRuleKeyBuilderFactory(hashCache, pathResolver).newInstance(rule).build();

    // Now, build a rule key with a different hash for the output for the above genrule.
    hashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                Preconditions.checkNotNull(dep.getPathToOutput()),
                HashCode.fromInt(1)));
    RuleKey inputKey2 =
        new InputBasedRuleKeyBuilderFactory(hashCache, pathResolver).newInstance(rule).build();

    assertThat(
        inputKey1,
        Matchers.not(Matchers.equalTo(inputKey2)));
  }

}
