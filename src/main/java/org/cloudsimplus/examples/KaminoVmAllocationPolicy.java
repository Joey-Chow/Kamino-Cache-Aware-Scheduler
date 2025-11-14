/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Kamino-style VM Allocation Policy combining:
 * 1. Cache-aware scheduling (prioritizing hosts with cached data)
 * 2. Latency-driven placement (minimizing request completion time)
 * 3. Load balancing (distributing VMs to avoid hotspots)
 *
 * This version models cache hits/misses during execution to show real benefits.
 *
 * Based on: "Kamino: Efficient VM Allocation at Scale with Latency-Driven
 * Cache-Aware Scheduling" (OSDI 2025)
 *
 * @author Implementation for CloudSim Plus Research Project
 * @since CloudSim Plus 8.5.7
 */
public class KaminoVmAllocationPolicy extends VmAllocationPolicyAbstract {

    /** Cache state tracking: maps host to set of cached data items */
    private final Map<Host, Set<String>> hostCacheState;

    /** Request history: tracks which VMs accessed which data */
    private final Map<Vm, Set<String>> vmDataAccess;

    /** Latency prediction: estimated latency for each host */
    private final Map<Host, Double> hostLatency;

    /** Track cache hits and misses during execution */
    private int totalCacheAccesses = 0;
    private int totalCacheHits = 0;

    /** Cache hit weight in scoring function */
    private static final double CACHE_HIT_WEIGHT = 0.6;

    /** Latency weight in scoring function */
    private static final double LATENCY_WEIGHT = 0.3;

    /** Load balance weight in scoring function */
    private static final double LOAD_WEIGHT = 0.1;

    public KaminoVmAllocationPolicy() {
        super();
        this.hostCacheState = new HashMap<>();
        this.vmDataAccess = new HashMap<>();
        this.hostLatency = new HashMap<>();
    }

    @Override
    protected Optional<Host> defaultFindHostForVm(Vm vm) {
        final List<Host> hostList = getHostList();
        if (hostList.isEmpty()) {
            return Optional.empty();
        }

        // Initialize cache state for new hosts
        for (Host host : hostList) {
            hostCacheState.putIfAbsent(host, new HashSet<>());
            hostLatency.putIfAbsent(host, 0.0);
        }

        // Initialize data access pattern for VM (simulated based on VM ID)
        vmDataAccess.putIfAbsent(vm, generateDataAccessPattern(vm));

        // Find host with best Kamino score
        Host bestHost = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Host host : hostList) {
            if (!host.isSuitableForVm(vm)) {
                continue;
            }

            double score = calculateKaminoScore(host, vm);
            if (score > bestScore) {
                bestScore = score;
                bestHost = host;
            }
        }

        if (bestHost != null) {
            // Update cache state when VM is allocated
            updateCacheState(bestHost, vm);
            // Update latency prediction
            updateLatency(bestHost, vm);
        }

        return Optional.ofNullable(bestHost);
    }

    /**
     * Calculate Kamino score combining cache affinity, latency, and load balance
     */
    private double calculateKaminoScore(Host host, Vm vm) {
        double cacheScore = calculateCacheScore(host, vm);
        double latencyScore = calculateLatencyScore(host);
        double loadScore = calculateLoadScore(host);

        return (CACHE_HIT_WEIGHT * cacheScore) +
               (LATENCY_WEIGHT * latencyScore) +
               (LOAD_WEIGHT * loadScore);
    }

    /**
     * Calculate cache affinity score (higher = more cached data)
     */
    private double calculateCacheScore(Host host, Vm vm) {
        Set<String> cachedData = hostCacheState.get(host);
        Set<String> vmNeededData = vmDataAccess.get(vm);

        if (vmNeededData == null || vmNeededData.isEmpty()) {
            // If no data pattern yet, check if host has any warm cache
            return cachedData.isEmpty() ? 0.3 : 0.7;
        }

        // Calculate potential cache hit rate
        long hits = vmNeededData.stream()
            .filter(cachedData::contains)
            .count();

        double hitRate = (double) hits / vmNeededData.size();

        // Boost score if this host has warm cache for this data
        return hitRate;
    }

    /**
     * Calculate latency score (lower latency = higher score)
     */
    private double calculateLatencyScore(Host host) {
        double latency = hostLatency.get(host);

        // Normalize: lower latency gives higher score
        // Using exponential decay to heavily penalize high latency
        return Math.exp(-latency / 10.0);
    }

    /**
     * Calculate load balance score (less loaded = higher score)
     */
    private double calculateLoadScore(Host host) {
        double cpuUtilization = host.getCpuPercentUtilization();
        double ramUtilization = host.getRam().getPercentUtilization();
        double bwUtilization = host.getBw().getPercentUtilization();

        // Average utilization
        double avgUtilization = (cpuUtilization + ramUtilization + bwUtilization) / 3.0;

        // Prefer hosts with moderate utilization (avoid empty and full hosts)
        // Optimal range: 40-60% utilization
        double targetUtilization = 0.5;
        double deviation = Math.abs(avgUtilization - targetUtilization);

        return 1.0 - deviation;
    }

    /**
     * Update cache state when VM is allocated to host
     */
    private void updateCacheState(Host host, Vm vm) {
        Set<String> cachedData = hostCacheState.get(host);
        Set<String> vmNeededData = vmDataAccess.get(vm);

        // Add VM's data to host cache (simulating cache warming)
        cachedData.addAll(vmNeededData);

        // Simulate cache eviction if too large (keep most recent 100 items)
        if (cachedData.size() > 100) {
            List<String> items = new ArrayList<>(cachedData);
            cachedData.clear();
            cachedData.addAll(items.subList(items.size() - 100, items.size()));
        }
    }

    /**
     * Update latency prediction based on host load
     */
    private void updateLatency(Host host, Vm vm) {
        // Simulate latency increase based on host utilization
        double cpuUtil = host.getCpuPercentUtilization();
        double baseLatency = 5.0; // base latency in ms
        double loadLatency = cpuUtil * 20.0; // additional latency from load

        hostLatency.put(host, baseLatency + loadLatency);
    }

    /**
     * Generate simulated data access pattern for VM based on its ID
     * VMs with similar IDs share some data (simulating workload locality)
     * This should match the pattern used in DataIntensiveCloudlet and prewarmCaches
     */
    private Set<String> generateDataAccessPattern(Vm vm) {
        Set<String> dataItems = new HashSet<>();
        long vmId = vm.getId();

        // Create data locality: Each VM will run cloudlets from certain groups
        // VM 0,1 → Cloudlets 0-3 (group 0)
        // VM 2,3 → Cloudlets 4-7 (group 1)
        // VM 4,5 → Cloudlets 8-11 (group 2)
        // Pattern: VM (2n, 2n+1) runs cloudlets from group n
        long group = vmId / 2; // VMs in pairs handle same group

        // Shared data within group (matches cloudlet pattern)
        for (int i = 0; i < 5; i++) {
            dataItems.add("dataset_group_" + group + "_item_" + i);
        }

        // VM-specific data (cloudlets on this VM will have specific data)
        for (int i = 0; i < 2; i++) {
            dataItems.add("dataset_cloudlet_" + (vmId * 2) + "_item_" + i);
            dataItems.add("dataset_cloudlet_" + (vmId * 2 + 1) + "_item_" + i);
        }

        // Global shared data (all cloudlets share this)
        dataItems.add("dataset_global_common");

        return dataItems;
    }

    /**
     * Pre-warm host cache with data (before VM allocation)
     */
    public void prewarmHostCache(Host host, String dataItem) {
        Set<String> cachedData = hostCacheState.get(host);
        if (cachedData != null) {
            cachedData.add(dataItem);
        }
    }

    /**
     * Simulate data access during cloudlet execution
     * This models cache hits/misses with actual latency impact
     * Note: Can be called after simulation ends to model what happened during execution
     * @return true if cache hit, false if cache miss
     */
    public boolean simulateDataAccess(Vm vm, String dataItem) {
        if (vm == null || vm.getHost() == null) {
            return false;
        }

        totalCacheAccesses++;
        Host host = vm.getHost();
        Set<String> cachedData = hostCacheState.get(host);

        if (cachedData == null) {
            // Initialize if needed
            cachedData = new HashSet<>();
            hostCacheState.put(host, cachedData);
        }

        boolean isHit = cachedData.contains(dataItem);

        if (isHit) {
            // Cache hit - fast access
            totalCacheHits++;
        } else {
            // Cache miss - add to cache (simulate fetch)
            cachedData.add(dataItem);

            // Evict if cache is full (LRU-style)
            if (cachedData.size() > 100) {
                Iterator<String> iter = cachedData.iterator();
                if (iter.hasNext()) {
                    iter.next();
                    iter.remove();
                }
            }
        }

        return isHit;
    }

    /**
     * Get cache hit rate for reporting (based on actual execution)
     */
    public double getCacheHitRate() {
        return totalCacheAccesses > 0 ? (double) totalCacheHits / totalCacheAccesses : 0.0;
    }

    /**
     * Get average predicted latency across all hosts
     */
    public double getAverageLatency() {
        return hostLatency.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
}

