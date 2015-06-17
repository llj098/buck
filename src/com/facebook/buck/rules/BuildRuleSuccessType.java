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

import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableSet;

import java.util.EnumSet;

/**
 * Token provided by the result of {@link BuildEngine#build(BuildContext, BuildRule)},
 * demonstrating that the associated {@link BuildRule} was built successfully.
 */
public enum BuildRuleSuccessType {

  /** Built by executing the {@link Step}s for the rule. */
  BUILT_LOCALLY(
      Property.SHOULD_UPLOAD_RESULTING_ARTIFACT,
      Property.SHOULD_UPLOAD_RESULTING_ARTIFACT_INPUT_BASED,
      Property.SHOULD_CLEAR_AND_WRITE_METADATA_ON_DISK
  ),

  /** Fetched via the {@link ArtifactCache}. */
  FETCHED_FROM_CACHE(
  ),

  /** Computed {@link RuleKey} matches the one on disk. */
  MATCHING_RULE_KEY(
  ),

  /** Fetched via the {@link ArtifactCache} using an input-based rule key. */
  FETCHED_FROM_CACHE_INPUT_BASED(
      Property.SHOULD_UPLOAD_RESULTING_ARTIFACT,
      Property.SHOULD_UPDATE_METADATA_ON_DISK
  ),

  /** Computed input-based {@link RuleKey} matches the one on disk. */
  MATCHING_INPUT_BASED_RULE_KEY(
      Property.SHOULD_UPLOAD_RESULTING_ARTIFACT,
      Property.SHOULD_UPDATE_METADATA_ON_DISK
  ),

  /**
   * Computed {@link RuleKey} without deps matches the one on disk <em>AND</em> the ABI key for
   * the deps matches the one on disk.
   */
  MATCHING_DEPS_ABI_AND_RULE_KEY_NO_DEPS(
      Property.SHOULD_UPDATE_METADATA_ON_DISK
  ),
  ;

  private final EnumSet<Property> properties;

  BuildRuleSuccessType() {
    this.properties = EnumSet.noneOf(Property.class);
  }

  BuildRuleSuccessType(Property... properties) {
    this.properties = EnumSet.copyOf(ImmutableSet.copyOf(properties));
  }

  public boolean shouldWriteRecordedMetadataToDiskAfterBuilding() {
    return properties.contains(Property.SHOULD_UPDATE_METADATA_ON_DISK) ||
        properties.contains(Property.SHOULD_CLEAR_AND_WRITE_METADATA_ON_DISK);

  }

  public boolean shouldClearAndOverwriteMetadataOnDisk() {
    return properties.contains(Property.SHOULD_CLEAR_AND_WRITE_METADATA_ON_DISK);
  }

  public boolean shouldUploadResultingArtifact() {
    return properties.contains(Property.SHOULD_UPLOAD_RESULTING_ARTIFACT);
  }

  public boolean shouldUploadResultingArtifactInputBased() {
    return properties.contains(Property.SHOULD_UPLOAD_RESULTING_ARTIFACT_INPUT_BASED);
  }

  private enum Property {
    SHOULD_UPLOAD_RESULTING_ARTIFACT,
    SHOULD_UPLOAD_RESULTING_ARTIFACT_INPUT_BASED,
    SHOULD_CLEAR_AND_WRITE_METADATA_ON_DISK,
    SHOULD_UPDATE_METADATA_ON_DISK,
  }
}
