import java.util.*;

class TreeNode {
    int val;
    TreeNode left, right;

    TreeNode(int val) {
        this.val = val;
        this.left = null;
        this.right = null;
    }
}

public class TreeTraversal {

    // ========== BFS on Tree (Level Order) ==========

    public static void bfsTree(TreeNode root) {
        if (root == null) return;

        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);

        System.out.println("BFS (Level Order) Traversal:");
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            System.out.print(current.val + " ");

            if (current.left != null) queue.add(current.left);
            if (current.right != null) queue.add(current.right);
        }
        System.out.println();
    }

    // BFS with level information
    public static void bfsTreeLevels(TreeNode root) {
        if (root == null) return;

        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);
        int level = 0;

        System.out.println("\nBFS with Levels:");
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            System.out.print("Level " + level + ": ");

            for (int i = 0; i < levelSize; i++) {
                TreeNode current = queue.poll();
                System.out.print(current.val + " ");

                if (current.left != null) queue.add(current.left);
                if (current.right != null) queue.add(current.right);
            }
            System.out.println();
            level++;
        }
    }

    // ========== DFS on Tree ==========

    // Preorder (Root -> Left -> Right)
    public static void dfsPreorder(TreeNode root) {
        if (root == null) return;

        System.out.print(root.val + " ");
        dfsPreorder(root.left);
        dfsPreorder(root.right);
    }

    // Inorder (Left -> Root -> Right)
    public static void dfsInorder(TreeNode root) {
        if (root == null) return;

        dfsInorder(root.left);
        System.out.print(root.val + " ");
        dfsInorder(root.right);
    }

    // Postorder (Left -> Right -> Root)
    public static void dfsPostorder(TreeNode root) {
        if (root == null) return;

        dfsPostorder(root.left);
        dfsPostorder(root.right);
        System.out.print(root.val + " ");
    }

    // DFS Iterative (Preorder)
    public static void dfsIterative(TreeNode root) {
        if (root == null) return;

        Stack<TreeNode> stack = new Stack<>();
        stack.push(root);

        System.out.println("\nDFS Iterative (Preorder):");
        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            System.out.print(current.val + " ");

            // Push right first so left is processed first
            if (current.right != null) stack.push(current.right);
            if (current.left != null) stack.push(current.left);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        /*
         Tree structure:
                1
              /   \
             2     3
            / \   / \
           4   5 6   7
        */

        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);
        root.right.left = new TreeNode(6);
        root.right.right = new TreeNode(7);

        // BFS
        bfsTree(root);
        bfsTreeLevels(root);

        // DFS
        System.out.println("\nDFS Preorder (Root-Left-Right):");
        dfsPreorder(root);

        System.out.println("\n\nDFS Inorder (Left-Root-Right):");
        dfsInorder(root);

        System.out.println("\n\nDFS Postorder (Left-Right-Root):");
        dfsPostorder(root);

        dfsIterative(root);
    }
}