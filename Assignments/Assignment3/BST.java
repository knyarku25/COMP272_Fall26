/**
 * A Binary Search Tree of distinct integers that inherits from BinaryTree.
 *
 * Invariant: for every node, every value in its left subtree is smaller than
 * the node's value, and every value in its right subtree is larger.
 *
 * EXERCISE 2.
 *
 * Name: Kofi Minta Nyarku
 * Student ID: 00001715797
 */
public class BST extends BinaryTree {

    /** Creates an empty BST. */
    public BST() {
        super();
    }

    /**
     * Part B: Inserts {@code value} so that the BST invariant is preserved.
     * Starting at the root, move left if value is smaller than the current
     * element and right if it is larger, until an empty position is reached;
     * the new node is attached there. If value is already present, the tree
     * is left unchanged.
     */
    @Override
    public void add(int value) {
        if (root == null) {
            root = new Node(value);
            return;
        }

        Node current = root;
        while (true) {
            int element = current.getElement();

            if (value == element) {
                return; // distinct values only: duplicate is ignored
            }

            if (value < element) {
                if (current.getLeft() == null) {
                    current.setLeft(new Node(value));
                    return;
                }
                current = current.getLeft();
            }
            else {
                if (current.getRight() == null) {
                    current.setRight(new Node(value));
                    return;
                }
                current = current.getRight();
            }
        }
    }

    /**
     * Part C: Returns the node containing {@code value}, or null if it is not
     * in the tree. Uses the BST invariant: at each node, the search continues
     * into only ONE subtree (left if value is smaller, right if larger).
     */
    @Override
    public Node search(int value) {
        Node current = root;
        while (current != null) {
            int element = current.getElement();
            if (value == element) {
                return current;
            }
            current = (value < element) ? current.getLeft() : current.getRight();
        }
        return null;
    }

    /*
     * Exercise 2, Part D — Algorithmic analysis
     * (n = number of nodes, h = height of the tree)
     *
     * Q1. Running time of BST.search
     *
     * In terms of h: O(h). At each node I compare once and then go either
     * left or right, never both, because the BST rule tells me which side
     * the value has to be on. So every step goes down one level. The
     * longest path from the root to a leaf has h + 1 nodes, so the loop
     * runs at most h + 1 times, which is O(h).
     *
     * Balanced tree: O(log n). In a balanced tree each level holds about
     * twice as many nodes as the level above it, so the height only grows
     * like log n. That makes O(h) = O(log n).
     *
     * Worst case: O(n). If the tree turns into a straight line (every node
     * has only one child), the height is n - 1. Searching for the bottom
     * value, or a value that isn't there, goes through every node, so it's
     * O(n).
     *
     * Q2. Worst-case insertion order for 1..7
     *
     * Adding them in increasing order: 1, 2, 3, 4, 5, 6, 7. Each new number
     * is bigger than everything already in the tree, so it always goes to
     * the right. The tree ends up as one long chain with height 6.
     *
     *   1
     *    \
     *     2
     *      \
     *       3
     *        \
     *         4
     *          \
     *           5
     *            \
     *             6
     *              \
     *               7
     *
     * Searching for 7 has to visit all 7 nodes. (Adding them in decreasing
     * order 7..1 gives the same kind of chain going left.)
     *
     * Q3. Comparison with Exercise 1, Part C
     *
     * The search in BinaryTree is O(n) in the worst case no matter what the
     * tree looks like, because it can't skip anything. The BST search is
     * O(h), so on a balanced tree it's only O(log n). For example, with a
     * million nodes that's around 20 comparisons instead of possibly a
     * million. The inherited search still gives the right answer, but it
     * ignores the ordering, which is the whole point of using a BST. That's
     * why it's worth overriding. The two are only equally slow when the BST
     * becomes a chain.
     *
     * Q4. What happens in this code?
     *
     *   BinaryTree t = new BST();
     *   t.add(50);
     *   t.add(70);
     *   t.add(30);
     *
     * The variable t is declared as a BinaryTree, but the object it points
     * to is actually a BST. In Java, when a method is overridden, the
     * version that runs depends on the actual object, not on the type of
     * the variable (dynamic dispatch). So all three calls run BST's add,
     * not BinaryTree's add.
     *
     *   add(50): the tree is empty, so 50 becomes the root.
     *   add(70): 70 > 50, so it goes to the right of 50.
     *   add(30): 30 < 50, so it goes to the left of 50.
     *
     *       50
     *      /  \
     *    30    70
     *
     * If BinaryTree's add had run instead, 70 would have gone on the left
     * and 30 on the right, which would break the BST rule.
     */
}