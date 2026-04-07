package smartgrid;

import java.util.*;

/**
 * DSA Role: Weighted directed graph using an adjacency list representation.
 * This is the central data structure of SmartGrid. Every algorithm —
 * MCMF, Dijkstra, BFS, DFS — operates on this graph.
 *
 * Internally uses:
 *   - HashMap<Integer, Node>       : O(1) node lookup by ID
 *   - HashMap<Integer, List<Edge>> : O(1) adjacency list lookup by source node ID
 *
 * Node IDs are auto-assigned starting at 0 and never reused.
 */
public class Graph {

    /** Registry of all nodes keyed by their unique integer ID */
    private final Map<Integer, Node> nodes;

    /**
     * Adjacency list: maps each node ID to the list of outgoing edges.
     * Invariant: every node ID in `nodes` has a corresponding key here.
     */
    private final Map<Integer, List<Edge>> adjList;

    /** Auto-incrementing counter; ensures every node gets a unique ID */
    private int nextId;

    /** Constructs an empty graph */
    public Graph() {
        nodes = new HashMap<>();
        adjList = new HashMap<>();
        nextId = 0;
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /**
     * Adds a new node to the graph and assigns it the next available ID.
     * Time complexity: O(1) amortized (HashMap put)
     *
     * @param label  display name shown in GridUI
     * @param type   NodeType classification
     * @param x      canvas x coordinate for rendering
     * @param y      canvas y coordinate for rendering
     * @param cost   cost per unit (used by MinHeap for EnergySources; 0 otherwise)
     * @return the newly created Node
     */
    public Node addNode(String label, NodeType type, int x, int y, int cost) {
        // Assign the next sequential ID and increment the counter
        int id = nextId++;
        Node node = new Node(id, label, type, x, y, cost);

        // Store in registry for O(1) lookup
        nodes.put(id, node);

        // Initialise an empty adjacency list for this node
        adjList.put(id, new ArrayList<>());

        return node;
    }

    /**
     * Adds a directed edge from node {@code from} to node {@code to}.
     * Time complexity: O(1) amortized (ArrayList add)
     *
     * @param from     source node ID
     * @param to       destination node ID
     * @param capacity maximum flow capacity of this transmission line
     * @param cost     cost per unit of energy transmitted
     */
    public void addEdge(int from, int to, int capacity, int cost) {
        // Validate both endpoints exist before adding
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("Both nodes must exist before adding an edge.");
        }
        // Append the new edge to the source node's adjacency list
        adjList.get(from).add(new Edge(from, to, capacity, cost));
    }

    /**
     * Removes a node and ALL edges incident to it (both outgoing and incoming).
     * Called during a FaultEvent to isolate the failed node from the graph.
     * Time complexity: O(V + E) — must scan all adjacency lists to remove incoming edges
     *
     * @param id the ID of the node to remove
     */
    public void removeNode(int id) {
        // Remove the node from the registry
        nodes.remove(id);

        // Remove the node's own outgoing adjacency list
        adjList.remove(id);

        // Scan every other node's adjacency list and remove edges pointing TO this node
        // This ensures no dangling references remain after the fault
        for (List<Edge> edges : adjList.values()) {
            edges.removeIf(e -> e.to == id);
        }
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    /**
     * Returns the list of outgoing edges from the given node.
     * Time complexity: O(1) (HashMap get)
     *
     * @param id source node ID
     * @return list of outgoing Edge objects; empty list if node has no outgoing edges
     */
    public List<Edge> getNeighbors(int id) {
        // Return empty list rather than null if node doesn't exist (defensive)
        return adjList.getOrDefault(id, Collections.emptyList());
    }

    /**
     * Returns all nodes currently in the graph.
     * Time complexity: O(1) (HashMap values view)
     *
     * @return collection of all Node objects
     */
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    /**
     * Looks up a node by its unique ID.
     * Time complexity: O(1) (HashMap get)
     *
     * @param id node ID
     * @return the Node, or null if not found
     */
    public Node getNode(int id) {
        return nodes.get(id);
    }

    /**
     * Returns the total number of nodes currently in the graph.
     * Time complexity: O(1)
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the total number of edges currently in the graph.
     * Time complexity: O(V) — sums sizes of all adjacency lists
     */
    public int edgeCount() {
        int count = 0;
        for (List<Edge> edges : adjList.values()) {
            count += edges.size();
        }
        return count;
    }

    /**
     * Returns a read-only view of the full adjacency list map.
     * Used by MCMF to build the ResidualGraph and by GridUI for rendering.
     * Time complexity: O(1)
     */
    public Map<Integer, List<Edge>> getAdjList() {
        return Collections.unmodifiableMap(adjList);
    }
}
