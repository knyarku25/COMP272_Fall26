"""
Plots and PDF report for my Stage 4 benchmark (COMP 272, Application A).

Reads the CSV files written by the benchmark harness (results/*.csv) and makes:
  - results/plots/*.png   one image per plot
  - docs/Stage4_Benchmark_Results.pdf   machine details, the A-N targets, and all plots
    (for python plot_results.py results_quick, the PDF goes to results_quick/ instead)

Usage (from the app-a-autocomplete folder):
    python plot_results.py            # reads results/, writes the PNGs and the PDF
    python plot_results.py RESULTS_DIR

Needs matplotlib:  python -m pip install matplotlib
"""

import csv
import statistics
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")                      # write files, never open a window
import matplotlib.pyplot as plt            # noqa: E402
from matplotlib.backends.backend_pdf import PdfPages  # noqa: E402

RESULTS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("results")
PLOTS = RESULTS / "plots"
# The real report goes to docs/; a quick test run's report stays next to its results.
PDF = (Path("docs") if RESULTS.name == "results" else RESULTS) / "Stage4_Benchmark_Results.pdf"

SOLUTIONS = {"hash": "Solution 1: hash table (chaining)", "tree": "Solution 2: Red-Black tree (TreeMap)"}
COLORS = {"hash": "#1f5fa8", "tree": "#c4581c"}
MARKERS = {"hash": "o", "tree": "s"}

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "Times", "Liberation Serif", "DejaVu Serif"],
    "font.size": 10,
    "axes.titlesize": 11,
    "axes.grid": True,
    "grid.alpha": 0.3,
    "figure.dpi": 150,
})


# ----------------------------------------------------------------------
# Reading the CSV files
# ----------------------------------------------------------------------

def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


summary = []
for s in SOLUTIONS:
    summary += read_csv(RESULTS / f"{s}_summary.csv")
for row in summary:
    row["n"] = int(row["n"])
    for key in ("median_ns_per_op", "min_ns_per_op", "max_ns_per_op", "median_ms_per_batch"):
        row[key] = float(row[key])
    row["avg_matches"] = float(row["avg_matches"]) if row["avg_matches"] else None

memory = []
for s in SOLUTIONS:
    memory += read_csv(RESULTS / f"{s}_memory.csv")

counters = read_csv(RESULTS / "counters.csv") if (RESULTS / "counters.csv").exists() else []
machine = {r["key"]: r["value"] for r in read_csv(RESULTS / "hash_machine.csv")}

FULL = max(r["n"] for r in summary)
# A quick run (--quick) has no full-size-only experiments; skip what needs them.
HAS_FULL_ONLY = any(r["workload"] == "complete_letter" for r in summary)


def series(solution, workload, param):
    """Rows of one curve, sorted by n."""
    rows = [r for r in summary
            if r["solution"] == solution and r["workload"] == workload and r["param"] == param]
    return sorted(rows, key=lambda r: r["n"])


def point(solution, workload, param, n=None):
    rows = [r for r in series(solution, workload, param) if n is None or r["n"] == n]
    return rows[-1] if rows else None


def plot_curve(ax, solution, workload, param, label, scale=1.0, linestyle="-"):
    """One curve: median as the line, min-max as error bars."""
    rows = series(solution, workload, param)
    if not rows:
        return
    x = [r["n"] for r in rows]
    y = [r["median_ns_per_op"] * scale for r in rows]
    low = [(r["median_ns_per_op"] - r["min_ns_per_op"]) * scale for r in rows]
    high = [(r["max_ns_per_op"] - r["median_ns_per_op"]) * scale for r in rows]
    ax.errorbar(x, y, yerr=[low, high], label=label, color=COLORS[solution], marker=MARKERS[solution],
                linestyle=linestyle, capsize=3, markersize=4, linewidth=1.3)


def target_line(ax, y, text):
    ax.axhline(y, color="#2a7a2a", linestyle=":", linewidth=1.2)
    ax.annotate(text, xy=(1.0, y), xycoords=("axes fraction", "data"), xytext=(-4, 3),
                textcoords="offset points", ha="right", va="bottom", fontsize=8, color="#2a7a2a")


def log_axes(ax, ylabel, title):
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel("dictionary size n (words, log scale)")
    ax.set_ylabel(ylabel)
    ax.set_title(title)


def save(fig, name):
    PLOTS.mkdir(parents=True, exist_ok=True)
    path = PLOTS / f"{name}.png"
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)
    images.append(path)


images = []

# ----------------------------------------------------------------------
# 1. contains (spell-checking)
# ----------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(7, 4.2))
for s in SOLUTIONS:
    plot_curve(ax, s, "contains", "real_text", f"{SOLUTIONS[s]}, real text")
    plot_curve(ax, s, "contains", "misspelled", f"{SOLUTIONS[s]}, misspellings", linestyle="--")
target_line(ax, 1000, "A-N1 target: 1 \u00b5s")
log_axes(ax, "time per contains (ns, log scale)", "Spell-checking: average time of contains(word)")
ax.legend(fontsize=8)
save(fig, "1_contains")

# ----------------------------------------------------------------------
# 2. complete for each prefix length
# ----------------------------------------------------------------------
fig, axes = plt.subplots(2, 2, figsize=(8, 6), sharex=True)
for ax, length in zip(axes.flat, (1, 2, 3, 5)):
    for s in SOLUTIONS:
        plot_curve(ax, s, "complete", f"len{length}", SOLUTIONS[s], scale=1e-6)
    if length >= 2:
        target_line(ax, 1.0, "A-N2: 1 ms")
    log_axes(ax, "time per query (ms, log scale)", f"complete(prefix, 10), prefix length {length}")
axes[0][0].legend(fontsize=7)
fig.suptitle("Autocomplete: average time of complete(prefix, 10)")
fig.tight_layout()
save(fig, "2_complete_by_size")

# ----------------------------------------------------------------------
# 3. complete at full size vs prefix length, with the number of matches
# ----------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(7, 4.2))
lengths = [1, 2, 3, 5]
width = 0.38
for i, s in enumerate(SOLUTIONS):
    ys = [point(s, "complete", f"len{L}", FULL)["median_ns_per_op"] / 1e6 for L in lengths]
    xs = [j + (i - 0.5) * width for j in range(len(lengths))]
    ax.bar(xs, ys, width, label=SOLUTIONS[s], color=COLORS[s])
matches = [point("tree", "complete", f"len{L}", FULL)["avg_matches"] for L in lengths]
ax.set_xticks(range(len(lengths)))
ax.set_xticklabels([f"length {L}\n(avg {m:,.0f} matches)" for L, m in zip(lengths, matches)])
ax.set_yscale("log")
ax.set_ylabel("time per query (ms, log scale)")
ax.set_title(f"complete(prefix, 10) on the full dictionary (n = {FULL:,}) by prefix length")
target_line(ax, 1.0, "A-N2: 1 ms")
ax.legend(fontsize=8)
save(fig, "3_complete_by_prefix_length")

# ----------------------------------------------------------------------
# 4. Every 1-letter prefix on the full dictionary (A-N3)
# ----------------------------------------------------------------------
letters = [chr(c) for c in range(ord("a"), ord("z") + 1)]
fig, ax = plt.subplots(figsize=(8, 4))
for i, s in enumerate(SOLUTIONS if HAS_FULL_ONLY else []):
    ys = [point(s, "complete_letter", c, FULL)["median_ns_per_op"] / 1e6 for c in letters]
    xs = [j + (i - 0.5) * width for j in range(len(letters))]
    ax.bar(xs, ys, width, label=SOLUTIONS[s], color=COLORS[s])
ax.set_xticks(range(len(letters)))
ax.set_xticklabels(letters)
ax.set_yscale("log")
ax.set_xlabel("1-letter prefix")
ax.set_ylabel("time of one query (ms, log scale)")
ax.set_title(f"complete(letter, 10) for every 1-letter prefix (n = {FULL:,})")
target_line(ax, 50, "A-N3: 50 ms (worst letter)")
if HAS_FULL_ONLY:
    ax.legend(fontsize=8)
    save(fig, "4_complete_one_letter")
else:
    plt.close(fig)

# ----------------------------------------------------------------------
# 5. Build time
# ----------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(7, 4.2))
for s in SOLUTIONS:
    rows = series(s, "build", "frequency_order")
    ax.plot([r["n"] for r in rows], [r["median_ms_per_batch"] for r in rows], marker=MARKERS[s],
            color=COLORS[s], label=f"{SOLUTIONS[s]}, frequency order")
    rows = series(s, "build", "sorted_order")
    ax.plot([r["n"] for r in rows], [r["median_ms_per_batch"] for r in rows], marker=MARKERS[s],
            color=COLORS[s], linestyle="--", label=f"{SOLUTIONS[s]}, alphabetical order (stress)")
    f = point(s, "build_from_file", "count_1w.txt")
    if f:
        ax.plot([f["n"]], [f["median_ms_per_batch"]], marker="*", markersize=12, color=COLORS[s],
                linestyle="none", label=f"{SOLUTIONS[s]}, read file + build (A-N4)")
target_line(ax, 2000, "A-N4 target: 2 s")
log_axes(ax, "total build time (ms, log scale)", "Build time: add() of the n most frequent words")
ax.legend(fontsize=7)
save(fig, "5_build_time")

# ----------------------------------------------------------------------
# 6. Mixed workload with updates
# ----------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(7, 4.2))
for s in SOLUTIONS:
    plot_curve(ax, s, "mixed", "10pct_updates", SOLUTIONS[s])
log_axes(ax, "time per operation (ns, log scale)",
         "Updates: 90% contains, 5% add, 5% remove")
ax.legend(fontsize=8)
save(fig, "6_mixed_updates")

# ----------------------------------------------------------------------
# 7. Memory per word: measured vs estimated
# ----------------------------------------------------------------------
def measured_bytes(solution):
    by_n = {}
    for r in memory:
        if r["solution"] == solution:
            by_n.setdefault(int(r["n"]), []).append(float(r["bytes_per_word"]))
    return {n: statistics.median(v) for n, v in sorted(by_n.items())}


capacity = {int(r["n"]): float(r["value"]) for r in counters
            if r["solution"] == "hash" and r["metric"] == "capacity"}
# Estimates (64-bit JVM, compressed references): see the PDF text.
estimate = {
    "hash": {n: 32 + 4 * capacity[n] / n for n in capacity},     # Node 32 B + its share of the array
    "tree": {n: 40 + 24 for n in capacity},                      # Entry 40 B + boxed Long 24 B
}
fig, ax = plt.subplots(figsize=(7, 4.2))
for s in SOLUTIONS:
    m = measured_bytes(s)
    ax.plot(list(m), list(m.values()), marker=MARKERS[s], color=COLORS[s], label=f"{SOLUTIONS[s]}, measured")
    if estimate[s]:
        ax.plot(list(estimate[s]), list(estimate[s].values()), color=COLORS[s], linestyle="--",
                label=f"{SOLUTIONS[s]}, estimated from node layout")
target_line(ax, 200, "A-N5 target: 200 bytes/word")
ax.set_xscale("log")
ax.set_ylim(0, 220)
ax.set_xlabel("dictionary size n (words, log scale)")
ax.set_ylabel("bytes per word")
ax.set_title("Memory of the structure per word (word Strings not included)")
ax.legend(fontsize=7)
save(fig, "7_memory")

# ----------------------------------------------------------------------
# 8. Counters: why the times look the way they do
# ----------------------------------------------------------------------
if counters:
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(9, 3.8))

    def counter_series(solution, metric):
        rows = sorted((int(r["n"]), float(r["value"])) for r in counters
                      if r["solution"] == solution and r["metric"] == metric)
        return [n for n, _ in rows], [v for _, v in rows]

    for metric, label, style in (("probes_per_lookup_real_text", "real text", "-"),
                                 ("probes_per_lookup_misspelled", "misspellings", "--")):
        ax1.plot(*counter_series("hash", metric), marker="o", color=COLORS["hash"], linestyle=style, label=label)
    ax1.set_xscale("log")
    ax1.set_ylim(0, 2)
    ax1.set_xlabel("dictionary size n (log scale)")
    ax1.set_ylabel("nodes compared per lookup")
    ax1.set_title("Hash table: probes per contains()")
    ax1.legend(fontsize=8)

    for s in SOLUTIONS:
        ax2.plot(*counter_series(s, "words_scanned_per_complete_len2"), marker=MARKERS[s],
                 color=COLORS[s], label=SOLUTIONS[s])
    ax2.set_xscale("log")
    ax2.set_yscale("log")
    ax2.set_xlabel("dictionary size n (log scale)")
    ax2.set_ylabel("words examined per query (log scale)")
    ax2.set_title("Words examined by complete(), prefix length 2")
    ax2.legend(fontsize=7)
    fig.tight_layout()
    save(fig, "8_counters")

# ----------------------------------------------------------------------
# Targets table (full dictionary)
# ----------------------------------------------------------------------
def fmt_ns(ns):
    if ns >= 1e6:
        return f"{ns / 1e6:,.2f} ms"
    if ns >= 1e3:
        return f"{ns / 1e3:,.2f} \u00b5s"
    return f"{ns:,.0f} ns"


def targets(s):
    rows = []
    real = point(s, "contains", "real_text", FULL)["median_ns_per_op"]
    rows.append(("A-N1", "contains (real text)", fmt_ns(real), "1 \u00b5s", real <= 1000))
    miss = point(s, "contains", "misspelled", FULL)["median_ns_per_op"]
    rows.append(("A-N1", "contains (misspellings)", fmt_ns(miss), "1 \u00b5s", miss <= 1000))
    worst2 = max(point(s, "complete", f"len{L}", FULL)["median_ns_per_op"] for L in (2, 3, 5))
    rows.append(("A-N2", "complete, prefix length 2+ (worst length)", fmt_ns(worst2), "1 ms", worst2 <= 1e6))
    if HAS_FULL_ONLY:
        letter = max(letters, key=lambda c: point(s, "complete_letter", c, FULL)["median_ns_per_op"])
        worst1 = point(s, "complete_letter", letter, FULL)["median_ns_per_op"]
        rows.append(("A-N3", f"complete, worst 1-letter prefix ('{letter}')", fmt_ns(worst1), "50 ms",
                     worst1 <= 50e6))
        build = point(s, "build_from_file", "count_1w.txt")["median_ms_per_batch"]
        rows.append(("A-N4", "read file + build full dictionary", f"{build:,.0f} ms", "2 s", build <= 2000))
    mem = measured_bytes(s).get(FULL)
    rows.append(("A-N5", "memory per word (structure only)", f"{mem:,.0f} bytes", "200 bytes", mem <= 200))
    small = point(s, "contains", "real_text", 10_000)["median_ns_per_op"]
    rows.append(("A-N6", "contains: full list vs 10^4 words", f"{real / small:.2f}x", "3x", real / small <= 3))
    return rows


# ----------------------------------------------------------------------
# PDF: page 1 = machine + method + targets, then the plots
# ----------------------------------------------------------------------
def ram_text():
    """Windows reports 32 GB of RAM as 34.4e9 bytes; show both units so it matches the spec sheet."""
    try:
        gb = float(machine.get("ram_total_gb", ""))
        return f"{gb:.1f} GB ({gb * 1e9 / 2**30:.0f} GiB)"
    except ValueError:
        return machine.get("ram_total_gb", "unknown")


def text_page():
    fig = plt.figure(figsize=(8.5, 11))
    y = 0.95

    def line(text, size=10, bold=False, dy=0.022, x=0.08):
        nonlocal y
        fig.text(x, y, text, fontsize=size, fontweight="bold" if bold else "normal", va="top")
        y -= dy

    line("COMP 272 Term Project: Stage 4 Benchmark Results", 15, True, 0.03)
    line("Kofi Nyarku", 11)
    line("Application A: Autocomplete and Spell-Checking", 11)
    line(machine.get("date", "")[:10], 11, dy=0.035)

    line("Machine", 12, True, 0.026)
    rows = [("CPU", machine.get("cpu", "")),
            ("Logical cores", machine.get("logical_cores", "")),
            ("RAM", ram_text()),
            ("Operating system", machine.get("os", "")),
            ("Java", machine.get("java_version", "") + "  (" + machine.get("java_vm", "") + ")"),
            ("Heap (-Xmx)", machine.get("max_heap_mb", "") + " MB"),
            ("JVM options", " ".join(a for a in machine.get("jvm_args", "").split()
                                     if a.startswith(("-Xm", "-Dautocomplete"))))]
    for k, v in rows:
        fig.text(0.10, y, k, fontsize=10, va="top")
        fig.text(0.32, y, v, fontsize=10, va="top")
        y -= 0.021
    y -= 0.012

    line("Method", 12, True, 0.026)
    for t in (f"Each solution runs in its own JVM. {machine.get('warmup_runs')} warm-up runs, then "
              f"{machine.get('measured_runs')} measured runs per data point;",
              "I report the median, with min and max as error bars. Batches shorter than 20 ms are repeated.",
              "A checksum of every result is printed, so the JIT cannot remove the work. Counters are off",
              "during timing (-Dautocomplete.counters=false) and measured in a separate run.",
              f"Inputs are fixed: the n most frequent words, all {int(machine.get('real_text_words', 0)):,} words "
              f"of {machine.get('books', '?')} Gutenberg books,",
              "100,000 misspellings and 100 prefixes per length generated with seed 272."):
        line(t, 10, dy=0.019)
    y -= 0.014

    line(f"Targets on the full dictionary (n = {FULL:,})", 12, True, 0.03)
    for s in SOLUTIONS:
        line(SOLUTIONS[s], 10.5, True, 0.022)
        for tid, what, value, target, ok in targets(s):
            fig.text(0.10, y, tid, fontsize=9.5, va="top")
            fig.text(0.18, y, what, fontsize=9.5, va="top")
            fig.text(0.62, y, value, fontsize=9.5, va="top")
            fig.text(0.76, y, "target " + target, fontsize=9.5, va="top")
            fig.text(0.90, y, "PASS" if ok else "FAIL", fontsize=9.5, va="top",
                     color="#2a7a2a" if ok else "#b00020", fontweight="bold")
            y -= 0.019
        y -= 0.01
    return fig


PDF.parent.mkdir(parents=True, exist_ok=True)
with PdfPages(PDF) as pdf:
    first = text_page()
    pdf.savefig(first)
    plt.close(first)
    # two plots per page
    for i in range(0, len(images), 2):
        page = plt.figure(figsize=(8.5, 11))
        for j, img in enumerate(images[i:i + 2]):
            ax = page.add_axes([0.06, 0.52 - j * 0.48, 0.88, 0.44])
            ax.imshow(plt.imread(img))
            ax.axis("off")
        pdf.savefig(page)
        plt.close(page)

print(f"Wrote {len(images)} plots to {PLOTS} and the report to {PDF}")
for s in SOLUTIONS:
    print(SOLUTIONS[s])
    for tid, what, value, target, ok in targets(s):
        print(f"  {tid}  {what:45s} {value:>12s}  target {target:10s} {'PASS' if ok else 'FAIL'}")