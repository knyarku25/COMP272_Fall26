# Starts my live demo on Windows: both solutions loaded with the full word list.
#
# From the app-a-autocomplete folder:
#   powershell -ExecutionPolicy Bypass -File run_demo.ps1
#
# Then type a prefix (like th), "check <sentence>", "book pg1342.txt", "help" or "quit".

Set-Location $PSScriptRoot
mvn -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit 1 }

java -Xmx2g "-Dautocomplete.counters=false" -cp target/classes autocomplete.Demo @args
