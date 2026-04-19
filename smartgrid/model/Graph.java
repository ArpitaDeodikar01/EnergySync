package smartgrid.model;

import java.util.*;

/**
 * Weighted directed graph using an adjacency list representation.
 * Central data structure — all algorithms operate on this graph.
 *
 * Internally uses:
 *   - HashMap<Integer, Node>       : O(1) node lookup by ID
 *   - HashMap<Integer, List<Edge>> : O(1) adjacency list lookup by source node ID
 */
public class Graph {

    private final Map<Integer, Node> nodes;
    private final Map<Integer, List<Edge>> adjList;
    private int nextId;

    public Graph() {
        nodes = new HashMap<>();
        adjList = new HashMap<>();
        nextId = 0;
    }

    /** Adds a new node and assigns it the next available ID. O(1) amortized */
    public Node addNode(String label, NodeType type, int x, int y, int cost) {
        int id = nextId++;
        Node node = new Node(id, label, type, x, y, cost);
        nodes.put(id, node);
        adjList.put(id, new ArrayList<>());
        return node;
    }

    /** Adds a directed edge from {@code from} to {@code to}. O(1) amortized */
    public void addEdge(int from, int to, int capacity, int cost) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("Both nodes must exist before adding an edge.");
        }
        adjList.get(from).add(new Edge(from, to, capacity, cost));
    }

    /**
     * Removes a node and ALL incident edges. O(V + E)
     * Called during fault injection to isolate the failed node.
     */
    public void removeNode(int id) {
        nodes.remove(id);
        adjList.remove(id);
        for (List<Edge> edges : adjList.values()) {
            edges.removeIf(e -> e.to == id);
        }
    }

    /** Returns outgoing edges from the given node. O(1) */
    public List<Edge> getNeighbors(int id) {
        return adjList.getOrDefault(id, Collections.emptyList());
    }

    /** Returns all nodes in the graph. O(1) */
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    /** Looks up a node by ID. O(1) */
    public Node getNode(int id) {
        return nodes.get(id);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        int count = 0;
        for (List<Edge> edges : adjList.values()) count += edges.size();
        return count;
    }

    /** Returns a read-only view of the full adjacency list. O(1) */
    public Map<Integer, List<Edge>> getAdjList() {
        return Collections.unmodifiableMap(adjList);
    }
}
