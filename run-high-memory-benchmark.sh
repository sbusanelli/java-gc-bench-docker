#!/usr/bin/env bash
set -euo pipefail

echo "=== Java GC High Memory Pressure Benchmark Suite ==="
echo "This will run 4 different memory stress scenarios to determine the GC winner"
echo ""

# Ensure logs directory exists
mkdir -p logs

# Clean previous logs
echo "Cleaning previous logs..."
rm -f logs/gc-*.log

# Function to run a scenario
run_scenario() {
    local scenario=$1
    local compose_file=$2
    
    echo ""
    echo "=== Running Scenario: $scenario ==="
    echo "Configuration: $compose_file"
    
    # Build containers
    docker compose -f "$compose_file" build
    
    # Run the benchmark
    echo "Starting containers..."
    docker compose -f "$compose_file" up --abort-on-container-exit
    
    # Rename logs to include scenario
    for gc in zgc shenandoah; do
        if [ -f "logs/gc-$gc.log" ]; then
            mv "logs/gc-$gc.log" "logs/gc-$gc-$scenario.log"
            echo "Saved: logs/gc-$gc-$scenario.log"
        fi
    done
    
    echo "Scenario $scenario completed."
    sleep 5
}

# Run baseline scenario (original settings)
echo "=== Running BASELINE Scenario ==="
docker compose build
docker compose up --abort-on-container-exit
# Keep baseline logs as-is for now

# Run high memory pressure scenarios
run_scenario "high1" "docker-compose-high1.yml"
run_scenario "high2" "docker-compose-high2.yml" 
run_scenario "high3" "docker-compose-high3.yml"

echo ""
echo "=== All scenarios completed! ==="
echo "Available log files:"
ls -lh logs/gc-*.log

echo ""
echo "Running comprehensive analysis..."
python3 scripts/analyze_high_memory_scenarios.py

echo ""
echo "=== BENCHMARK COMPLETE ==="
echo "Check reports/ directory for:"
echo "  - winner-analysis.txt (clear winner determination)"
echo "  - gc-performance-comparison.png (visual comparison)"  
echo "  - gc-comprehensive-summary.csv (detailed metrics)"