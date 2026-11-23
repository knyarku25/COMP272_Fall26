"""Draws the two design diagrams for my report (Section 4).

    python make_diagrams.py

writes four figures to docs/figures/:
    hash_table.png       Solution 1, and why complete() must visit every bucket
    red_black_tree.png   Solution 2, and how complete() visits only the matching range
    top_k.png            the ranking step both solutions share (my TopK class)
    project_structure.png  how the code fits together

Both pictures use a few real words. The hash table puts each word in the same
bucket my Solution1 would (Java's String.hashCode, mixed with h ^ (h >>> 16)),
so the picture matches the code. The tree is a small valid Red-Black tree that
I drew by hand to show how complete("th") works; it is not the tree TreeMap
would build for these words.
"""
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, FancyArrowPatch, FancyBboxPatch, Rectangle

plt.rcParams["font.family"] = "serif"
plt.rcParams["font.serif"] = ["Times New Roman", "Liberation Serif", "DejaVu Serif"]

OUT = Path(__file__).resolve().parent / "docs" / "figures"
MATCH = "#cfe3f5"      # light blue: a word that starts with the prefix
VISITED = "#f2f2f2"    # light grey: looked at, but no match
PREFIX = "th"


# ----- hash table -------------------------------------------------------------------

def java_hash(word):
    """String.hashCode() in Java: h = 31*h + c, kept to 32 bits."""
    h = 0
    for c in word:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return h


def bucket_index(word, buckets):
    """The same bucket my Solution1.bucketIndex() picks."""
    h = java_hash(word)
    h ^= h >> 16
    return h & (buckets - 1)


def draw_hash_table():
    buckets = 16
    words = ["the", "of", "and", "to", "in", "that", "is", "for",
             "this", "they", "then", "on"]              # 12 words / 16 buckets = load 0.75
    chains = [[] for _ in range(buckets)]
    for w in words:
        chains[bucket_index(w, buckets)].insert(0, w)   # Solution1 adds at the front of the chain

    fig, ax = plt.subplots(figsize=(7.0, 6.2))
    row_h, box_w, node_w, gap = 0.34, 0.55, 0.85, 0.35
    top = buckets * row_h
    for i, chain in enumerate(chains):
        y = top - (i + 1) * row_h
        ax.add_patch(Rectangle((0, y), box_w, row_h, fill=True, facecolor=VISITED, edgecolor="black"))
        ax.text(-0.12, y + row_h / 2, str(i), ha="right", va="center", fontsize=9)
        if not chain:
            ax.text(box_w / 2, y + row_h / 2, "null", ha="center", va="center", fontsize=8, color="grey")
            continue
        ax.plot(box_w / 2, y + row_h / 2, "o", color="black", markersize=3)
        x_prev = box_w / 2
        for j, w in enumerate(chain):
            x = box_w + gap + j * (node_w + gap)
            color = MATCH if w.startswith(PREFIX) else VISITED
            ax.add_patch(Rectangle((x, y + 0.04), node_w, row_h - 0.08,
                                   facecolor=color, edgecolor="black"))
            ax.text(x + node_w / 2, y + row_h / 2, w, ha="center", va="center", fontsize=10)
            ax.add_patch(FancyArrowPatch((x_prev, y + row_h / 2), (x, y + row_h / 2),
                                         arrowstyle="-|>", mutation_scale=9, color="black", lw=0.8))
            x_prev = x + node_w

    ax.text(box_w / 2, top + 0.15, "buckets", ha="center", va="bottom", fontsize=10)
    ax.text(box_w + gap, top + 0.15, "chains (each node: word, count, hash, next)",
            ha="left", va="bottom", fontsize=10)
    ax.add_patch(Rectangle((0, -0.75), 0.3, 0.22, facecolor=MATCH, edgecolor="black"))
    ax.text(0.4, -0.64, f'starts with "{PREFIX}"', va="center", fontsize=9)
    ax.add_patch(Rectangle((2.1, -0.75), 0.3, 0.22, facecolor=VISITED, edgecolor="black"))
    ax.text(2.5, -0.64, "visited, no match", va="center", fontsize=9)
    ax.text(0, -1.15,
            f'contains("then") hashes once and checks one short chain.\n'
            f'complete("{PREFIX}", k) has no order to use, so it must visit all {buckets} buckets '
            f'and all {len(words)} words.',
            fontsize=9.5, va="top")

    ax.set_xlim(-0.5, 5.6)
    ax.set_ylim(-1.75, top + 0.5)
    ax.axis("off")
    ax.set_title(f"Solution 1: hash table with separate chaining ({buckets} buckets, load factor 0.75)",
                 fontsize=11)
    fig.savefig(OUT / "hash_table.png", dpi=200, bbox_inches="tight")
    plt.close(fig)
    return chains


# ----- Red-Black tree -----------------------------------------------------------------

def draw_red_black_tree():
    # 15 words in alphabetical order make a perfect tree of height 4. All nodes black,
    # with the bottom row red, is a valid Red-Black tree: no red node has a red child,
    # and every path from the root to a null has the same number of black nodes (3).
    words = ["a", "and", "for", "in", "is", "it", "of", "on",
             "that", "the", "their", "there", "they", "this", "to"]

    positions = {}            # index in words -> (x, y)
    def place(lo, hi, depth):
        if lo > hi:
            return
        mid = (lo + hi) // 2
        positions[mid] = (mid, -depth)
        place(lo, mid - 1, depth + 1)
        place(mid + 1, hi, depth + 1)
    place(0, len(words) - 1, 0)

    # complete("th"): walk down to the first word >= "th" (that), then go in order
    # through the matches, and stop at the first word that does not match (to).
    path = [7, 11, 9, 8]                                   # on -> there -> the -> that
    first = 8
    stop = next(i for i in range(first, len(words)) if not words[i].startswith(PREFIX))

    fig, ax = plt.subplots(figsize=(8.6, 4.6))
    for i, (x, y) in positions.items():                    # edges first, so nodes cover them
        for child in (i - 2 ** (2 + y), i + 2 ** (2 + y)) if y > -3 else ():
            if child in positions:
                cx, cy = positions[child]
                on_path = i in path and child in path
                ax.plot([x, cx], [y, cy], color="black", lw=2.4 if on_path else 0.9, zorder=1)

    r = 0.42
    for i, (x, y) in positions.items():
        red = y == -3
        if words[i].startswith(PREFIX):
            fill = MATCH
        elif i == stop or i in path:
            fill = VISITED
        else:
            fill = "white"
        ax.add_patch(Circle((x, y), r, facecolor=fill, edgecolor="#c0392b" if red else "black",
                            lw=2.4 if red else 1.6, zorder=2))
        ax.text(x, y, words[i], ha="center", va="center", fontsize=9.5, zorder=3,
                color="#c0392b" if red else "black")

    sx, sy = positions[stop]
    ax.annotate("first word that does not\nstart with \"th\": stop",
                xy=(sx, sy - r), xytext=(sx - 0.6, sy - 1.25),
                ha="center", fontsize=9, arrowprops=dict(arrowstyle="->", lw=0.8))
    fx, fy = positions[first]
    ax.annotate('tailMap("th") starts here\n(first word >= "th")',
                xy=(fx, fy - r), xytext=(fx - 2.2, fy - 1.25),
                ha="center", fontsize=9, arrowprops=dict(arrowstyle="->", lw=0.8))

    ax.add_patch(Circle((0.0, 0.0), 0.16, facecolor="white", edgecolor="black", lw=1.6))
    ax.text(0.3, 0.0, "black node", va="center", fontsize=9)
    ax.add_patch(Circle((0.0, -0.45), 0.16, facecolor="white", edgecolor="#c0392b", lw=2.4))
    ax.text(0.3, -0.45, "red node", va="center", fontsize=9)
    ax.add_patch(Circle((0.0, -0.9), 0.16, facecolor=MATCH, edgecolor="black", lw=1.2))
    ax.text(0.3, -0.9, f'starts with "{PREFIX}"', va="center", fontsize=9)
    ax.plot([-0.15, 0.2], [-1.35, -1.35], color="black", lw=2.4)
    ax.text(0.3, -1.35, "search path", va="center", fontsize=9)

    touched = set(path) | set(range(first, stop + 1))
    ax.text(-0.3, -4.6,
            f'complete("{PREFIX}", k) walks down {len(path)} nodes (O(log n)), then visits only the '
            f'{stop - first} matching words and one more.\nThe other {len(words) - len(touched)} '
            f'words are never touched. contains() follows one path from the root, O(log n).',
            fontsize=9.5, va="top")
    ax.set_xlim(-0.6, len(words) - 0.4)
    ax.set_ylim(-5.3, 0.7)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.set_title("Solution 2: Red-Black tree (java.util.TreeMap), words in alphabetical order",
                 fontsize=11)
    fig.savefig(OUT / "red_black_tree.png", dpi=200, bbox_inches="tight")
    plt.close(fig)


# ----- TopK: the ranking step ---------------------------------------------------------

def draw_top_k():
    """Replays my TopK class on the 6 words of the tree figure, with k = 3.

    The counts are the real ones from count_1w.txt. The words arrive in the order the
    tree finds them (alphabetical). The hash table finds them in bucket order instead,
    but TopK ends with the same 3 words either way.
    """
    k = 3
    offered = [("that", 3_400_031_103), ("the", 23_135_851_162), ("their", 782_849_411),
               ("there", 701_170_205), ("they", 883_223_816), ("this", 3_228_469_771)]

    def billions(c):
        return f"{c / 1e9:.2f} B"

    rows = []
    heap = []                                          # (count, word); worst = smallest count
    for word, count in offered:
        if len(heap) < k:
            heap.append((count, word))
            action = "heap not full: add"
        else:
            worst = min(heap)
            if count > worst[0]:
                heap.remove(worst)
                heap.append((count, word))
                action = f"beats {worst[1]}: replace it"
            else:
                action = f"loses to {worst[1]}: skip"
        kept = ", ".join(w for c, w in sorted(heap))
        rows.append((word, billions(count), action, kept))
    result = [w for c, w in sorted(heap, reverse=True)]

    fig, ax = plt.subplots(figsize=(7.6, 3.6))
    headers = ("word offered", "count", "what TopK does", "heap after (worst first)")
    xs = (0.0, 1.35, 2.55, 5.0)
    y = 0
    for x, h in zip(xs, headers):
        ax.text(x, y, h, fontsize=10, fontweight="bold", va="center")
    ax.plot([0, 7.4], [y - 0.35, y - 0.35], color="black", lw=0.8)
    for word, count, action, kept in rows:
        y -= 0.62
        skipped = "skip" in action
        for x, value in zip(xs, (word, count, action, kept)):
            ax.text(x, y, value, fontsize=10, va="center", color="grey" if skipped else "black")
    y -= 0.55
    ax.plot([0, 7.4], [y + 0.2, y + 0.2], color="black", lw=0.8)
    ax.text(0, y - 0.25, f"result(): sort the {k} kept words, best first  ->  " + ", ".join(result),
            fontsize=10, va="center")
    ax.text(0, y - 0.85,
            f"Each offer costs O(log k): compare with the worst kept word, and only touch the heap if it wins.\n"
            f"For m matching words: O(m log k) time and O(k) memory, the same code in both solutions.",
            fontsize=9.5, va="top")
    ax.set_xlim(-0.1, 7.5)
    ax.set_ylim(y - 1.9, 0.5)
    ax.axis("off")
    ax.set_title(f'Ranking the matches of "{PREFIX}" with TopK (a min-heap of size k = {k})', fontsize=11)
    fig.savefig(OUT / "top_k.png", dpi=200, bbox_inches="tight")
    plt.close(fig)
    return result


# ----- project structure ----------------------------------------------------------------

def draw_project_structure():
    fig, ax = plt.subplots(figsize=(8.2, 5.4))

    def box(x, y, w, h, title, note="", fill="white", dashed=False):
        ax.add_patch(FancyBboxPatch((x - w / 2, y - h / 2), w, h, boxstyle="round,pad=0.02,rounding_size=0.08",
                                    facecolor=fill, edgecolor="black", lw=1.2,
                                    linestyle="--" if dashed else "-"))
        ax.text(x, y + (0.13 if note else 0), title, ha="center", va="center", fontsize=10, fontweight="bold")
        if note:
            ax.text(x, y - 0.17, note, ha="center", va="center", fontsize=8.5)

    def arrow(x1, y1, x2, y2, label="", hollow=False):
        ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2),
                                     arrowstyle="-|>",
                                     mutation_scale=12, lw=1.0, color="black",
                                     linestyle="--" if hollow else "-"))
        if label:
            ax.text((x1 + x2) / 2 + 0.08, (y1 + y2) / 2, label, fontsize=8.5, va="center")

    # data and reading
    box(1.6, 5.0, 2.6, 0.62, "count_1w.txt", "333,333 words with counts", fill=VISITED, dashed=True)
    box(5.4, 5.0, 3.0, 0.62, "12 Gutenberg books", "1,986,147 words of real text", fill=VISITED, dashed=True)
    box(3.5, 3.85, 2.6, 0.62, "DatasetReader", "reads and tokenizes")
    arrow(1.6, 4.68, 3.0, 4.17)
    arrow(5.4, 4.68, 4.0, 4.17)

    # the three programs that use the interface
    box(1.0, 2.65, 1.8, 0.62, "JUnit tests", "DictionaryTest: both")
    box(3.5, 2.65, 1.8, 0.62, "Benchmark", "bench package")
    box(6.0, 2.65, 1.8, 0.62, "Demo", "live demo")
    for x in (1.0, 3.5, 6.0):
        arrow(3.5, 3.53, x, 2.97)

    # the interface and the two solutions
    box(3.5, 1.45, 3.0, 0.62, "Dictionary (interface)", "add, remove, contains, frequency, complete, size",
        fill=MATCH)
    for x in (1.0, 3.5, 6.0):
        arrow(x, 2.33, 3.5 + (x - 3.5) * 0.45, 1.77)
    ax.text(3.6, 2.05, "use only the interface", fontsize=8.5, va="center")
    box(1.6, 0.1, 2.6, 0.62, "Solution1", "my hash table (chaining)")
    box(5.4, 0.1, 2.6, 0.62, "Solution2", "adapter around TreeMap")
    arrow(1.6, 0.42, 2.8, 1.13, hollow=True)
    arrow(5.4, 0.42, 4.2, 1.13, hollow=True)
    ax.text(1.75, 0.8, "implements", fontsize=8.5, ha="right", va="center")
    ax.text(5.25, 0.8, "implements", fontsize=8.5, ha="left", va="center")
    ax.text(3.5, -0.45, "both call", fontsize=8.5, ha="center", va="center")
    box(3.5, -1.05, 2.0, 0.62, "TopK", "shared min-heap")
    arrow(1.6, -0.22, 2.8, -0.85)
    arrow(5.4, -0.22, 4.2, -0.85)

    ax.text(-0.3, -1.75,
            "Everything above the interface only knows Dictionary, so the tests, the benchmark and the demo run\n"
            "unchanged on both solutions, and both rank completions with the same TopK class: a fair comparison.",
            fontsize=9.5, va="top")
    ax.set_xlim(-0.4, 7.4)
    ax.set_ylim(-2.4, 5.5)
    ax.axis("off")
    ax.set_title("How my project fits together", fontsize=11)
    fig.savefig(OUT / "project_structure.png", dpi=200, bbox_inches="tight")
    plt.close(fig)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    chains = draw_hash_table()
    draw_red_black_tree()
    top = draw_top_k()
    draw_project_structure()
    for i, chain in enumerate(chains):
        if chain:
            print(f"  bucket {i:2d}: {' -> '.join(chain)}")
    print(f'  TopK result for "{PREFIX}", k = 3: {", ".join(top)}')
    print(f"Wrote 4 figures to {OUT}")
