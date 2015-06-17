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

package com.facebook.buck.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.keys.DefaultRuleKeyBuilderFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.util.FileHashCache;
import com.facebook.buck.util.NullFileHashCache;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.common.io.Files;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class DirArtifactCacheTest {
  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  private FileHashCache fileHashCache = new NullFileHashCache();

  private DirArtifactCache dirArtifactCache;

  @After
  public void tearDown() {
    if (dirArtifactCache != null) {
      dirArtifactCache.close();
    }
  }

  @Test
  public void testCacheCreation() throws IOException {
    File cacheDir = tmpDir.newFolder();

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));
  }

  @Test
  public void testCacheFetchMiss() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX.toPath(), HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write("x", fileX, Charsets.UTF_8);
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver(ImmutableSet.of(
        inputRuleX)));
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(fileHashCache, resolver)
        .newInstance(inputRuleX)
        .build();

    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
  }

  @Test
  public void testCacheStoreAndFetchHit() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX.toPath(), HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.<Long>absent());

    Files.write("x", fileX, Charsets.UTF_8);
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver(ImmutableSet.of(
        inputRuleX)));
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(fileHashCache, resolver)
        .newInstance(inputRuleX)
        .build();

    dirArtifactCache.store(ImmutableSet.of(ruleKeyX), fileX);

    // Test that artifact overwrite works.
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));

    // Test that artifact creation works.
    assertTrue(fileX.delete());
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
  }

  @Test
  public void testCacheStoreOverwrite() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX.toPath(), HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write("x", fileX, Charsets.UTF_8);
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver(ImmutableSet.of(
        inputRuleX)));
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(fileHashCache, resolver)
        .newInstance(inputRuleX)
        .build();

    dirArtifactCache.store(ImmutableSet.of(ruleKeyX), fileX);
    dirArtifactCache.store(ImmutableSet.of(ruleKeyX), fileX); // Overwrite.

    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
  }

  @Test
  public void testCacheStoresAndFetchHits() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");
    File fileY = tmpDir.newFile("y");
    File fileZ = tmpDir.newFile("z");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX.toPath(), HashCode.fromInt(0),
                fileY.toPath(), HashCode.fromInt(1),
                fileZ.toPath(), HashCode.fromInt(2)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write("x", fileX, Charsets.UTF_8);
    Files.write("y", fileY, Charsets.UTF_8);
    Files.write("z", fileZ, Charsets.UTF_8);

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    BuildRule inputRuleZ = new BuildRuleForTest(fileZ);
    assertFalse(inputRuleX.equals(inputRuleY));
    assertFalse(inputRuleX.equals(inputRuleZ));
    assertFalse(inputRuleY.equals(inputRuleZ));
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver(ImmutableSet.of(
        inputRuleX,
        inputRuleY,
        inputRuleZ)));

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleX)
        .build();
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleY)
        .build();
    RuleKey ruleKeyZ = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleZ)
        .build();

    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyY, fileY).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyZ, fileZ).getType());

    dirArtifactCache.store(ImmutableSet.of(ruleKeyX), fileX);
    dirArtifactCache.store(ImmutableSet.of(ruleKeyY), fileY);
    dirArtifactCache.store(ImmutableSet.of(ruleKeyZ), fileZ);

    assertTrue(fileX.delete());
    assertTrue(fileY.delete());
    assertTrue(fileZ.delete());

    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyY, fileY).getType());
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKeyZ, fileZ).getType());

    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
    assertEquals(inputRuleY, new BuildRuleForTest(fileY));
    assertEquals(inputRuleZ, new BuildRuleForTest(fileZ));

    assertEquals(3, cacheDir.listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(0, cacheDir.listFiles().length);
  }

  @Test
  public void testNoStoreMisses() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");
    File fileY = tmpDir.newFile("y");
    File fileZ = tmpDir.newFile("z");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX.toPath(), HashCode.fromInt(0),
                fileY.toPath(), HashCode.fromInt(1),
                fileZ.toPath(), HashCode.fromInt(2)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ false,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write("x", fileX, Charsets.UTF_8);
    Files.write("y", fileY, Charsets.UTF_8);
    Files.write("z", fileZ, Charsets.UTF_8);

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    BuildRule inputRuleZ = new BuildRuleForTest(fileZ);
    assertFalse(inputRuleX.equals(inputRuleY));
    assertFalse(inputRuleX.equals(inputRuleZ));
    assertFalse(inputRuleY.equals(inputRuleZ));
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver(ImmutableSet.of(
        inputRuleX,
        inputRuleY,
        inputRuleZ)));

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleX)
        .build();
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleY)
        .build();
    RuleKey ruleKeyZ = fakeRuleKeyBuilderFactory
        .newInstance(inputRuleZ)
        .build();

    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyY, fileY).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyZ, fileZ).getType());

    dirArtifactCache.store(ImmutableSet.of(ruleKeyX), fileX);
    dirArtifactCache.store(ImmutableSet.of(ruleKeyY), fileY);
    dirArtifactCache.store(ImmutableSet.of(ruleKeyZ), fileZ);

    assertTrue(fileX.delete());
    assertTrue(fileY.delete());
    assertTrue(fileZ.delete());

    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyX, fileX).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyY, fileY).getType());
    assertEquals(CacheResult.Type.MISS, dirArtifactCache.fetch(ruleKeyZ, fileZ).getType());

    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
    assertEquals(inputRuleY, new BuildRuleForTest(fileY));
    assertEquals(inputRuleZ, new BuildRuleForTest(fileZ));

    assertEquals(0, cacheDir.listFiles().length);
  }

  @Test
  public void testDeleteNothing() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = new File(cacheDir, "x");
    File fileY = new File(cacheDir, "y");
    File fileZ = new File(cacheDir, "z");

    dirArtifactCache = new DirArtifactCache(
        "dir",
        tmpDir.getRoot(),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(1024L));

    Files.write("x", fileX, Charsets.UTF_8);
    Files.write("y", fileY, Charsets.UTF_8);
    Files.write("z", fileZ, Charsets.UTF_8);

    assertEquals(3, cacheDir.listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(3, cacheDir.listFiles().length);
  }

  @Test
  public void testDeleteNothingAbsentLimit() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = new File(cacheDir, "x");
    File fileY = new File(cacheDir, "y");
    File fileZ = new File(cacheDir, "z");

    dirArtifactCache = new DirArtifactCache(
        "dir",
        tmpDir.getRoot(),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.<Long>absent());

    Files.write("x", fileX, Charsets.UTF_8);
    Files.write("y", fileY, Charsets.UTF_8);
    Files.write("z", fileZ, Charsets.UTF_8);

    assertEquals(3, cacheDir.listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(3, cacheDir.listFiles().length);
  }

  @Test
  public void testDeleteSome() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileW = new File(cacheDir, "w");
    File fileX = new File(cacheDir, "x");
    File fileY = new File(cacheDir, "y");
    File fileZ = new File(cacheDir, "z");

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(2L));

    Files.write("w", fileW, Charsets.UTF_8);
    Files.write("x", fileX, Charsets.UTF_8);
    Files.write("y", fileY, Charsets.UTF_8);
    Files.write("z", fileZ, Charsets.UTF_8);

    java.nio.file.Files.setAttribute(fileW.toPath(), "lastAccessTime", FileTime.fromMillis(9000));
    java.nio.file.Files.setAttribute(fileX.toPath(), "lastAccessTime", FileTime.fromMillis(0));
    java.nio.file.Files.setAttribute(fileY.toPath(), "lastAccessTime", FileTime.fromMillis(1000));
    java.nio.file.Files.setAttribute(fileZ.toPath(), "lastAccessTime", FileTime.fromMillis(2000));

    assertEquals(4, cacheDir.listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(ImmutableSet.of(fileZ, fileW), ImmutableSet.copyOf(cacheDir.listFiles()));
  }

  @Test
  public void testCacheStoreMultipleKeys() throws IOException {
    File cacheDir = tmpDir.newFolder();
    File fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX.toPath(), HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.<Long>absent());

    Files.write("x", fileX, Charsets.UTF_8);
    RuleKey ruleKey1 = new RuleKey("aaaa");
    RuleKey ruleKey2 = new RuleKey("bbbb");

    dirArtifactCache.store(ImmutableSet.of(ruleKey1, ruleKey2), fileX);

    // Test that artifact is available via both keys.
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKey1, fileX).getType());
    assertEquals(CacheResult.Type.HIT, dirArtifactCache.fetch(ruleKey2, fileX).getType());
  }

  private static class BuildRuleForTest extends FakeBuildRule {

    @SuppressWarnings("PMD.UnusedPrivateField")
    @AddToRuleKey
    private final Path file;

    private BuildRuleForTest(File file) {
      super(
          BuildTarget.builder("//foo", file.getName()).build(),
          new SourcePathResolver(new BuildRuleResolver()));
      this.file = file.toPath();
    }
  }
}
