import java.util.*;

public class BFS {

    // Graph representation using Adjacency List
    private int vertices;
    private LinkedList<Integer>[] adjacencyList;

    // Constructor
    @SuppressWarnings("unchecked")
    public BFS(int v) {
        vertices = v;
        adjacencyList = new LinkedList[v];
        for (int i = 0; i < v; i++) {
            // 5 Initialize adjacency list for each vertex
            adjacencyList[i] = new LinkedList<>();
        }
    }

    // Add edge to graph
    public void addEdge(int source, int destination) {
        adjacencyList[source].add(destination);
    }

    // BFS Traversal
    public void bfsTraversal(int startVertex) {
        // Track visited nodes
        boolean[] visited = new boolean[vertices];

        // Queue for BFS
        Queue<Integer> queue = new LinkedList<>();

        // Mark start vertex as visited and enqueue it
        visited[startVertex] = true;
        queue.add(startVertex);

        System.out.println("BFS Traversal starting from vertex " + startVertex + ":");

        while (!queue.isEmpty()) {
            // Dequeue a vertex and print it
            int currentVertex = queue.poll();
            System.out.print(currentVertex + " ");

            // Get all adjacent vertices of dequeued vertex
            // If adjacent vertex not visited, mark visited and enqueue
            for (int adjacent : adjacencyList[currentVertex]) {
                if (!visited[adjacent]) {
                    visited[adjacent] = true;
                    queue.add(adjacent);
                }
            }
        }
        System.out.println();
    }

    // BFS to find shortest path (unweighted graph)
    public void shortestPath(int start, int end) {
        boolean[] visited = new boolean[vertices];
        int[] parent = new int[vertices];
        Arrays.fill(parent, -1);

        Queue<Integer> queue = new LinkedList<>();
        visited[start] = true;
        queue.add(start);

        while (!queue.isEmpty()) {
            int current = queue.poll();

            if (current == end) {
                printPath(parent, start, end);
                return;
            }

            for (int adjacent : adjacencyList[current]) {
                if (!visited[adjacent]) {
                    visited[adjacent] = true;
                    parent[adjacent] = current;
                    queue.add(adjacent);
                }
            }
        }

        System.out.println("No path found from " + start + " to " + end);
    }

    // Helper method to print path
    private void printPath(int[] parent, int start, int end) {
        List<Integer> path = new ArrayList<>();
        int current = end;

        while (current != -1) {
            path.add(current);
            current = parent[current];
        }

        Collections.reverse(path);
        System.out.println("Shortest path from " + start + " to " + end + ": " + path);
        System.out.println("Distance: " + (path.size() - 1) + " edges");
    }

    // BFS Level Order (returns nodes at each level)
    public void levelOrderTraversal(int startVertex) {
        boolean[] visited = new boolean[vertices];
        Queue<Integer> queue = new LinkedList<>();

        visited[startVertex] = true;
        queue.add(startVertex);

        int level = 0;
        System.out.println("\nLevel Order Traversal:");

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            System.out.print("Level " + level + ": ");

            for (int i = 0; i < levelSize; i++) {
                int current = queue.poll();
                System.out.print(current + " ");

                for (int adjacent : adjacencyList[current]) {
                    if (!visited[adjacent]) {
                        visited[adjacent] = true;
                        queue.add(adjacent);
                    }
                }
            }
            System.out.println();
            level++;
        }
    }

    // Main method with examples
    public static void main(String[] args) {
        // Create graph with 7 vertices
        BFS graph = new BFS(7);

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

        // BFS Traversal
        graph.bfsTraversal(0);

        // Shortest Path
        System.out.println();
        graph.shortestPath(0, 5);

        // Level Order
        graph.levelOrderTraversal(0);
    }
}