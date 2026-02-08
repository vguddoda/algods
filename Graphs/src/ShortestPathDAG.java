import java.util.*;

class Edge {
    int dest;
    int weight;

    Edge(int dest, int weight) {
        this.dest = dest;
        this.weight = weight;
    }
}

public class ShortestPathDAG {

    private int vertices;
    private List<List<Edge>> adjList;

    // Constructor
    public ShortestPathDAG(int vertices) {
        this.vertices = vertices;
        adjList = new ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            adjList.add(new ArrayList<>());
        }
    }

    // Add weighted directed edge
    public void addEdge(int u, int v, int weight) {
        adjList.get(u).add(new Edge(v, weight));
    }

    // DFS utility for topological sort
    private void topologicalSortUtil(int u, boolean[] visited, Stack<Integer> stack) {
        visited[u] = true;

        // Visit all adjacent vertices
        for (Edge edge : adjList.get(u)) {
            if (!visited[edge.dest]) {
                topologicalSortUtil(edge.dest, visited, stack);
            }
        }

        // Push current vertex to stack after visiting all descendants
        stack.push(u);
    }

    // Main function to find shortest paths from source
    public void shortestPath(int source) {
        Stack<Integer> stack = new Stack<>();

        // Step 1: Initialize distances to INF
        int[] dist = new int[vertices];
        Arrays.fill(dist, Integer.MAX_VALUE);

        // Step 2: Set distance of source to 0
        dist[source] = 0;

        // Step 3: Find topological sort
        boolean[] visited = new boolean[vertices];
        for (int i = 0; i < vertices; i++) {
            if (!visited[i]) {
                topologicalSortUtil(i, visited, stack);
            }
        }

        // Print topological sort
        System.out.println("Topological Sort: " + stack);

        // Step 4: Process vertices in topological order
        while (!stack.isEmpty()) {
            int u = stack.pop();

            // Update distances of all adjacent vertices
            if (dist[u] != Integer.MAX_VALUE) {
                for (Edge edge : adjList.get(u)) {
                    int v = edge.dest;
                    int weight = edge.weight;

                    // Relaxation step
                    if (dist[v] > dist[u] + weight) {
                        dist[v] = dist[u] + weight;
                    }
                }
            }
        }

        // Print shortest distances
        printDistances(dist, source);
    }

    // Print distances
    private void printDistances(int[] dist, int source) {
        System.out.println("\nShortest distances from source " + source + ":");
        System.out.println("Vertex\tDistance from Source");
        for (int i = 0; i < vertices; i++) {
            if (dist[i] == Integer.MAX_VALUE) {
                System.out.println(i + "\t\tINF");
            } else {
                System.out.println(i + "\t\t" + dist[i]);
            }
        }
    }

    public static void main(String[] args) {
        /*
         Graph from the image:
         0 --(2)--> 1 --(3)--> 2 --(6)--> 3
         |                      ^          ^
        (1)                    (2)        (1)
         |                      |          |
         4 ---------(4)-----> 5 -----------
         
         Source: 0
        */

        ShortestPathDAG graph = new ShortestPathDAG(6);

        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 4, 1);
        graph.addEdge(1, 2, 3);
        graph.addEdge(4, 5, 4);
        graph.addEdge(5, 2, 2);
        graph.addEdge(2, 3, 6);
        graph.addEdge(5, 3, 1);

        graph.shortestPath(0);

        System.out.println("\n" + "=".repeat(50));
        System.out.println("Another Example:");
        System.out.println("=".repeat(50));
        
        /*
         Another example:
         0 --(5)--> 1 --(3)--> 3
         |          |
        (2)        (6)
         |          |
         2 ---------> 3
        */

        ShortestPathDAG graph2 = new ShortestPathDAG(4);
        graph2.addEdge(0, 1, 5);
        graph2.addEdge(0, 2, 2);
        graph2.addEdge(1, 3, 3);
        graph2.addEdge(2, 3, 6);

        graph2.shortestPath(0);
    }
}