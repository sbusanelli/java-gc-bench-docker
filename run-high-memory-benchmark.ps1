#!/usr/bin/env pwsh

Write-Host "=== Java GC High Memory Pressure Benchmark Suite ===" -ForegroundColor Cyan
Write-Host "This will run 4 different memory stress scenarios to determine the GC winner"
Write-Host ""

# Ensure logs directory exists
if (!(Test-Path "logs")) {
    New-Item -ItemType Directory -Path "logs" | Out-Null
}

# Clean previous logs
Write-Host "Cleaning previous logs..." -ForegroundColor Yellow
Remove-Item -Path "logs/gc-*.log" -Force -ErrorAction SilentlyContinue

# Function to run a scenario
function Run-Scenario {
    param(
        [string]$Scenario,
        [string]$ComposeFile
    )
    
    Write-Host ""
    Write-Host "=== Running Scenario: $Scenario ===" -ForegroundColor Green
    Write-Host "Configuration: $ComposeFile"
    
    # Build containers
    docker compose -f $ComposeFile build
    
    # Run the benchmark
    Write-Host "Starting containers..."
    docker compose -f $ComposeFile up --abort-on-container-exit
    
    # Rename logs to include scenario
    foreach ($gc in @("zgc", "shenandoah")) {
        $logFile = "logs/gc-$gc.log"
        $newLogFile = "logs/gc-$gc-$Scenario.log"
        if (Test-Path $logFile) {
            Move-Item $logFile $newLogFile
            Write-Host "Saved: $newLogFile" -ForegroundColor Blue
        }
    }
    
    Write-Host "Scenario $Scenario completed." -ForegroundColor Green
    Start-Sleep -Seconds 5
}

# Run baseline scenario (original settings)
Write-Host "=== Running BASELINE Scenario ===" -ForegroundColor Green
docker compose build
docker compose up --abort-on-container-exit
# Keep baseline logs as-is for now

# Run high memory pressure scenarios
Run-Scenario -Scenario "high1" -ComposeFile "docker-compose-high1.yml"
Run-Scenario -Scenario "high2" -ComposeFile "docker-compose-high2.yml" 
Run-Scenario -Scenario "high3" -ComposeFile "docker-compose-high3.yml"

Write-Host ""
Write-Host "=== All scenarios completed! ===" -ForegroundColor Cyan
Write-Host "Available log files:"
Get-ChildItem -Path "logs/gc-*.log" | Format-Table Name, Length, LastWriteTime

Write-Host ""
Write-Host "Running comprehensive analysis..." -ForegroundColor Yellow
python scripts/analyze_high_memory_scenarios.py

Write-Host ""
Write-Host "=== BENCHMARK COMPLETE ===" -ForegroundColor Cyan
Write-Host "Check reports/ directory for:" -ForegroundColor White
Write-Host "  - winner-analysis.txt (clear winner determination)" -ForegroundColor Green
Write-Host "  - gc-performance-comparison.png (visual comparison)" -ForegroundColor Green  
Write-Host "  - gc-comprehensive-summary.csv (detailed metrics)" -ForegroundColor Green