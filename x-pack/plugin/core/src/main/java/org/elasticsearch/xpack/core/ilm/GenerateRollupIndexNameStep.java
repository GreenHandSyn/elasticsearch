/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ilm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.index.Index;

import java.util.Locale;
import java.util.Objects;

import static org.elasticsearch.xpack.core.ilm.LifecycleExecutionState.ILM_CUSTOM_METADATA_KEY;
import static org.elasticsearch.xpack.core.ilm.LifecycleExecutionState.fromIndexMetadata;

/**
 * Generates a new rollup index name for the current managed index.
 * <p>
 * The generated rollup index name will be in the format {rollup-indexName-randomUUID}
 * eg.: rollup-myindex-cmuce-qfvmn_dstqw-ivmjc1etsa
 */
public class GenerateRollupIndexNameStep extends ClusterStateActionStep {

    public static final String NAME = "generate-rollup-name";

    private static final Logger logger = LogManager.getLogger(GenerateRollupIndexNameStep.class);

    public GenerateRollupIndexNameStep(StepKey key, StepKey nextStepKey) {
        super(key, nextStepKey);
    }

    @Override
    public ClusterState performAction(Index index, ClusterState clusterState) {
        IndexMetadata indexMetaData = clusterState.metadata().index(index);
        if (indexMetaData == null) {
            // Index must have been since deleted, ignore it
            logger.debug("[{}] lifecycle action for index [{}] executed but index no longer exists", getKey().getAction(), index.getName());
            return clusterState;
        }
        ClusterState.Builder newClusterStateBuilder = ClusterState.builder(clusterState);
        LifecycleExecutionState lifecycleState = fromIndexMetadata(indexMetaData);
        assert lifecycleState.getRollupIndexName() == null : "index " + index.getName() +
            " should have a rollup index name generated by the ilm policy but has " + lifecycleState.getRollupIndexName();
        LifecycleExecutionState.Builder newCustomData = LifecycleExecutionState.builder(lifecycleState);
        String rollupIndexName = generateRollupIndexName(index.getName());
        newCustomData.setRollupIndexName(rollupIndexName);
        IndexMetadata.Builder indexMetadataBuilder = IndexMetadata.builder(indexMetaData);
        indexMetadataBuilder.putCustom(ILM_CUSTOM_METADATA_KEY, newCustomData.build().asMap());
        newClusterStateBuilder.metadata(Metadata.builder(clusterState.getMetadata()).put(indexMetadataBuilder));
        return newClusterStateBuilder.build();
    }

    // TODO(talevy): find alternative to lowercasing UUID? kind of defeats the expectation of the UUID here. index names must lowercase
    public static String generateRollupIndexName(String indexName) {
        return "rollup-" + indexName + "-" +  UUIDs.randomBase64UUID().toLowerCase(Locale.ROOT);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return super.equals(obj);
    }
}