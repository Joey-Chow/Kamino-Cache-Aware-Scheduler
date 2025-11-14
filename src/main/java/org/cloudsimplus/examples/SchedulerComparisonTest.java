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

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
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
import java.util.List;

/**
 * Comprehensive test runner comparing Kamino vs Benchmark schedulers
 * across multiple workload scenarios.
 *
 * @author Kamino Research Project
 * @since CloudSim Plus 8.5.7
 */
public class SchedulerComparisonTest {

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  KAMINO SCHEDULER COMPARISON TEST");
        System.out.println("  Testing cache-aware vs standard allocation policies");
        System.out.println("=".repeat(80) + "\n");

        // Test scenario 1: Moderate load
        System.out.println("\n>>> SCENARIO 1: Moderate Load (6 hosts, 12 VMs, 24 cloudlets)");
        TestResult benchmark1 = runTest("Benchmark", false, 6, 12, 24);
        TestResult kamino1 = runTest("Kamino", true, 6, 12, 24);

        // Test scenario 2: High load
        System.out.println("\n>>> SCENARIO 2: High Load (4 hosts, 16 VMs, 48 cloudlets)");
        TestResult benchmark2 = runTest("Benchmark", false, 4, 16, 48);
        TestResult kamino2 = runTest("Kamino", true, 4, 16, 48);

        // Test scenario 3: Unbalanced load
        System.out.println("\n>>> SCENARIO 3: Unbalanced Load (8 hosts, 10 VMs, 30 cloudlets)");
        TestResult benchmark3 = runTest("Benchmark", false, 8, 10, 30);
        TestResult kamino3 = runTest("Kamino", true, 8, 10, 30);

        // Print comparison report
        printComparisonReport(benchmark1, kamino1, benchmark2, kamino2, benchmark3, kamino3);
    }

    private static TestResult runTest(String name, boolean useKamino, int hosts, int vms, int cloudlets) {
        TestRunner runner = new TestRunner(name, useKamino, hosts, vms, cloudlets);
        return runner.run();
    }

    private static void printComparisonReport(TestResult b1, TestResult k1,
                                             TestResult b2, TestResult k2,
                                             TestResult b3, TestResult k3) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  FINAL COMPARISON REPORT");
        System.out.println("=".repeat(80) + "\n");

        printScenarioComparison("SCENARIO 1: Moderate Load", b1, k1);
        printScenarioComparison("SCENARIO 2: High Load", b2, k2);
        printScenarioComparison("SCENARIO 3: Unbalanced Load", b3, k3);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("  OVERALL IMPROVEMENTS (Kamino vs Benchmark)");
        System.out.println("=".repeat(80) + "\n");

        double avgLatencyImprovement =
            ((b1.meanLatency - k1.meanLatency) / b1.meanLatency +
             (b2.meanLatency - k2.meanLatency) / b2.meanLatency +
             (b3.meanLatency - k3.meanLatency) / b3.meanLatency) / 3.0 * 100;

        double avgThroughputImprovement =
            ((k1.throughput - b1.throughput) / b1.throughput +
             (k2.throughput - b2.throughput) / b2.throughput +
             (k3.throughput - b3.throughput) / b3.throughput) / 3.0 * 100;

        System.out.printf("Average Latency Improvement:     %.2f%%\n", avgLatencyImprovement);
        System.out.printf("Average Throughput Improvement:  %.2f%%\n", avgThroughputImprovement);
        System.out.printf("Kamino Cache Hit Rate (Avg):    %.2f%%\n",
            (k1.cacheHitRate + k2.cacheHitRate + k3.cacheHitRate) / 3.0);
        System.out.printf("Resource Efficiency Improvement: %.2f%%\n",
            avgLatencyImprovement * 0.5 + avgThroughputImprovement * 0.5);

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    private static void printScenarioComparison(String scenario, TestResult benchmark, TestResult kamino) {
        System.out.println("\n" + scenario);
        System.out.println("-".repeat(80));
        System.out.printf("%-30s %15s %15s %15s\n", "Metric", "Benchmark", "Kamino", "Improvement");
        System.out.println("-".repeat(80));

        double latencyImprovement = ((benchmark.meanLatency - kamino.meanLatency) / benchmark.meanLatency) * 100;
        System.out.printf("%-30s %15.2f %15.2f %14.2f%%\n",
            "Mean Latency (s)", benchmark.meanLatency, kamino.meanLatency, latencyImprovement);

        double p90Improvement = ((benchmark.p90Latency - kamino.p90Latency) / benchmark.p90Latency) * 100;
        System.out.printf("%-30s %15.2f %15.2f %14.2f%%\n",
            "90th %ile Latency (s)", benchmark.p90Latency, kamino.p90Latency, p90Improvement);

        System.out.printf("%-30s %15s %15.2f %15s\n",
            "Cache Hit Rate (%)", "N/A", kamino.cacheHitRate, "-");

        double throughputImprovement = ((kamino.throughput - benchmark.throughput) / benchmark.throughput) * 100;
        System.out.printf("%-30s %15.2f %15.2f %14.2f%%\n",
            "Throughput (cl/s)", benchmark.throughput, kamino.throughput, throughputImprovement);

        double hostUtilImprovement = ((kamino.hostUtilization - benchmark.hostUtilization) /
            Math.max(benchmark.hostUtilization, 0.01)) * 100;
        System.out.printf("%-30s %15.2f %15.2f %14.2f%%\n",
            "Host Utilization (%)", benchmark.hostUtilization, kamino.hostUtilization, hostUtilImprovement);

        System.out.printf("%-30s %15d %15d %15s\n",
            "Active Hosts", benchmark.activeHosts, kamino.activeHosts,
            (kamino.activeHosts < benchmark.activeHosts ? "Better" : "Same"));

        System.out.println("-".repeat(80));
    }

    static class TestResult {
        String name;
        double meanLatency;
        double p90Latency;
        double cacheHitRate;
        double throughput;
        double hostUtilization;
        int activeHosts;
        double simulationTime;
    }

    static class TestRunner {
        private final String name;
        private final boolean useKamino;
        private final int numHosts;
        private final int numVms;
        private final int numCloudlets;

        private static final int HOST_PES = 8;
        private static final int HOST_MIPS = 1000;
        private static final int HOST_RAM = 4096;
        private static final long HOST_BW = 10_000;
        private static final long HOST_STORAGE = 1_000_000;

        private static final int VM_PES = 4;
        private static final int CLOUDLET_PES = 2;
        private static final int CLOUDLET_LENGTH = 10_000;

        TestRunner(String name, boolean useKamino, int hosts, int vms, int cloudlets) {
            this.name = name;
            this.useKamino = useKamino;
            this.numHosts = hosts;
            this.numVms = vms;
            this.numCloudlets = cloudlets;
        }

        TestResult run() {
            CloudSimPlus simulation = new CloudSimPlus();

            VmAllocationPolicy policy = useKamino ?
                new KaminoVmAllocationPolicy() : new VmAllocationPolicySimple();

            Datacenter datacenter = createDatacenter(simulation, policy);
            DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

            List<Vm> vmList = createVms();
            List<Cloudlet> cloudletList = createCloudlets();
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);

            simulation.start();

            List<Cloudlet> finishedList = broker.getCloudletFinishedList();

            TestResult result = new TestResult();
            result.name = name;

            // Calculate metrics
            double totalLatency = 0;
            List<Double> latencies = new ArrayList<>();
            for (Cloudlet cloudlet : finishedList) {
                double latency = cloudlet.getTotalExecutionTime();
                totalLatency += latency;
                latencies.add(latency);
            }

            latencies.sort(Double::compareTo);
            result.meanLatency = totalLatency / finishedList.size();
            int p90Index = (int) Math.ceil(0.90 * latencies.size()) - 1;
            result.p90Latency = latencies.get(p90Index);

            if (useKamino && policy instanceof KaminoVmAllocationPolicy) {
                result.cacheHitRate = ((KaminoVmAllocationPolicy) policy).getCacheHitRate() * 100;
            } else {
                result.cacheHitRate = 0;
            }

            double totalUtil = 0;
            int activeHosts = 0;
            for (Host host : datacenter.getHostList()) {
                double util = host.getCpuPercentUtilization();
                if (util > 0) {
                    totalUtil += util;
                    activeHosts++;
                }
            }
            result.hostUtilization = activeHosts > 0 ? (totalUtil / activeHosts) * 100 : 0;
            result.activeHosts = activeHosts;

            result.simulationTime = simulation.clock();
            result.throughput = finishedList.size() / result.simulationTime;

            System.out.printf("  %s: Mean=%.2fs, P90=%.2fs, Cache=%.1f%%, Throughput=%.2f, Util=%.1f%%\n",
                name, result.meanLatency, result.p90Latency, result.cacheHitRate,
                result.throughput, result.hostUtilization);

            return result;
        }

        private Datacenter createDatacenter(CloudSimPlus simulation, VmAllocationPolicy policy) {
            List<Host> hostList = new ArrayList<>(numHosts);
            for (int i = 0; i < numHosts; i++) {
                hostList.add(createHost());
            }
            return new DatacenterSimple(simulation, hostList, policy);
        }

        private Host createHost() {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int i = 0; i < HOST_PES; i++) {
                peList.add(new PeSimple(HOST_MIPS));
            }
            return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
        }

        private List<Vm> createVms() {
            List<Vm> vmList = new ArrayList<>(numVms);
            for (int i = 0; i < numVms; i++) {
                Vm vm = new VmSimple(HOST_MIPS, VM_PES);
                vm.setRam(512).setBw(1000).setSize(10_000);
                vmList.add(vm);
            }
            return vmList;
        }

        private List<Cloudlet> createCloudlets() {
            List<Cloudlet> cloudletList = new ArrayList<>(numCloudlets);
            UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.5);

            for (int i = 0; i < numCloudlets; i++) {
                Cloudlet cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
                cloudlet.setSizes(1024);
                cloudletList.add(cloudlet);
            }
            return cloudletList;
        }
    }
}

