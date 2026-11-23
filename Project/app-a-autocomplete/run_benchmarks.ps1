# Runs my whole Stage 4 benchmark on Windows: each solution in its own JVM, then the counters.
#
# From the app-a-autocomplete folder:
#   powershell -ExecutionPolicy Bypass -File run_benchmarks.ps1           (full run, about 5 minutes)
#   powershell -ExecutionPolicy Bypass -File run_benchmarks.ps1 --quick   (1-minute check, writes results_quick)
#
# Then make the plots and the PDF:   python plot_results.py

Set-Location $PSScriptRoot
mvn -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit 1 }

$timing   = @("-Xmx2g", "-Xms2g", "-Dautocomplete.counters=false", "-cp", "target/classes", "autocomplete.bench.Benchmark")
$counters = @("-Xmx2g", "-Xms2g", "-cp", "target/classes", "autocomplete.bench.Benchmark", "--counters")

java @timing --solution hash @args
if ($LASTEXITCODE -ne 0) { exit 1 }
java @timing --solution tree @args
if ($LASTEXITCODE -ne 0) { exit 1 }
java @counters @args