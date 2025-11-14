/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 */
package org.cloudsimplus.examples;

import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModel;

import java.util.*;

/**
 * Data-intensive cloudlet that models data access patterns
 * and benefits from cache locality.
 *
 * @author Kamino Research Project
 * @since CloudSim Plus 8.5.7
 */
public class DataIntensiveCloudlet extends CloudletSimple {

    /** Data items this cloudlet needs to access */
    private final Set<String> dataItems;

    /** Number of data accesses per instruction */
    private static final double DATA_ACCESS_RATIO = 0.1;  // 10% of instructions involve data access

    /** Latency penalty for cache miss (in seconds of additional execution time) */
    private static final double CACHE_MISS_PENALTY = 0.050;  // 50ms per miss (realistic remote fetch)

    /** Cache hit latency */
    private static final double CACHE_HIT_LATENCY = 0.001;  // 1ms per hit (local cache access)

    /**
     * Creates a data-intensive cloudlet
     */
    public DataIntensiveCloudlet(long length, int pesNumber, UtilizationModel utilizationModel) {
        super(length, pesNumber, utilizationModel);
        this.dataItems = new HashSet<>();
    }

    /**
     * Add data item that this cloudlet will access
     */
    public void addDataItem(String dataItem) {
        dataItems.add(dataItem);
    }

    /**
     * Set all data items at once
     */
    public void setDataItems(Set<String> items) {
        dataItems.clear();
        dataItems.addAll(items);
    }

    /**
     * Get data items accessed by this cloudlet
     */
    public Set<String> getDataItems() {
        return new HashSet<>(dataItems);
    }

    /** Tracked cache hits for this cloudlet */
    private int cacheHits = 0;

    /** Tracked cache misses for this cloudlet */
    private int cacheMisses = 0;

    /**
     * Record a cache access result
     */
    public void recordCacheAccess(boolean hit) {
        if (hit) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
    }

    /**
     * Calculate actual I/O overhead based on cache behavior
     * This returns the time penalty in seconds
     */
    public double calculateIOOverhead() {
        return (cacheHits * CACHE_HIT_LATENCY) + (cacheMisses * CACHE_MISS_PENALTY);
    }

    /**
     * Get cache hit count
     */
    public int getCacheHits() {
        return cacheHits;
    }

    /**
     * Get cache miss count
     */
    public int getCacheMisses() {
        return cacheMisses;
    }

    /**
     * Calculate expected cache miss overhead
     * This simulates the latency impact of cache misses
     */
    public double calculateCacheMissOverhead(KaminoVmAllocationPolicy policy) {
        if (getVm() == null || !getVm().isCreated()) {
            return 0.0;
        }

        double overhead = 0.0;

        // Simulate data accesses during execution
        int numAccesses = (int) (getLength() * DATA_ACCESS_RATIO);

        for (int i = 0; i < numAccesses; i++) {
            // Pick a random data item (simulate access pattern)
            if (!dataItems.isEmpty()) {
                String item = dataItems.iterator().next();

                // Check if in cache (simulate access)
                boolean wasHit = checkCache(policy, item);

                if (wasHit) {
                    overhead += CACHE_HIT_LATENCY;
                } else {
                    overhead += CACHE_MISS_PENALTY;
                }
            }
        }

        return overhead;
    }

    /**
     * Check cache and update policy statistics
     */
    private boolean checkCache(KaminoVmAllocationPolicy policy, String dataItem) {
        // Simulate cache access
        policy.simulateDataAccess(getVm(), dataItem);

        // Return whether it was a hit (policy tracks this)
        return Math.random() < policy.getCacheHitRate();
    }
}

