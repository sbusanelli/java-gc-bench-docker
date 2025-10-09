import re
import os
from statistics import mean

def analyze_log(filename):
    """Extract pause times and calculate statistics"""
    if not os.path.exists(filename):
        return None
    
    pauses = []
    with open(filename, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            if 'Pause' in line and 'ms' in line:
                # Extract pause time in ms
                match = re.search(r'(\d+\.?\d*)\s*ms', line)
                if match:
                    pauses.append(float(match.group(1)))
    
    if not pauses:
        return None
    
    return {
        'count': len(pauses),
        'total': sum(pauses),
        'mean': mean(pauses),
        'max': max(pauses),
        'p95': sorted(pauses)[int(len(pauses) * 0.95)] if len(pauses) > 20 else max(pauses),
        'p99': sorted(pauses)[int(len(pauses) * 0.99)] if len(pauses) > 100 else max(pauses)
    }

print("=== GC BENCHMARK RESULTS SUMMARY ===\n")

# Analyze baseline
print("BASELINE SCENARIO:")
zgc_base = analyze_log('logs/gc-zgc.log')
shen_base = analyze_log('logs/gc-shenandoah.log')

if zgc_base and shen_base:
    print(f"ZGC      : {zgc_base['count']:4d} pauses, Total: {zgc_base['total']:7.1f}ms, Max: {zgc_base['max']:6.2f}ms, P95: {zgc_base['p95']:6.2f}ms, Mean: {zgc_base['mean']:6.2f}ms")
    print(f"Shenandoah: {shen_base['count']:4d} pauses, Total: {shen_base['total']:7.1f}ms, Max: {shen_base['max']:6.2f}ms, P95: {shen_base['p95']:6.2f}ms, Mean: {shen_base['mean']:6.2f}ms")
    print()

# Analyze high memory scenarios
scenarios = ['high1', 'high2', 'high3']
results = {}

for scenario in scenarios:
    print(f"{scenario.upper()} SCENARIO:")
    zgc_result = analyze_log(f'logs/gc-zgc-{scenario}.log')
    shen_result = analyze_log(f'logs/gc-shenandoah-{scenario}.log')
    
    if zgc_result:
        print(f"ZGC      : {zgc_result['count']:4d} pauses, Total: {zgc_result['total']:7.1f}ms, Max: {zgc_result['max']:6.2f}ms, P95: {zgc_result['p95']:6.2f}ms, Mean: {zgc_result['mean']:6.2f}ms")
    else:
        print("ZGC      : Failed (OOM)")
        
    if shen_result:
        print(f"Shenandoah: {shen_result['count']:4d} pauses, Total: {shen_result['total']:7.1f}ms, Max: {shen_result['max']:6.2f}ms, P95: {shen_result['p95']:6.2f}ms, Mean: {shen_result['mean']:6.2f}ms")
    else:
        print("Shenandoah: Failed (OOM)")
    
    results[scenario] = {'zgc': zgc_result, 'shenandoah': shen_result}
    print()

# Determine winner
print("="*60)
print("WINNER ANALYSIS")
print("="*60)

# Count successful runs
zgc_successes = sum(1 for scenario in scenarios if results[scenario]['zgc'] is not None)
shen_successes = sum(1 for scenario in scenarios if results[scenario]['shenandoah'] is not None)

print(f"Successful runs: ZGC = {zgc_successes}/3, Shenandoah = {shen_successes}/3")

# Compare performance where both succeeded
if zgc_base and shen_base:
    baseline_winner = "ZGC" if zgc_base['mean'] < shen_base['mean'] else "Shenandoah"
    print(f"Baseline winner (lower mean pause): {baseline_winner}")

# Overall assessment
print(f"\nOVERALL WINNER: ", end="")
if shen_successes > zgc_successes:
    print("SHENANDOAH")
    print("Reason: Better reliability under extreme memory pressure")
elif zgc_successes > shen_successes:
    print("ZGC") 
    print("Reason: Better reliability under extreme memory pressure")
else:
    if zgc_base and shen_base:
        if zgc_base['mean'] < shen_base['mean']:
            print("ZGC")
            print("Reason: Lower average pause times in baseline")
        else:
            print("SHENANDOAH")
            print("Reason: Lower average pause times in baseline")
    else:
        print("TIE - Need more data")