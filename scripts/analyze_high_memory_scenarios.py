import os, re, math, csv
from dataclasses import dataclass
from typing import List, Optional, Dict
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

MAGENTA = "#fd3db5"
BLUE = "#2E8B57"
RED = "#DC143C"

# All possible log files from different scenarios
LOGS = {
    "baseline": {
        "zgc": "logs/gc-zgc.log",
        "shenandoah": "logs/gc-shenandoah.log",
    },
    "high1": {
        "zgc": "logs/gc-zgc-high1.log",
        "shenandoah": "logs/gc-shenandoah-high1.log",
    },
    "high2": {
        "zgc": "logs/gc-zgc-high2.log", 
        "shenandoah": "logs/gc-shenandoah-high2.log",
    },
    "high3": {
        "zgc": "logs/gc-zgc-high3.log",
        "shenandoah": "logs/gc-shenandoah-high3.log",
    }
}

os.makedirs("reports", exist_ok=True)

def parse_unified_gc_log(path: str) -> pd.DataFrame:
    pattern_pause_ms = re.compile(r"Pause[^:]*?:\s*([\d.]+)\s*ms")
    pattern_pause_s  = re.compile(r"Pause[^:]*?:\s*([\d.]+)\s*s")
    pattern_prefix = re.compile(r"^(?:(?P<ts>\d{4}-\d{2}-\d{2}T[0-9:.+-]+):\s*)?(?P<uptime>[\d.]+)s?:\s*")

    rows = []
    with open(path, 'r', errors='ignore') as f:
        for line in f:
            if "Pause" not in line:
                continue
            m_ms = pattern_pause_ms.search(line)
            m_s  = pattern_pause_s.search(line)
            if not (m_ms or m_s):
                continue
            pause_ms = float(m_ms.group(1)) if m_ms else float(m_s.group(1))*1000.0
            mp = pattern_prefix.search(line)
            ts = mp.group('ts') if mp else None
            uptime = float(mp.group('uptime')) if mp and mp.group('uptime') else None
            rows.append({
                "timestamp": ts, "uptime_sec": uptime, "pause_ms": pause_ms
            })
    df = pd.DataFrame(rows)
    return df

def percentile(series: pd.Series, q: float) -> float:
    if series.empty: return float('nan')
    return float(series.quantile(q/100.0))

def calculate_gc_score(df: pd.DataFrame) -> float:
    """Calculate a comprehensive GC performance score (lower is better)"""
    if df.empty:
        return float('inf')
    
    # Weight different aspects of GC performance
    p99 = percentile(df['pause_ms'], 99)
    p95 = percentile(df['pause_ms'], 95)
    max_pause = df['pause_ms'].max()
    mean_pause = df['pause_ms'].mean()
    total_pause = df['pause_ms'].sum()
    
    # Composite score emphasizing tail latencies (P99) and max pause
    score = (p99 * 3) + (p95 * 2) + (max_pause * 2) + mean_pause + (total_pause / 1000)
    return score

# Collect all available data
all_frames = []
scenario_results = {}

for scenario, gc_logs in LOGS.items():
    scenario_results[scenario] = {}
    for gc_name, path in gc_logs.items():
        if os.path.exists(path):
            print(f"Processing {scenario}-{gc_name}: {path}")
            df = parse_unified_gc_log(path)
            df['source'] = f"{gc_name}-{scenario}"
            df['gc'] = gc_name
            df['scenario'] = scenario
            all_frames.append(df)
            scenario_results[scenario][gc_name] = df
        else:
            print(f"Missing log: {path} (skipping)")

if not all_frames:
    print("No logs found. Run benchmarks first.")
    raise SystemExit(0)

# Combine all data
df_all = pd.concat(all_frames, ignore_index=True)
df_all.to_csv("reports/gc-events-detailed.csv", index=False)

# Generate comprehensive summary
summary_rows = []
winner_analysis = {}

for scenario, gc_data in scenario_results.items():
    if not gc_data:
        continue
        
    scenario_scores = {}
    print(f"\n=== SCENARIO: {scenario.upper()} ===")
    
    for gc_name, df in gc_data.items():
        if df.empty:
            continue
            
        events = len(df)
        total_pause = df['pause_ms'].sum()
        max_pause = df['pause_ms'].max()
        mean_pause = df['pause_ms'].mean()
        p95 = percentile(df['pause_ms'], 95)
        p99 = percentile(df['pause_ms'], 99)
        score = calculate_gc_score(df)
        
        scenario_scores[gc_name] = score
        
        summary_rows.append({
            'scenario': scenario,
            'gc': gc_name,
            'events': events,
            'total_pause_ms': total_pause,
            'max_pause_ms': max_pause,
            'p95_ms': p95,
            'p99_ms': p99,
            'mean_ms': mean_pause,
            'gc_score': score
        })
        
        print(f"{gc_name:12}: Events={events:4d}, Total={total_pause:7.1f}ms, "
              f"Max={max_pause:6.1f}ms, P95={p95:6.1f}ms, P99={p99:6.1f}ms, Score={score:.1f}")
    
    # Determine winner for this scenario
    if scenario_scores:
        winner = min(scenario_scores, key=scenario_scores.get)
        winner_score = scenario_scores[winner]
        print(f"WINNER: {winner} (score: {winner_score:.1f})")
        winner_analysis[scenario] = {
            'winner': winner,
            'score': winner_score,
            'scores': scenario_scores.copy()
        }

# Create summary DataFrame and save
summary_df = pd.DataFrame(summary_rows)
summary_df.to_csv("reports/gc-comprehensive-summary.csv", index=False)

# Overall winner analysis
print(f"\n{'='*60}")
print("OVERALL WINNER ANALYSIS")
print(f"{'='*60}")

overall_scores = {}
for scenario, analysis in winner_analysis.items():
    for gc, score in analysis['scores'].items():
        if gc not in overall_scores:
            overall_scores[gc] = []
        overall_scores[gc].append(score)

# Calculate average scores across all scenarios
avg_scores = {gc: np.mean(scores) for gc, scores in overall_scores.items()}
overall_winner = min(avg_scores, key=avg_scores.get)

print(f"Average GC Scores across all scenarios:")
for gc, score in sorted(avg_scores.items(), key=lambda x: x[1]):
    wins = sum(1 for analysis in winner_analysis.values() if analysis['winner'] == gc)
    print(f"{gc:12}: {score:8.1f} (won {wins}/{len(winner_analysis)} scenarios)")

print(f"\nOVERALL WINNER: {overall_winner}")

# Create visualizations
plt.style.use('default')

# 1. Pause distribution comparison
fig, axes = plt.subplots(2, 2, figsize=(15, 12))
fig.suptitle('GC Pause Distributions by Scenario', fontsize=16)

for i, (scenario, gc_data) in enumerate(scenario_results.items()):
    if not gc_data or i >= 4:
        continue
    
    ax = axes[i//2, i%2]
    colors = [MAGENTA, BLUE]
    
    for j, (gc_name, df) in enumerate(gc_data.items()):
        if not df.empty:
            ax.hist(df['pause_ms'], bins=50, alpha=0.6, label=gc_name, 
                   color=colors[j % len(colors)], histtype='stepfilled')
    
    ax.set_xlabel("Pause (ms)")
    ax.set_ylabel("Count")
    ax.set_title(f"Scenario: {scenario}")
    ax.legend()
    ax.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig("reports/gc-pause-distributions-scenarios.png", dpi=160, bbox_inches='tight')
plt.close()

# 2. Performance comparison chart
scenarios = list(winner_analysis.keys())
gcs = list(avg_scores.keys())

fig, ax = plt.subplots(figsize=(12, 8))
x = np.arange(len(scenarios))
width = 0.35

for i, gc in enumerate(gcs):
    scores = [winner_analysis[scenario]['scores'].get(gc, 0) for scenario in scenarios]
    ax.bar(x + i*width, scores, width, label=gc, alpha=0.8)

ax.set_xlabel('Scenarios')
ax.set_ylabel('GC Performance Score (lower is better)')
ax.set_title('GC Performance Comparison Across High Memory Scenarios')
ax.set_xticks(x + width/2)
ax.set_xticklabels(scenarios)
ax.legend()
ax.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig("reports/gc-performance-comparison.png", dpi=160, bbox_inches='tight')
plt.close()

# 3. Winner summary
with open("reports/winner-analysis.txt", "w") as f:
    f.write("GC BENCHMARK WINNER ANALYSIS\n")
    f.write("="*50 + "\n\n")
    
    f.write("Per-Scenario Winners:\n")
    for scenario, analysis in winner_analysis.items():
        f.write(f"  {scenario}: {analysis['winner']} (score: {analysis['score']:.1f})\n")
    
    f.write(f"\nOVERALL WINNER: {overall_winner}\n")
    f.write(f"Average Score: {avg_scores[overall_winner]:.1f}\n\n")
    
    f.write("Average Scores:\n")
    for gc, score in sorted(avg_scores.items(), key=lambda x: x[1]):
        wins = sum(1 for analysis in winner_analysis.values() if analysis['winner'] == gc)
        f.write(f"  {gc}: {score:.1f} (won {wins}/{len(winner_analysis)} scenarios)\n")

print(f"\nReports generated:")
print(f"  - reports/gc-events-detailed.csv")
print(f"  - reports/gc-comprehensive-summary.csv") 
print(f"  - reports/gc-pause-distributions-scenarios.png")
print(f"  - reports/gc-performance-comparison.png")
print(f"  - reports/winner-analysis.txt")