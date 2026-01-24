import java.util.*;

public class DFS {

    // Graph representation using Adjacency List
    private int vertices;
    private LinkedList<Integer>[] adjacencyList;

    // Constructor
    @SuppressWarnings("unchecked")
    public DFS(int v) {
        vertices = v;
        adjacencyList = new LinkedList[v];
        for (int i = 0; i < v; i++) {
            adjacencyList[i] = new LinkedList<>();
        }
    }

    // Add edge to graph
    public void addEdge(int source, int destination) {
        adjacencyList[source].add(destination);
    }

    // ========== RECURSIVE DFS ==========

    // DFS Traversal (Recursive)
    public void dfsRecursive(int startVertex) {
        boolean[] visited = new boolean[vertices];
        System.out.println("DFS Recursive Traversal starting from vertex " + startVertex + ":");
        dfsRecursiveUtil(startVertex, visited);
        System.out.println();
    }

    // Recursive helper method
    private void dfsRecursiveUtil(int vertex, boolean[] visited) {
        // Mark current node as visited and print it
        visited[vertex] = true;
        System.out.print(vertex + " ");

        // Recur for all adjacent vertices
        for (int adjacent : adjacencyList[vertex]) {
            if (!visited[adjacent]) {
                dfsRecursiveUtil(adjacent, visited);
            }
        }
    }

    // ========== ITERATIVE DFS ==========

    // DFS Traversal (Iterative using Stack)
    public void dfsIterative(int startVertex) {
        boolean[] visited = new boolean[vertices];
        Stack<Integer> stack = new Stack<>();

        System.out.println("DFS Iterative Traversal starting from vertex " + startVertex + ":");

        stack.push(startVertex);

        while (!stack.isEmpty()) {
            int current = stack.pop();

            if (!visited[current]) {
                visited[current] = true;
                System.out.print(current + " ");

                // Push all adjacent vertices (in reverse order for same result as recursive)
                // Create reverse iterator for same order as recursive
                List<Integer> adjacents = new ArrayList<>(adjacencyList[current]);
                Collections.reverse(adjacents);

                for (int adjacent : adjacents) {
                    if (!visited[adjacent]) {
                        stack.push(adjacent);
                    }
                }
            }
        }
        System.out.println();
    }

    // ========== PATH FINDING ==========

    // Find if path exists between two vertices
    public boolean hasPath(int start, int end) {
        boolean[] visited = new boolean[vertices];
        return hasPathUtil(start, end, visited);
    }

    private boolean hasPathUtil(int current, int end, boolean[] visited) {
        if (current == end) {
            return true;
        }

        visited[current] = true;

        for (int adjacent : adjacencyList[current]) {
            if (!visited[adjacent]) {
                if (hasPathUtil(adjacent, end, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Find a path (any path, not necessarily shortest)
    public void findPath(int start, int end) {
        boolean[] visited = new boolean[vertices];
        List<Integer> path = new ArrayList<>();
        path.add(start);

        if (findPathUtil(start, end, visited, path)) {
            System.out.println("Path found from " + start + " to " + end + ": " + path);
        } else {
            System.out.println("No path found from " + start + " to " + end);
        }
    }

    private boolean findPathUtil(int current, int end, boolean[] visited, List<Integer> path) {
        if (current == end) {
            return true;
        }

        visited[current] = true;

        for (int adjacent : adjacencyList[current]) {
            if (!visited[adjacent]) {
                path.add(adjacent);
                if (findPathUtil(adjacent, end, visited, path)) {
                    return true;
                }
                path.remove(path.size() - 1); // Backtrack
            }
        }

        return false;
    }

    // ========== DETECT CYCLE ==========

    // Detect cycle in directed graph
    public boolean hasCycle() {
        boolean[] visited = new boolean[vertices];
        boolean[] recursionStack = new boolean[vertices];

        for (int i = 0; i < vertices; i++) {
            if (hasCycleUtil(i, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleUtil(int vertex, boolean[] visited, boolean[] recursionStack) {
        if (recursionStack[vertex]) {
            return true; // Cycle detected
        }

        if (visited[vertex]) {
            return false;
        }

        visited[vertex] = true;
        recursionStack[vertex] = true;

        for (int adjacent : adjacencyList[vertex]) {
            if (hasCycleUtil(adjacent, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack[vertex] = false; // Remove from recursion stack
        return false;
    }

    // Main method with examples
    public static void main(String[] args) {
        // Create graph with 7 vertices
        DFS graph = new DFS(7);

        // Add edges
        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(1, 4);
        graph.addEdge(2, 5);
        graph.addEdge(2, 6);
        graph.addEdge(3, 5);
        
        /*
         Graph structure:
                0
              /   \
             1     2
            / \   / \
           3   4 5   6
            \ /
             5
        */

        // DFS Recursive
        graph.dfsRecursive(0);

        // DFS Iterative
        graph.dfsIterative(0);

        // Path finding
        System.out.println();
        System.out.println("Has path from 0 to 5? " + graph.hasPath(0, 5));
        System.out.println("Has path from 0 to 7? " + graph.hasPath(0, 6));

        graph.findPath(0, 5);

        // Cycle detection
        System.out.println();
        System.out.println("Graph has cycle? " + graph.hasCycle());

        // Add cycle
        DFS cyclicGraph = new DFS(3);
        cyclicGraph.addEdge(0, 1);
        cyclicGraph.addEdge(1, 2);
        cyclicGraph.addEdge(2, 0);
        System.out.println("Cyclic graph has cycle? " + cyclicGraph.hasCycle());
    }
}