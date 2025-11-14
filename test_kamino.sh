#!/bin/bash
# Quick test to verify Kamino scheduler with proper output

echo "======================================"
echo "Testing Kamino Scheduler"
echo "======================================"
echo ""

cd /Users/joeychow/MyProjects/cloudsimplus-examples-master

echo "Step 1: Compiling..."
./mvnw -q compile
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi
echo "✓ Compilation successful"
echo ""

echo "Step 2: Running Kamino Scheduler..."
echo "(This may take 20-30 seconds...)"
echo ""

# Run and capture output
./mvnw -q exec:java -Dexec.mainClass="org.cloudsimplus.examples.KaminoSchedulerExample" 2>&1 | tee /tmp/kamino_full_output.txt

echo ""
echo "======================================"
echo "Extracting Key Metrics:"
echo "======================================"
echo ""

# Extract and display key lines
grep -E "(Pre-warming|Cache Hit Rate|Total data accesses|Cloudlets processed)" /tmp/kamino_full_output.txt

echo ""
echo "Full output saved to: /tmp/kamino_full_output.txt"
echo ""

