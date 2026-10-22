/**
 * COMP 272 Assignment 4 — Exercise 1.
 *
 * This is the AVLTree class from Module 4, unchanged except for delete().
 * Implement delete(). Do not change any other method, name or signature.
 * You may add private helper methods.
 */
public class AVLTree extends BinarySearchTree {

    @Override
    public Node add(int value) {
        // First perform ordinary BST insertion.
        Node inserted = super.add(value);

        if (inserted != null) {
            // The new leaf already has height 0.
            rebalanceUpward(inserted.parent);
        }

        return inserted;
    }

    /**
     * Positive: left subtree is taller.
     * Negative: right subtree is taller.
     */
    private int balanceFactor(Node node) {
        return height(node.left) - height(node.right);
    }

    /**
     * Update heights and restore balance from bottom to top.
     */
    private void rebalanceUpward(Node node) {
        while (node != null) {
            updateHeight(node);

            Node subtreeRoot = node;
            int balance = balanceFactor(node);

            if (balance > 1) {
                // Left-right case.
                if (balanceFactor(node.left) < 0) {
                    rotateLeft(node.left);
                }

                // Left-left case, or second left-right rotation.
                subtreeRoot = rotateRight(node);

            } else if (balance < -1) {
                // Right-left case.
                if (balanceFactor(node.right) > 0) {
                    rotateRight(node.right);
                }

                // Right-right case, or second right-left rotation.
                subtreeRoot = rotateLeft(node);
            }

            node = subtreeRoot.parent;
        }
    }

    /**
     * Rotate a subtree left and return its new root.
     */
    private Node rotateLeft(Node oldRoot) {
        Node newRoot = oldRoot.right;
        Node middle = newRoot.left;

        replaceAtParent(oldRoot, newRoot);

        newRoot.left = oldRoot;
        oldRoot.parent = newRoot;

        oldRoot.right = middle;
        if (middle != null) {
            middle.parent = oldRoot;
        }

        // Order matters: update the lower node first.
        updateHeight(oldRoot);
        updateHeight(newRoot);

        return newRoot;
    }

    /**
     * Rotate a subtree right and return its new root.
     */
    private Node rotateRight(Node oldRoot) {
        Node newRoot = oldRoot.left;
        Node middle = newRoot.right;

        replaceAtParent(oldRoot, newRoot);

        newRoot.right = oldRoot;
        oldRoot.parent = newRoot;

        oldRoot.left = middle;
        if (middle != null) {
            middle.parent = oldRoot;
        }

        // Order matters: update the lower node first.
        updateHeight(oldRoot);
        updateHeight(newRoot);

        return newRoot;
    }

    /**
     * Connect the new subtree root to the original parent.
     */
    private void replaceAtParent(Node oldRoot, Node newRoot) {
        Node parent = oldRoot.parent;
        newRoot.parent = parent;

        if (parent == null) {
            root = newRoot;
        } else if (parent.left == oldRoot) {
            parent.left = newRoot;
        } else {
            parent.right = newRoot;
        }
    }

    @Override
    public boolean delete(int value) {
        Node target = findNode(value);
            if (target == null) {
                return false;
            }
        
            // Two children: the inoder successor's value is copied and the sucessor is removed.
            if (target.left != null && target.right != null) {
                Node successor = minimum(target.right);
                target.value = successor.value;
                target = successor;
            }

            Node child = (target.left != null) ? target.left : target.right;
            Node parentofRemoved = target.parent;

            transplant(target, child);
            size--;

            //Rebalance from the parent of the physically removed node up to the root.
            rebalanceUpward(parentofRemoved);
            return true;
      
    }

    /*
     * ======================== Exercise 1, Part B — Analysis ========================
     *Let n be the number of nodes. An AVL tree has heoght h = O(log n).
     *
     *Q1. delete is O(log n) in the worst case.
     * - Finding the node: findNopde follows one root-to-node path, O(h).
     * - Finding the successor: minimum follows left links down, at most h steps, O(h).
     * - Removing it: transplant changes a constant number of pointers, O(1).
     * - Rebalancing: rebalanceUpward visits at most h + 1 anscestors, doing O(1).
     *   work at each (height update, balance check, at mot 2 rotations), O(h).
     * Total: O(h) = O(log n ).
     * 
     * Q2. One deletion can rotate at every level on the path to the reoot:
     * at most 2 per level, so O(log n) rotations. A rotation after a deletion
     * can leave the subtree one level shorter, which can unbalance an ancestor.
     * the subtree to its  height before the insertion, so no ancestor's balance changes.
     * ===============================================================================
     */

}
