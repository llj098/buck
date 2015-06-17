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

package com.facebook.buck.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.android.AndroidResource.BuildOutput;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeOnDiskBuildInfo;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.Sha1HashCode;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestSourcePath;
import com.facebook.buck.rules.keys.DefaultRuleKeyBuilderFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Hashing;

import org.easymock.EasyMock;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


public class AndroidResourceTest {

  @Test
  public void testRuleKeyForDifferentInputFilenames() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    SourcePathResolver pathResolver = new SourcePathResolver(new BuildRuleResolver());
    String commonHash = Strings.repeat("a", 40);
    FakeFileHashCache fakeFileHashCache = FakeFileHashCache.createFromStrings(
        ImmutableMap.of(
            "java/src/com/facebook/base/res/drawable/A.xml", commonHash,
            "java/src/com/facebook/base/assets/drawable/B.xml", Strings.repeat("b", 40),
            "java/src/com/facebook/base/res/drawable/C.xml", commonHash,
            "java/src/com/facebook/base/AndroidManifest.xml", Strings.repeat("d", 40)));

    BuildTarget buildTarget =
        BuildTarget.builder("//java/src/com/facebook/base", "res").build();
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(buildTarget)
        .setFileHashCache(fakeFileHashCache)
        .build();

    AndroidResource androidResource1 = AndroidResourceRuleBuilder.newBuilder()
        .setResolver(pathResolver)
        .setBuildRuleParams(params)
        .setRes(new TestSourcePath("java/src/com/facebook/base/res"))
        .setResSrcs(
            ImmutableSortedSet.of(
                Paths.get("java/src/com/facebook/base/res/drawable/A.xml")))
        .setRDotJavaPackage("com.facebook")
        .setAssets(new TestSourcePath("java/src/com/facebook/base/assets"))
        .setAssetsSrcs(
            ImmutableSortedSet.of(
                Paths.get("java/src/com/facebook/base/assets/drawable/B.xml")))
        .setManifest(
            new PathSourcePath(
                projectFilesystem,
                Paths.get("java/src/com/facebook/base/AndroidManifest.xml")))
        .build();

    AndroidResource androidResource2 = AndroidResourceRuleBuilder.newBuilder()
        .setResolver(pathResolver)
        .setBuildRuleParams(params)
        .setRes(new TestSourcePath("java/src/com/facebook/base/res"))
        .setResSrcs(
            ImmutableSortedSet.of(
                Paths.get("java/src/com/facebook/base/res/drawable/C.xml")))
        .setRDotJavaPackage("com.facebook")
        .setAssets(new TestSourcePath("java/src/com/facebook/base/assets"))
        .setAssetsSrcs(
            ImmutableSortedSet.of(
                Paths.get("java/src/com/facebook/base/assets/drawable/B.xml")))
        .setManifest(
            new PathSourcePath(
                projectFilesystem,
                Paths.get("java/src/com/facebook/base/AndroidManifest.xml")))
        .build();

    DefaultRuleKeyBuilderFactory factory =
        new DefaultRuleKeyBuilderFactory(FakeFileHashCache.createFromStrings(ImmutableMap.of(
                "java/src/com/facebook/base/AndroidManifest.xml", "bbbbbbbbbb",
                "java/src/com/facebook/base/assets/drawable/A.xml", "cccccccccccc",
                "java/src/com/facebook/base/assets/drawable/B.xml", "aaaaaaaaaaaa",
                "java/src/com/facebook/base/res/drawable/A.xml", "dddddddddd",
                "java/src/com/facebook/base/res/drawable/C.xml", "eeeeeeeeee"
                )),
            pathResolver);
    RuleKey ruleKey1 =
        factory.newInstance(androidResource1).build();
    RuleKey ruleKey2 =
        factory.newInstance(androidResource2).build();

    assertNotEquals(
        "The two android_resource rules should have different rule keys.",
        ruleKey1,
        ruleKey2);
  }

  @Test
  public void testAbiKeyExcludesEmptyResources() throws IOException {
    BuildRuleResolver ruleResolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(ruleResolver);

    BuildRule resourceRule1 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setResolver(pathResolver)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res1"))
            .setRDotJavaPackage("com.facebook")
            .setRes(new TestSourcePath("android_res/com/example/res1"))
            .build());
    setAndroidResourceBuildOutput(resourceRule1, "a");
    BuildRule resourceRule2 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setResolver(pathResolver)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res2"))
            .setRDotJavaPackage("com.facebook")
            .build());
    setAndroidResourceBuildOutput(resourceRule2, "b");
    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/example:res3");
    ImmutableSortedSet<BuildRule> deps = ImmutableSortedSet.of(resourceRule1, resourceRule2);
    BuildRule resourceRule3 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setResolver(pathResolver)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res3"))
            .setDeps(deps)
            .setBuildRuleParams(
                new FakeBuildRuleParamsBuilder(target)
                    .setDeps(deps)
                    .build())
            .build());

    FakeBuildableContext buildableContext = new FakeBuildableContext();
    assertTrue(
        resourceRule3
            .getBuildSteps(
                EasyMock.createMock(BuildContext.class),
                buildableContext)
            .isEmpty());

    Sha1HashCode expectedSha1 = HasAndroidResourceDeps.ABI_HASHER.apply(
        ImmutableList.of((HasAndroidResourceDeps) resourceRule1));
    buildableContext.assertContainsMetadataMapping(
        AndroidResource.METADATA_KEY_FOR_ABI,
        expectedSha1.getHash());
  }

  @Test
  public void testAdditionalAbiKey() throws IOException {
    BuildRuleResolver ruleResolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(ruleResolver);

    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/example:res2");
    String additionalAbiKey = Strings.repeat("d", 40);
    BuildRule resourceRule2 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setResolver(pathResolver)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res2"))
            .setAdditionalAbiKey(
                Optional.of(Suppliers.ofInstance(Sha1HashCode.of(additionalAbiKey))))
            .setBuildRuleParams(
                new FakeBuildRuleParamsBuilder(target).build())
            .build());

    FakeBuildableContext buildableContext = new FakeBuildableContext();
    assertTrue(
        resourceRule2
            .getBuildSteps(
                EasyMock.createMock(BuildContext.class),
                buildableContext)
            .isEmpty());

    Sha1HashCode expectedSha1 = Sha1HashCode.fromHashCode(
        Hashing.sha1().newHasher()
            .putUnencodedChars(Hashing.sha1().newHasher().hash().toString())
            .putUnencodedChars(additionalAbiKey)
            .hash());

    buildableContext.assertContainsMetadataMapping(
        AndroidResource.METADATA_KEY_FOR_ABI,
        expectedSha1.getHash());
  }

  @Test
  public void testGetRDotJavaPackageWhenPackageIsSpecified() {
    AndroidResource androidResource = new AndroidResource(
        new FakeBuildRuleParamsBuilder("//foo:bar").build(),
        new SourcePathResolver(new BuildRuleResolver()),
        /* deps */ ImmutableSortedSet.<BuildRule>of(),
        new TestSourcePath("foo/res"),
        ImmutableSortedSet.of(Paths.get("foo/res/values/strings.xml")),
        /* rDotJavaPackage */ "com.example.android",
        /* assets */ null,
        /* assetsSrcs */ ImmutableSortedSet.<Path>of(),
        /* manifestFile */ null,
        /* hasWhitelistedStrings */ false);
    assertEquals("com.example.android", androidResource.getRDotJavaPackage());
  }

  @Test
  public void testGetRDotJavaPackageWhenPackageIsNotSpecified() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    AndroidResource androidResource = new AndroidResource(
        new FakeBuildRuleParamsBuilder("//foo:bar").build(),
        new SourcePathResolver(new BuildRuleResolver()),
        /* deps */ ImmutableSortedSet.<BuildRule>of(),
        new TestSourcePath("foo/res"),
        ImmutableSortedSet.of(Paths.get("foo/res/values/strings.xml")),
        /* rDotJavaPackage */ null,
        /* assets */ null,
        /* assetsSrcs */ ImmutableSortedSet.<Path>of(),
        /* manifestFile */ new PathSourcePath(
            projectFilesystem,
            Paths.get("foo/AndroidManifest.xml")),
        /* hasWhitelistedStrings */ false);
    FakeOnDiskBuildInfo onDiskBuildInfo = new FakeOnDiskBuildInfo();
    onDiskBuildInfo.putMetadata(AndroidResource.METADATA_KEY_FOR_ABI, Strings.repeat("a", 40));
    onDiskBuildInfo.putMetadata(AndroidResource.METADATA_KEY_FOR_R_DOT_JAVA_PACKAGE, "com.ex.pkg");
    androidResource.initializeFromDisk(onDiskBuildInfo);
    assertEquals("com.ex.pkg", androidResource.getRDotJavaPackage());
  }

  private void setAndroidResourceBuildOutput(BuildRule resourceRule, String hashChar) {
    if (resourceRule instanceof AndroidResource) {
      ((AndroidResource) resourceRule)
          .getBuildOutputInitializer()
          .setBuildOutput(new BuildOutput(Sha1HashCode.of(Strings.repeat(hashChar, 40))));
    }
  }
}
