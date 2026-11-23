#!/usr/bin/env bash
# Starts my live demo on Mac/Linux: both solutions loaded with the full word list.
#
# From the app-a-autocomplete folder:
#   ./run_demo.sh
#
# Then type a prefix (like th), "check <sentence>", "book pg1342.txt", "help" or "quit".
set -euo pipefail
cd "$(dirname "$0")"
mvn -q -DskipTests compile
java -Xmx2g -Dautocomplete.counters=false -cp target/classes autocomplete.Demo "$@"
