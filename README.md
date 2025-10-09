# Java GC Performance Benchmark: ZGC vs Shenandoah

**🏆 Result: ZGC achieves 25-40x better pause times under high memory pressure**

This project provides a comprehensive Docker-based benchmarking suite comparing **ZGC** and **Shenandoah GC** performance under various memory pressure scenarios. Using realistic workload simulation and isolated container environments, we demonstrate ZGC's superior low-latency performance.

## 🚀 Key Findings

| Scenario | ZGC Mean Pause | Shenandoah Mean Pause | ZGC Advantage |
|----------|:--------------:|:---------------------:|:-------------:|
| HIGH1 (6GB heap) | **0.12ms** | 2.89ms | **24x faster** |
| HIGH2 (4GB heap) | **0.07ms** | 2.64ms | **38x faster** |
| HIGH3 (2GB heap) | **0.11ms** | 4.23ms | **38x faster** |

- **P95 pause times**: ZGC consistently under 0.5ms, Shenandoah 9-12ms
- **Maximum pause**: ZGC peaks at 15ms, Shenandoah reaches 596ms
- **Reliability**: Both GCs successfully completed all extreme scenarios

## 📋 Prerequisites
- Docker Desktop (4GB+ memory allocation recommended)
- Internet access for base image downloads
- Python 3.9+ for log analysis and visualization
- 30 minutes for complete benchmark suite

## 🚀 Quick Start

### Option 1: Single Scenario (5 minutes)
```bash
# Clone and build
git clone https://github.com/your-repo/java-gc-bench-docker
cd java-gc-bench-docker
mvn clean package

# Run baseline comparison
docker-compose up --abort-on-container-exit

# View results
python quick_analysis.py
```

### Option 2: Full Benchmark Suite (30 minutes)
```bash
# Run all high-memory pressure scenarios
./run-high-memory-benchmark.ps1  # Windows PowerShell
./run-high-memory-benchmark.sh   # Linux/Mac

# Comprehensive analysis
python scripts/analyze_high_memory_scenarios.py
```

## 📊 Test Scenarios

### Baseline Scenario
- **Heap**: 8GB (-Xms8g -Xmx8g)
- **Allocation**: 65KB blocks
- **Batch**: 4,000 allocations per burst
- **Leak Rate**: Keep every 200th allocation
- **Duration**: 120 seconds

### High Memory Pressure Scenarios

| Scenario | Heap | Allocation Size | Batch Size | Leak Rate | Pressure Level |
|----------|------|----------------|------------|-----------|----------------|
| **HIGH1** | 6GB | 65KB | 8,000 | 1/100 | Elevated (50%) |
| **HIGH2** | 4GB | 131KB | 15,000 | 1/50 | Extreme (80%) |
| **HIGH3** | 2GB | 256KB | 30,000 | 1/25 | Maximum (95%) |

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│              Docker Host                │
├─────────────────┬─────────────────────┤
│   ZGC Container │ Shenandoah Container │
│                 │                     │
│ Eclipse Temurin │ Red Hat OpenJDK 21  │
│ 21-JDK          │                     │
│ -XX:+UseZGC     │ -XX:+UseShenandoahGC│
│                 │                     │
│ LeakAndChurn ←──┼──→ LeakAndChurn     │
│ Workload        │     Workload        │
└─────────────────┴─────────────────────┘
         ↓                 ↓
┌─────────────────────────────────────────┐
│        Shared Volume: ./logs/           │
│  gc-zgc.log        gc-shenandoah.log   │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│         Python Analysis Pipeline        │
│  • Parse pause times                   │
│  • Generate statistics                 │
│  • Create visualizations               │
│  • Determine winner                    │
└─────────────────────────────────────────┘
```

## ⚙️ Customization

### Memory and Workload Tuning
Edit environment variables in `docker-compose.yml`:

```yaml
environment:
  HEAP: "-Xms4g -Xmx4g -XX:+AlwaysPreTouch"
  WORK: "alloc.bytes=32768 churn.batch=2000 leak.every=400 run.seconds=60"
```

### GC-Specific Optimization
```bash
# ZGC optimization
-XX:+UseZGC
-XX:SoftMaxHeapSize=6g
-XX:+AlwaysPreTouch

# Shenandoah optimization  
-XX:+UseShenandoahGC
-XX:ShenandoahGCHeuristics=adaptive
```

## 📈 Analysis & Reporting

### Quick Analysis
```bash
python quick_analysis.py
# Output: Console summary with winner determination
```

### Comprehensive Analysis
```bash
python scripts/parse_gc_logs.py
# Generates:
# - reports/gc-summary.csv (detailed metrics)
# - reports/gc-pause-distribution.png
# - reports/gc-pauses-over-time.png
```

### Sample Output
```
=== GC BENCHMARK RESULTS ===
HIGH1: ZGC wins (24x better mean pause)
HIGH2: ZGC wins (38x better mean pause)  
HIGH3: ZGC wins (38x better mean pause)

OVERALL WINNER: ZGC
- P95 pause times: 0.4ms vs 12ms
- Consistent performance under pressure
- 25-40x better pause times across scenarios
```

## 🎯 Workload Simulation

The `LeakAndChurn` class simulates realistic enterprise application memory patterns:

```java
public class LeakAndChurn {
    private static final Map<Integer, byte[]> LEAK = new HashMap<>();
    
    // High-rate allocation bursts (simulates request processing)
    byte[] allocation = new byte[allocBytes];
    
    // Memory fragmentation (simulates object lifecycle)
    if (random.nextBoolean()) continue;
    
    // Controlled memory leaks (simulates caches, connection pools)
    if (ops++ % leakEvery == 0) {
        LEAK.put(ops, allocation);
    }
}
```

**Memory Pressure Characteristics:**
- **Burst Allocation**: Simulates HTTP request processing spikes
- **Fragmentation**: Random object dropping creates realistic fragmentation
- **Memory Leaks**: Gradual accumulation simulates caches and connection pools
- **Sustained Pressure**: Long-running tests capture steady-state behavior

## 🔧 Troubleshooting

### Common Issues

**OutOfMemoryError in HIGH3 scenario:**
- Expected behavior under extreme pressure
- Both GCs may fail, but logs are still generated
- Reduce memory pressure in `docker-compose-high3.yml`

**Docker build failures:**
- Run `mvn clean package` locally first
- Use optimized Dockerfiles: `docker/*/Dockerfile.optimized`
- Check Docker Desktop memory allocation (4GB+ recommended)

**Missing log files:**
- Ensure `logs/` directory exists
- Check container permissions for volume mounts
- Verify containers ran to completion

## 📚 Further Reading

- [ZGC Documentation](https://openjdk.java.net/jeps/377)
- [Shenandoah GC Guide](https://wiki.openjdk.java.net/display/shenandoah/Main)
- [Docker Memory Management](https://docs.docker.com/config/containers/resource_constraints/)
- [JVM GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)

## 🤝 Contributing

We welcome contributions! Areas for enhancement:

- Additional GC implementations (G1, Parallel, etc.)
- More realistic workload patterns
- Alternative analysis methodologies  
- Performance regression testing
- CI/CD integration examples

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

## 🏷️ Tags

`java` `garbage-collection` `performance` `docker` `benchmarking` `zgc` `shenandoah` `microservices` `jvm` `memory-management`
