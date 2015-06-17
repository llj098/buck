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

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonStreamParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility for reading the metadata associated with a build rule's output. This is metadata that
 * would have been written by a {@link BuildInfoRecorder} when the rule was built initially.
 * <p>
 * Such metadata is stored as key/value pairs.
 */
public class DefaultOnDiskBuildInfo implements OnDiskBuildInfo {

  private static final Function<String, ImmutableList<String>> TO_STRINGS =
      new Function<String, ImmutableList<String>>() {
    @Override
    public ImmutableList<String> apply(String input) {
      JsonElement element = new JsonStreamParser(input).next();
      Preconditions.checkState(element.isJsonArray(),
          "Value for %s should have been a JSON array but was %s.",
          input,
          element);

      JsonArray array = element.getAsJsonArray();
      ImmutableList.Builder<String> out = ImmutableList.builder();
      for (JsonElement item : array) {
        out.add(item.getAsString());
      }
      return out.build();
    }
  };

  private final Logger LOG;
  private final ProjectFilesystem projectFilesystem;
  private final Path metadataDirectory;

  public DefaultOnDiskBuildInfo(BuildTarget target, ProjectFilesystem projectFilesystem) {
    LOG = Logger.get(DefaultOnDiskBuildInfo.class);
    this.projectFilesystem = projectFilesystem;
    this.metadataDirectory = BuildInfo.getPathToMetadataDirectory(target);
  }

  @Override
  public Optional<String> getValue(String key) {
    return projectFilesystem.readFileIfItExists(metadataDirectory.resolve(key));
  }

  @Override
  public Optional<ImmutableList<String>> getValues(String key) {
    try {
      return getValue(key).transform(TO_STRINGS);
    } catch (JsonParseException | IllegalStateException ignored) {
      return Optional.absent();
    }
  }

  @Override
  public Optional<Sha1HashCode> getHash(String key) {
    Optional<String> optionalValue = getValue(key);
    if (optionalValue.isPresent()) {
      String value = optionalValue.get();
      try {
        return Optional.of(Sha1HashCode.of(value));
      } catch (IllegalArgumentException e) {
        LOG.error(
            e,
            "DefaultOnDiskBuildInfo.getHash(%s): Cannot transform %s to SHA1",
            key,
            value);
        return Optional.absent();
      }
    } else {
      LOG.warn("DefaultOnDiskBuildInfo.getHash(%s): Hash not found", key);
      return Optional.absent();
    }
  }

  @Override
  public Optional<RuleKey> getRuleKey(String key) {
    try {
      return getValue(key).transform(RuleKey.TO_RULE_KEY);
    } catch (IllegalArgumentException ignored) {
      return Optional.absent();
    }
  }

  @Override
  public List<String> getOutputFileContentsByLine(Path pathRelativeToProjectRoot)
      throws IOException {
    return projectFilesystem.readLines(pathRelativeToProjectRoot);
  }

  @Override
  public void deleteExistingMetadata() throws IOException {
    projectFilesystem.deleteRecursivelyIfExists(metadataDirectory);
  }

}
