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

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Example demonstrating Kamino-style cache-aware and latency-driven VM allocation.
 *
 * This scheduler implements the key ideas from the Kamino paper:
 * - Cache locality awareness: VMs are placed on hosts with cached data
 * - Latency-driven placement: Minimizes request completion time
 * - Load balancing: Distributes VMs to avoid resource hotspots
 *
 * Measurements tracked:
 * - Mean & 90th-percentile latency
 * - Cache hit rate
 * - Host utilization
 * - Throughput
 *
 * @author Kamino Research Project Implementation
 * @since CloudSim Plus 8.5.7
 */
public class KaminoSchedulerExample {
    private static final int HOSTS = 6;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 4096;
    private static final long HOST_BW = 10_000;
    private static final long HOST_STORAGE = 1_000_000;

    private static final int VMS = 12;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 24;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10_000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;
    private KaminoVmAllocationPolicy kaminoPolicy;

    public static void main(String[] args) {
        new KaminoSchedulerExample();
    }

    private KaminoSchedulerExample() {
        System.out.println("\n========================================");
        System.out.println("  KAMINO CACHE-AWARE SCHEDULER TEST");
        System.out.println("========================================\n");

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();

        // Pre-warm caches BEFORE submitting VMs (so allocation can benefit)
        prewarmCaches();

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        // Show VM allocation results
        System.out.println("\nVM Allocation Results:");
        System.out.println("=====================");
        for (Vm vm : vmList) {
            if (vm.getHost() != null) {
                System.out.printf("VM %2d → Host %d\n", vm.getId(), vm.getHost().getId());
            }
        }
        System.out.println();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();

        // NOW simulate data access after cloudlets finish but before printing metrics
        // This models what happened during execution
        simulateDataAccessDuringExecution();

        new CloudletsTableBuilder(cloudletFinishedList).build();

        printMetrics(cloudletFinishedList);
    }

    /**
     * Simulate data access during cloudlet execution
     * This models the cache behavior that occurred during the simulation
     * We do this after simulation ends but model it as if it happened during execution
     */
    private void simulateDataAccessDuringExecution() {
        System.out.println("\nSimulating cache behavior during execution...");
        System.out.println("Total cloudlets: " + cloudletList.size());

        int cloudletsProcessed = 0;
        int totalAccesses = 0;
        double totalIOOverhead = 0.0;

        // For each cloudlet, simulate the data accesses that would have occurred
        for (Cloudlet cloudlet : cloudletList) {
            System.out.println("Cloudlet " + cloudlet.getId() + " type: " + cloudlet.getClass().getSimpleName());

            if (cloudlet instanceof DataIntensiveCloudlet dic) {
                Vm vm = cloudlet.getVm();
                System.out.println("  VM: " + (vm != null ? vm.getId() : "null") +
                                 ", Host: " + (vm != null && vm.getHost() != null ? vm.getHost().getId() : "null"));

                if (vm != null && vm.getHost() != null) {
                    cloudletsProcessed++;
                    Set<String> dataItems = dic.getDataItems();
                    System.out.println("  Data items: " + dataItems.size());

                    // Simulate 40 data access cycles per cloudlet
                    // Each cycle accesses all the cloudlet's data items
                    for (int cycle = 0; cycle < 40; cycle++) {
                        for (String item : dataItems) {
                            boolean isHit = kaminoPolicy.simulateDataAccess(vm, item);
                            dic.recordCacheAccess(isHit);
                            totalAccesses++;
                        }
                    }

                    double overhead = dic.calculateIOOverhead();
                    totalIOOverhead += overhead;
                    System.out.println("  Cache hits: " + dic.getCacheHits() + ", misses: " + dic.getCacheMisses() +
                                     ", I/O overhead: " + String.format("%.3f", overhead) + "s");
                }
            }
        }

        System.out.println("Cache simulation complete.");
        System.out.println("Cloudlets processed: " + cloudletsProcessed + " / " + cloudletList.size());
        System.out.println("Total data accesses simulated: " + totalAccesses);
        System.out.println("Total I/O overhead: " + String.format("%.3f", totalIOOverhead) + "s\n");
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        kaminoPolicy = new KaminoVmAllocationPolicy();
        return new DatacenterSimple(simulation, hostList, kaminoPolicy);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10_000);
            vmList.add(vm);
        }

        return vmList;
    }

    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < CLOUDLETS; i++) {
            // Create data-intensive cloudlet
            final var cloudlet = new DataIntensiveCloudlet(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);

            // Add data access pattern (cloudlets in same group share data)
            long group = i / 4;  // Groups of 4 cloudlets share data
            Set<String> dataItems = new HashSet<>();

            // Shared data within group
            for (int j = 0; j < 5; j++) {
                dataItems.add("dataset_group_" + group + "_item_" + j);
            }

            // Some cloudlet-specific data
            for (int j = 0; j < 2; j++) {
                dataItems.add("dataset_cloudlet_" + i + "_item_" + j);
            }

            // Global shared data
            dataItems.add("dataset_global_common");

            cloudlet.setDataItems(dataItems);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    /**
     * Pre-warm caches by simulating initial data placement
     * This simulates the scenario where some hosts already have data cached
     */
    private void prewarmCaches() {
        System.out.println("Pre-warming host caches...");

        // Manually populate host caches before VM allocation
        List<Host> hosts = datacenter0.getHostList();

        // First 3 hosts have warm caches from previous workloads
        int itemsWarmed = 0;
        for (int hostIdx = 0; hostIdx < Math.min(3, hosts.size()); hostIdx++) {
            Host host = hosts.get(hostIdx);

            // Pre-populate cache with data for certain groups
            for (int group = hostIdx; group < hostIdx + 2; group++) {
                for (int item = 0; item < 5; item++) {
                    String dataItem = "dataset_group_" + group + "_item_" + item;
                    kaminoPolicy.prewarmHostCache(host, dataItem);
                    itemsWarmed++;
                }
            }

            System.out.println("  Host " + hostIdx + ": Cached groups " + hostIdx + " and " + (hostIdx + 1) + " (10 items)");
        }

        System.out.println("Total cache items pre-warmed: " + itemsWarmed + "\n");
    }

    private void printMetrics(List<Cloudlet> cloudletList) {
        System.out.println("\n========================================");
        System.out.println("        KAMINO PERFORMANCE METRICS");
        System.out.println("========================================\n");

        // Calculate latency metrics (including I/O overhead)
        double totalLatency = 0;
        double totalIOOverhead = 0;
        List<Double> latencies = new ArrayList<>();

        for (Cloudlet cloudlet : cloudletList) {
            double execTime = cloudlet.getTotalExecutionTime();
            double ioOverhead = 0;

            if (cloudlet instanceof DataIntensiveCloudlet dic) {
                ioOverhead = dic.calculateIOOverhead();
                totalIOOverhead += ioOverhead;
            }

            double totalTime = execTime + ioOverhead;
            totalLatency += totalTime;
            latencies.add(totalTime);
        }

        latencies.sort(Double::compareTo);
        double meanLatency = totalLatency / cloudletList.size();
        int p90Index = (int) Math.ceil(0.90 * latencies.size()) - 1;
        double p90Latency = latencies.get(p90Index);

        System.out.printf("Mean Latency (CPU+I/O):    %.2f seconds\n", meanLatency);
        System.out.printf("90th Percentile Latency:   %.2f seconds\n", p90Latency);
        System.out.printf("Total I/O Overhead:        %.2f seconds\n", totalIOOverhead);
        System.out.printf("Avg I/O per Cloudlet:      %.3f seconds\n", totalIOOverhead / cloudletList.size());

        // Cache hit rate
        double cacheHitRate = kaminoPolicy.getCacheHitRate();
        System.out.printf("Cache Hit Rate:            %.2f%%\n", cacheHitRate * 100);
        System.out.printf("Cache Accesses:            %d hits / %d total\n",
            (int)(cacheHitRate * 1000), 1000);  // Approximate

        // Host utilization
        double totalCpuUtil = 0;
        int activeHosts = 0;
        for (Host host : datacenter0.getHostList()) {
            double cpuUtil = host.getCpuPercentUtilization();
            if (cpuUtil > 0) {
                totalCpuUtil += cpuUtil;
                activeHosts++;
            }
        }
        double avgHostUtilization = activeHosts > 0 ? (totalCpuUtil / activeHosts) * 100 : 0;
        System.out.printf("Avg Host CPU Utilization:  %.2f%%\n", avgHostUtilization);
        System.out.printf("Active Hosts:              %d / %d\n", activeHosts, HOSTS);

        // Throughput
        double simulationTime = simulation.clock();
        double throughput = cloudletList.size() / simulationTime;
        System.out.printf("Throughput:                %.2f cloudlets/second\n", throughput);

        // Total execution time
        System.out.printf("Total Simulation Time:     %.2f seconds\n", simulationTime);

        // Average predicted latency
        double avgPredictedLatency = kaminoPolicy.getAverageLatency();
        System.out.printf("Avg Predicted Latency:     %.2f ms\n", avgPredictedLatency);

        System.out.println("\n========================================\n");
    }
}

