/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation.decider;

import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.routing.MutableShardRouting;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.settings.NodeSettingsService;

public class ConcurrentRebalanceAllocationDecider extends AllocationDecider {

    static {
        MetaData.addDynamicSettings(
                "cluster.routing.allocation.cluster_concurrent_rebalance"
        );
    }

    class ApplySettings implements NodeSettingsService.Listener {
        @Override public void onRefreshSettings(Settings settings) {
            int clusterConcurrentRebalance = settings.getAsInt("cluster.routing.allocation.cluster_concurrent_rebalance", ConcurrentRebalanceAllocationDecider.this.clusterConcurrentRebalance);
            if (clusterConcurrentRebalance != ConcurrentRebalanceAllocationDecider.this.clusterConcurrentRebalance) {
                logger.info("updating [cluster.routing.allocation.cluster_concurrent_rebalance] from [{}], to [{}]", ConcurrentRebalanceAllocationDecider.this.clusterConcurrentRebalance, clusterConcurrentRebalance);
                ConcurrentRebalanceAllocationDecider.this.clusterConcurrentRebalance = clusterConcurrentRebalance;
            }
        }
    }

    private volatile int clusterConcurrentRebalance;

    @Inject public ConcurrentRebalanceAllocationDecider(Settings settings, NodeSettingsService nodeSettingsService) {
        super(settings);
        this.clusterConcurrentRebalance = settings.getAsInt("cluster.routing.allocation.cluster_concurrent_rebalance", 2);
        logger.debug("using [cluster_concurrent_rebalance] with [{}]", clusterConcurrentRebalance);
        nodeSettingsService.addListener(new ApplySettings());
    }

    @Override public boolean canRebalance(ShardRouting shardRouting, RoutingAllocation allocation) {
        if (clusterConcurrentRebalance == -1) {
            return true;
        }
        int rebalance = 0;
        for (RoutingNode node : allocation.routingNodes()) {
            for (MutableShardRouting shard : node) {
                if (shard.state() == ShardRoutingState.RELOCATING) {
                    rebalance++;
                }
            }
        }
        if (rebalance >= clusterConcurrentRebalance) {
            return false;
        }
        return true;
    }
}