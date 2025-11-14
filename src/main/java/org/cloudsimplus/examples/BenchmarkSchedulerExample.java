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

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
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
 * Benchmark example using standard VmAllocationPolicySimple (First-Fit).
 * This serves as the baseline for comparison against Kamino scheduler.
 *
 * Uses the same workload configuration as KaminoSchedulerExample:
 * - Same number of hosts, VMs, and cloudlets
 * - Same resource configurations
 * - Only difference: allocation policy
 *
 * Measurements tracked:
 * - Mean & 90th-percentile latency
 * - Host utilization
 * - Throughput
 *
 * @author Kamino Research Project Implementation
 * @since CloudSim Plus 8.5.7
 */
public class BenchmarkSchedulerExample {
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

    public static void main(String[] args) {
        new BenchmarkSchedulerExample();
    }

    private BenchmarkSchedulerExample() {
        System.out.println("\n========================================");
        System.out.println("  BENCHMARK SCHEDULER TEST (First-Fit)");
        System.out.println("========================================\n");

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();

        // Simulate cache behavior (will show poor performance without cache awareness)
        simulateCacheAccess();

        new CloudletsTableBuilder(cloudletFinishedList).build();

        printMetrics(cloudletFinishedList);
    }

    /**
     * Simulate cache access for benchmark (no cache awareness - all cold misses)
     * This will show the penalty of not using cache-aware scheduling
     */
    private void simulateCacheAccess() {
        System.out.println("\nSimulating cache behavior (no cache awareness)...");

        // For benchmark, all accesses are misses since there's no cache management
        for (Cloudlet cloudlet : cloudletList) {
            if (cloudlet instanceof DataIntensiveCloudlet dic) {
                Set<String> dataItems = dic.getDataItems();

                // 40 cycles of data access
                for (int cycle = 0; cycle < 40; cycle++) {
                    for (String item : dataItems) {
                        // All misses in benchmark - no cache awareness!
                        dic.recordCacheAccess(false);
                    }
                }
            }
        }

        System.out.println("Cache simulation complete (all misses - no cache policy).\n");
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        // Use standard First-Fit allocation policy
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
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
            // Use DataIntensiveCloudlet for fair comparison
            final var cloudlet = new DataIntensiveCloudlet(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);

            // Add same data access pattern as Kamino example
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

    private void printMetrics(List<Cloudlet> cloudletList) {
        System.out.println("\n========================================");
        System.out.println("       BENCHMARK PERFORMANCE METRICS");
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

        // Cache hit rate (0% for baseline - no cache awareness)
        System.out.printf("Cache Hit Rate:            0.00%% (no cache awareness)\n");

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

        System.out.println("\n========================================\n");
    }
}

