import java.util.*;

class Graph {
    private int vertices;
    private LinkedList<Integer>[] adjacencyList;

    // Constructor
    @SuppressWarnings("unchecked")
    public Graph(int v) {
        vertices = v;
        adjacencyList = new LinkedList[v];
        for (int i = 0; i < v; i++) {
            adjacencyList[i] = new LinkedList<>();
        }
    }

    // Add edge to undirected graph
    public void addEdge(int source, int destination) {
        adjacencyList[source].add(destination);
        adjacencyList[destination].add(source); // Undirected graph
    }

    // DFS helper function to detect cycle
    private boolean dfsCycleDetection(int node, boolean[] visited, int parent) {
        // Mark current node as visited
        visited[node] = true;

        // Visit all adjacent vertices
        for (Integer neighbor : adjacencyList[node]) {
            // If neighbor is not visited, recursively check
            if (!visited[neighbor]) {
                if (dfsCycleDetection(neighbor, visited, node)) {
                    return true; // Cycle found in recursion
                }
            }
            // If neighbor is visited AND it's not the parent
            // Then we have found a cycle
            else if (neighbor != parent) {
                return true;
            }
        }
        return false;
    }

    // Main function to detect cycle in graph
    public boolean hasCycle() {
        boolean[] visited = new boolean[vertices];

        // Check for all vertices (handles disconnected components)
        for (int i = 0; i < vertices; i++) {
            if (!visited[i]) {
                if (dfsCycleDetection(i, visited, -1)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Display the graph
    public void printGraph() {
        for (int i = 0; i < vertices; i++) {
            System.out.print("Vertex " + i + ":");
            for (Integer neighbor : adjacencyList[i]) {
                System.out.print(" -> " + neighbor);
            }
            System.out.println();
        }
    }
}

public class CycleDetection {
    public static void main(String[] args) {
        // Example 1: Graph WITH a cycle
        System.out.println("=== Graph 1: WITH Cycle ===");
        Graph graph1 = new Graph(5);
        graph1.addEdge(0, 1);
        graph1.addEdge(1, 2);
        graph1.addEdge(2, 3);
        graph1.addEdge(3, 4);
        graph1.addEdge(4, 1); // Creates a cycle: 1-2-3-4-1

        graph1.printGraph();
        System.out.println("Has Cycle? " + graph1.hasCycle());

        System.out.println("\n=== Graph 2: WITHOUT Cycle ===");
        // Example 2: Graph WITHOUT a cycle (Tree)
        Graph graph2 = new Graph(5);
        graph2.addEdge(0, 1);
        graph2.addEdge(1, 2);
        graph2.addEdge(1, 3);
        graph2.addEdge(3, 4);

        graph2.printGraph();
        System.out.println("Has Cycle? " + graph2.hasCycle());

        System.out.println("\n=== Graph 3: Disconnected WITH Cycle ===");
        // Example 3: Disconnected graph with cycle
        Graph graph3 = new Graph(6);
        graph3.addEdge(0, 1);
        graph3.addEdge(1, 2);
        graph3.addEdge(2, 0); // Cycle here
        graph3.addEdge(3, 4); // Separate component
        graph3.addEdge(4, 5);

        graph3.printGraph();
        System.out.println("Has Cycle? " + graph3.hasCycle());
    }
}
