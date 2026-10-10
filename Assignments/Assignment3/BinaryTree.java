import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A binary tree of integers (the tree from class, now storing int values).
 *
 * EXERCISE 1 — Complete the two methods marked TODO: add(int) and search(int).
 * Do NOT change the provided code, method names, or signatures.
 * You may add private helper methods.
 *
 * Name: Kofi Minta Nyarku
 * Student ID: 00001715797
 */
public class BinaryTree {

    public static class Node {
        private int element;
        private Node left;
        private Node right;

        public Node(int element) {
            this.element = element;
        }

        public int getElement() { return element; }
        public Node getLeft() { return left; }
        public Node getRight() { return right; }
        public void setLeft(Node left) { this.left = left; }
        public void setRight(Node right) { this.right = right; }
    }

    // protected (not private) so that subclasses such as BST can use it.
    protected Node root;

    /** Creates an empty tree. */
    public BinaryTree() {
        this.root = null;
    }

    /** Creates a tree whose root is the given node (may be a hand-built tree). */
    public BinaryTree(Node root) {
        this.root = root;
    }

    public Node getRoot() { return root; }

    // Height is measured in edges: a leaf has height 0.
    // An empty tree has height -1.
    public int height() {
        return height(root);
    }

    private int height(Node node) {
        if (node == null) return -1;
        int leftHeight = height(node.getLeft());
        int rightHeight = height(node.getRight());
        return 1 + Math.max(leftHeight, rightHeight);
    }

    // ------------------------------------------------------------------
    // EXERCISE 1 — TODO
    // ------------------------------------------------------------------

    /**
     * Part A: Adds a new node containing {@code value} at the FIRST FREE POSITION
     * found by a LEVEL-ORDER (breadth-first) traversal of the tree.
     *
     * Rule: visit the nodes level by level, from top to bottom, and from left
     * to right within each level (use a queue). At each visited node:
     *   - if its left child is empty, attach the new node there and stop;
     *   - otherwise, if its right child is empty, attach the new node there and stop;
     *   - otherwise, keep traversing.
     * If the tree is empty, the new node becomes the root.
     * Duplicate values are allowed in a general binary tree.
     */
    public void add(int value) {
        Node newNode = new Node(value);

        if (root == null) {
            root = newNode;
            return;
        }

        Queue<Node> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.remove();

            if (current.getLeft() == null) {
                current.setLeft(newNode);
                return;
            }
            if (current.getRight() == null) {
                current.setRight(newNode);
                return;
            }
            // Both children present: visit them later, left before right.
            queue.add(current.getLeft());
            queue.add(current.getRight());
        }  
    }

    /**
     * Part B: Searches the tree for a node whose element equals {@code value}.
     * Use a PRE-ORDER traversal and return the FIRST matching node found
     * (this matters when the tree contains duplicates).
     *
     * @return the node containing value, or null if no such node exists
     *         (including when the tree is empty).
     */
    public Node search(int value) {
        return search(root, value);
    }

     /**
     * Pre-order search of the subtree rooted at {@code node}: check the node
     * itself, then its left subtree, then its right subtree. Returns the first
     * match found, or null. The tree is never modified.
     */
    private Node search(Node node, int value) {
        if (node == null) {
            return null;
        }
        if (node.getElement() == value) {
            return node;
        }
        Node found = search(node.getLeft(), value);
        if (found != null) {
            return found;
        }
        return search(node.getRight(), value);
    }
    
    /*
     * Exercise 1, Part C — Complexity of search (n = number of nodes)
     *
     * The input size is n, the number of nodes in the tree. Every call to
     * the helper only does a few constant-time steps (check for null,
     * compare the value, call on the children), so the running time depends
     * on how many nodes we end up visiting.
     *
     * Best case: O(1). If the value is at the root, the first comparison
     * matches and we return right away. It doesn't matter how big the tree
     * is. Same thing for an empty tree, it just returns null.
     *
     * Worst case: O(n). This happens when the value isn't in the tree at
     * all, or when it's the very last node pre-order reaches. A regular
     * binary tree has no ordering, so there's no way to know which side the
     * value might be on. We have to check every node, and each node is
     * visited only once, so it's n steps of constant work, which is O(n).
     *
     * Example of the worst case: build the tree with add(1) through add(7)
     * and call search(99). Since 99 isn't there, all 7 nodes get checked
     * before it returns null. Searching for 7 is also a worst case, because
     * 7 is the last node in pre-order (1, 2, 4, 5, 3, 6, 7).
     */

    // ------------------------------------------------------------------
    // PROVIDED — display the structure of any binary tree.
    // ------------------------------------------------------------------

    public void printTree() {
        if (root == null) {
            System.out.println("(empty tree)");
        }
        else {
            System.out.println(root.getElement());
            printChildren(root, "");
        }
    }

    private void printChildren(Node node, String prefix) {
        Node left = node.getLeft();
        Node right = node.getRight();

        if (left != null) {
            boolean last = right == null;
            System.out.println(prefix + (last ? "└── " : "├── ")
                    + "L: " + left.getElement());
            printChildren(left, prefix + (last ? "    " : "│   "));
        }
        if (right != null) {
            System.out.println(prefix + "└── R: " + right.getElement());
            printChildren(right, prefix + "    ");
        }
    }
}
