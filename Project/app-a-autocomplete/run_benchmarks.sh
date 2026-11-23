#!/usr/bin/env bash
# Runs my whole Stage 4 benchmark on Mac/Linux: each solution in its own JVM, then the counters.
#
# From the app-a-autocomplete folder:
#   ./run_benchmarks.sh            (full run, about 5 minutes)
#   ./run_benchmarks.sh --quick    (1-minute check, writes results_quick)
#
# Then make the plots and the PDF:   python3 plot_results.py
set -euo pipefail
cd "$(dirname "$0")"
mvn -q -DskipTests compile

TIMING="-Xmx2g -Xms2g -Dautocomplete.counters=false -cp target/classes autocomplete.bench.Benchmark"
java $TIMING --solution hash "$@"
java $TIMING --solution tree "$@"
java -Xmx2g -Xms2g -cp target/classes autocomplete.bench.Benchmark --counters "$@"