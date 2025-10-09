import os, re, math, csv
from dataclasses import dataclass
from typing import List, Optional, Dict
import pandas as pd
import matplotlib.pyplot as plt

MAGENTA = "#fd3db5"
LOGS = {
    "zgc": "logs/gc-zgc.log",
    "shenandoah": "logs/gc-shenandoah.log",
    "zing": "logs/gc-zing.log",  # optional
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

all_frames = []
for name, path in LOGS.items():
    if os.path.exists(path):
        df = parse_unified_gc_log(path)
        df['source'] = name
        all_frames.append(df)
    else:
        print(f"Missing log: {path} (skipping)")

if not all_frames:
    print("No logs found. Run docker compose first.")
    raise SystemExit(0)

df = pd.concat(all_frames, ignore_index=True)
df.to_csv("reports/gc-events.csv", index=False)

summary = (df.groupby('source')['pause_ms']
    .agg(events='count', total_pause_ms='sum', max_pause_ms='max', mean_ms='mean')
    .reset_index())
summary['p95_ms'] = df.groupby('source')['pause_ms'].apply(lambda s: percentile(s,95)).values
summary['p99_ms'] = df.groupby('source')['pause_ms'].apply(lambda s: percentile(s,99)).values
summary = summary[['source','events','total_pause_ms','max_pause_ms','p95_ms','p99_ms','mean_ms']]
summary.to_csv("reports/gc-summary.csv", index=False)
print(summary)

# Plot histograms
plt.figure()
for src, sub in df.groupby('source'):
    plt.hist(sub['pause_ms'], bins=50, alpha=0.6, label=src, color=MAGENTA, histtype='stepfilled')
plt.xlabel("Pause (ms)"); plt.ylabel("Count"); plt.title("GC Pause Distribution")
plt.legend(); plt.tight_layout(); plt.savefig("reports/gc-pause-distribution.png", dpi=160); plt.close()

# Plot pauses over time
plt.figure()
for src, sub in df.groupby('source'):
    sub = sub.sort_values('uptime_sec')
    plt.plot(sub['uptime_sec'], sub['pause_ms'], marker='o', linestyle='-', label=src, color=MAGENTA)
plt.xlabel("Uptime (s)"); plt.ylabel("Pause (ms)"); plt.title("GC Pauses Over Time")
plt.legend(); plt.tight_layout(); plt.savefig("reports/gc-pauses-over-time.png", dpi=160); plt.close()

print("Wrote: reports/gc-summary.csv, reports/gc-pause-distribution.png, reports/gc-pauses-over-time.png")
