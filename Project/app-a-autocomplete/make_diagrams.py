"""Draws the two design diagrams for my report (Section 4).

    python make_diagrams.py

writes docs/figures/hash_table.png and docs/figures/red_black_tree.png.

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
from matplotlib.patches import Circle, FancyArrowPatch, Rectangle

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


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    chains = draw_hash_table()
    draw_red_black_tree()
    for i, chain in enumerate(chains):
        if chain:
            print(f"  bucket {i:2d}: {' -> '.join(chain)}")
    print(f"Wrote {OUT / 'hash_table.png'} and {OUT / 'red_black_tree.png'}")
