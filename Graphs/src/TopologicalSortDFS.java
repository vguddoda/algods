import java.util.*;

public class TopologicalSortDFS {

    private int vertices;
    private List<List<Integer>> adjList;

    // Constructor
    public TopologicalSortDFS(int vertices) {
        this.vertices = vertices;
        adjList = new ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            adjList.add(new ArrayList<>());
        }
    }

    // Add directed edge from u to v
    public void addEdge(int u, int v) {
        adjList.get(u).add(v);
    }

    // DFS utility function
    private void DFS(int u, boolean[] visited, Stack<Integer> stack) {
        // (1) Mark u as visited
        visited[u] = true;

        // (2) For every adjacent vertex v of u
        for (int v : adjList.get(u)) {
            if (!visited[v]) {
                DFS(v, visited, stack);
            }
        }

        // (3) Push u to stack
        stack.push(u);
    }

    // Main topological sort function
    public void topologicalSort() {
        // (1) Create an empty stack
        Stack<Integer> stack = new Stack<>();
        boolean[] visited = new boolean[vertices];

        // (2) For every vertex u, do following
        // If u is not visited, call DFS(u, stack)
        for (int u = 0; u < vertices; u++) {
            if (!visited[u]) {
                DFS(u, visited, stack);
            }
        }

        // (3) While stack is not empty
        // Pop an item from stack and print it
        System.out.println("Topological Sort Order:");
        while (!stack.isEmpty()) {
            System.out.print(stack.pop() + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        /*
         Graph from the image:
         0 �� 1
         1 → 3
         2 → 3 → 4

         Topological order: 2 0 1 3 4 (or other valid orders)
        */

        TopologicalSortDFS graph = new TopologicalSortDFS(5);

        graph.addEdge(0, 1);
        graph.addEdge(1, 3);
        graph.addEdge(2, 3);
        graph.addEdge(3, 4);

        graph.topologicalSort();

        System.out.println("\n--- Another Example ---");

        /*
         Another example:
         5 → 2 → 3
         5 → 0
         4 → 0 → 1

         Valid order: 5 4 2 3 0 1 or 4 5 0 2 3 1
        */

        TopologicalSortDFS graph2 = new TopologicalSortDFS(6);

        graph2.addEdge(5, 2);
        graph2.addEdge(5, 0);
        graph2.addEdge(4, 0);
        graph2.addEdge(4, 1);
        graph2.addEdge(2, 3);
        graph2.addEdge(3, 1);

        graph2.topologicalSort();
    }
}
