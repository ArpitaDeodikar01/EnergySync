package smartgrid.ds;

import java.util.*;

/**
 * Union-Find (Disjoint Set Union) with path compression and union-by-rank.
 *
 * Used in SmartGrid for two purposes:
 *   1. Detecting connected components in the energy network graph.
 *   2. Isolating faulty subgraphs — nodes in the same component as a faulted
 *      node can be identified in near-O(1) per query after O(V + E) build.
 *
 * Supports arbitrary integer node IDs (not just 0..n-1) via a HashMap.
 *
 * Time complexity:
 *   - union / find : O(α(n)) amortized — effectively O(1) for all practical n
 *   - build        : O((V + E) * α(V))
 *   - componentCount: O(1)
 */
public class UnionFind {

    /** Maps node ID → representative (root) node ID */
    private final Map<Integer, Integer> parent;

    /** Maps node ID → rank (upper bound on subtree height) */
    private final Map<Integer, Integer> rank;

    /** Number of distinct components currently in the structure */
    private int componentCount;

    /**
     * Constructs an empty UnionFind. Call {@link #addNode(int)} or
     * {@link #buildFromGraph} to populate it.
     */
    public UnionFind() {
        parent = new HashMap<>();
        rank   = new HashMap<>();
        componentCount = 0;
    }

    /**
     * Constructs a UnionFind pre-populated with the given node IDs.
     * Each node starts as its own component.
     *
     * @param nodeIds collection of node IDs to register
     */
    public UnionFind(Collection<Integer> nodeIds) {
        parent = new HashMap<>();
        rank   = new HashMap<>();
        componentCount = 0;
        for (int id : nodeIds) addNode(id);
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Registers a new node as its own singleton component.
     * No-op if the node is already registered.
     * Time complexity: O(1)
     */
    public void addNode(int id) {
        if (!parent.containsKey(id)) {
            parent.put(id, id);
            rank.put(id, 0);
            componentCount++;
        }
    }

    /**
     * Merges the components containing nodes {@code a} and {@code b}.
     * Uses union-by-rank to keep trees shallow.
     * Time complexity: O(α(n)) amortized
     *
     * @return true if a and b were in different components (a merge occurred)
     */
    public boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) return false; // already connected

        // Attach smaller-rank tree under larger-rank tree
        int rankA = rank.get(rootA);
        int rankB = rank.get(rootB);
        if (rankA < rankB) {
            parent.put(rootA, rootB);
        } else if (rankA > rankB) {
            parent.put(rootB, rootA);
        } else {
            parent.put(rootB, rootA);
            rank.put(rootA, rankA + 1);
        }

        componentCount--;
        return true;
    }

    /**
     * Finds the representative (root) of the component containing {@code id}.
     * Applies path compression: all nodes on the path point directly to the root.
     * Time complexity: O(α(n)) amortized
     */
    public int find(int id) {
        if (!parent.containsKey(id)) {
            throw new IllegalArgumentException("Node " + id + " not registered in UnionFind.");
        }
        if (parent.get(id) != id) {
            parent.put(id, find(parent.get(id))); // path compression
        }
        return parent.get(id);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns true if nodes {@code a} and {@code b} are in the same component.
     * Time complexity: O(α(n)) amortized
     */
    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    /**
     * Returns the number of distinct components.
     * Time complexity: O(1)
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * Returns all node IDs that belong to the same component as {@code nodeId}.
     * Useful for identifying the full faulty subgraph after a fault.
     * Time complexity: O(V * α(V))
     *
     * @param nodeId the node whose component members are requested
     * @return set of all node IDs in the same component (includes nodeId itself)
     */
    public Set<Integer> getComponent(int nodeId) {
        int root = find(nodeId);
        Set<Integer> component = new HashSet<>();
        for (int id : parent.keySet()) {
            if (find(id) == root) component.add(id);
        }
        return component;
    }

    /**
     * Returns a map from representative node ID → set of member node IDs,
     * covering all components.
     * Time complexity: O(V * α(V))
     */
    public Map<Integer, Set<Integer>> getAllComponents() {
        Map<Integer, Set<Integer>> components = new HashMap<>();
        for (int id : parent.keySet()) {
            int root = find(id);
            components.computeIfAbsent(root, k -> new HashSet<>()).add(id);
        }
        return components;
    }

    /** Returns true if the given node ID is registered. */
    public boolean contains(int id) {
        return parent.containsKey(id);
    }

    /** Returns all registered node IDs. */
    public Set<Integer> nodeIds() {
        return Collections.unmodifiableSet(parent.keySet());
    }
}
