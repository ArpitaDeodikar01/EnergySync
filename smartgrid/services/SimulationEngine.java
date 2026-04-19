package smartgrid.services;

import smartgrid.algorithms.*;
import smartgrid.ds.FenwickTree;
import smartgrid.ds.SegmentTree;
import smartgrid.ds.UnionFind;
import smartgrid.model.*;

import java.util.*;
import java.util.List;

/**
 * Central simulation engine. Owns all graph state and orchestrates
 * every algorithm in SmartGrid. GridUI is a pure view that delegates
 * all simulation actions here.
 *
 * Algorithms used:
 *   1. MCMFAlgorithm       — Min-Cost Max-Flow via SPFA
 *   2. DijkstraAlgorithm   — shortest path via min-heap
 *   3. BFSAlgorithm        — fault impact detection
 *   4. DFSAlgorithm        — faulty subgraph isolation
 *   5. FenwickTree         — cumulative energy tracking (prefix sums)
 *   6. PriorityQueue<Node> — MinHeap load balancing by cost
 *   7. SegmentTree         — range energy queries + max-load zone detection
 *   8. UnionFind           — connected component detection + faulty subgraph isolation
 *
 * Adaptive services:
 *   9. WeightUpdateService — updates edge costs from observed performance
 *  10. PredictionEngine    — sliding-window failure prediction via max-heap
 */
public class SimulationEngine {

    private Graph graph;
    private FenwickTree fenwick;
    private SegmentTree segTree;   // range energy queries over CityZones
    private UnionFind unionFind;   // connected component tracking
    private PriorityQueue<Node> minHeap;
    private SimStats stats;
    private long faultTimestamp;
    private int preFaultFlow;
    private Set<Integer> lastFlowReceivers;
    private List<Node> dijkstraPath;

    /** Weight vector for composite edge cost. Defaults reproduce original behavior. */
    private CostWeights weights;

    /** Tracks per-edge performance and adaptively updates baseCost / failureRisk. */
    private final WeightUpdateService weightUpdateService;

    /** Maintains sliding window of failures and predicts next failure node. */
    private final PredictionEngine predictionEngine;

    /** Node IDs involved in the most recent fault — passed to WeightUpdateService. */
    private final Set<Integer> currentFaultedIds = new HashSet<>();

    /** When false, WeightUpdateService cost updates are skipped each run. */
    private boolean adaptiveMode = true;

    public SimulationEngine() {
        stats = new SimStats();
        lastFlowReceivers = new HashSet<>();
        dijkstraPath = new ArrayList<>();
        weights = new CostWeights(1.0, 0.15, 0.0, 0.0); // w2=0.15: carbon factored into routing by default
        weightUpdateService = new WeightUpdateService();
        predictionEngine = new PredictionEngine();
        buildTopology();
    }

    // -------------------------------------------------------------------------
    // Topology
    // -------------------------------------------------------------------------

    /**
     * Builds the hardcoded 10-node, 12-edge energy network topology.
     * Time complexity: O(V + E)
     */
    public void buildTopology() {
        graph = new Graph();

        graph.addNode("Solar",  NodeType.ENERGY_SOURCE, 80,  80,  1);
        graph.addNode("Wind",   NodeType.ENERGY_SOURCE, 80,  240, 2);
        graph.addNode("Hydro",  NodeType.ENERGY_SOURCE, 80,  400, 3);
        graph.addNode("Sub-A",  NodeType.SUBSTATION,    300, 120, 0);
        graph.addNode("Sub-B",  NodeType.SUBSTATION,    300, 280, 0);
        graph.addNode("Sub-C",  NodeType.SUBSTATION,    300, 420, 0);
        graph.addNode("Zone-1", NodeType.CITY_ZONE,     540, 80,  0);
        graph.addNode("Zone-2", NodeType.CITY_ZONE,     540, 200, 0);
        graph.addNode("Zone-3", NodeType.CITY_ZONE,     540, 340, 0);
        graph.addNode("Zone-4", NodeType.CITY_ZONE,     540, 460, 0);

        graph.addEdge(0, 3, 10, 1); // Solar  → Sub-A
        graph.addEdge(0, 4, 8,  2); // Solar  → Sub-B
        graph.addEdge(1, 3, 7,  2); // Wind   → Sub-A
        graph.addEdge(1, 5, 9,  1); // Wind   → Sub-C
        graph.addEdge(2, 4, 6,  3); // Hydro  → Sub-B
        graph.addEdge(2, 5, 8,  2); // Hydro  → Sub-C
        graph.addEdge(3, 6, 5,  1); // Sub-A  → Zone-1
        graph.addEdge(3, 7, 6,  2); // Sub-A  → Zone-2
        graph.addEdge(4, 7, 4,  1); // Sub-B  → Zone-2
        graph.addEdge(4, 8, 7,  2); // Sub-B  → Zone-3
        graph.addEdge(5, 8, 5,  1); // Sub-C  → Zone-3
        graph.addEdge(5, 9, 6,  2); // Sub-C  → Zone-4

        // Assign carbon costs (kg CO₂ per unit of energy) reflecting source type.
        // Solar edges: ~0 carbon; Wind: very low; Hydro: low; transmission lines: small fixed cost.
        // Stored on the Edge objects after construction via the adjacency list.
        assignCarbonCosts();

        fenwick = new FenwickTree(4);

        // SegmentTree over 4 CityZones — rebuilt from currentLoad after each MCMF run
        segTree = new SegmentTree(4);

        // UnionFind over all nodes — rebuilt from live graph edges
        rebuildUnionFind();

        minHeap = new PriorityQueue<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.ENERGY_SOURCE) minHeap.offer(n);
        }

        stats = new SimStats();
        preFaultFlow = 0;
        faultTimestamp = 0;
        lastFlowReceivers = new HashSet<>();
        dijkstraPath = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    public void reset() {
        currentFaultedIds.clear();
        weightUpdateService.getAllRecords(); // no-op, just keeping reference alive
        predictionEngine.reset();
        buildTopology();
    }

    // -------------------------------------------------------------------------
    // Effective cost recomputation
    // -------------------------------------------------------------------------

    /**
     * Recomputes effectiveCost on every edge using the current weights.
     * Called before each algorithm run and immediately after setWeights().
     */
    private void recomputeEffectiveCosts() {
        for (List<Edge> edges : graph.getAdjList().values()) {
            for (Edge e : edges) {
                e.computeEffectiveCost(weights);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Carbon cost assignment
    // -------------------------------------------------------------------------

    /**
     * Assigns carbonCost values to every edge based on the energy source type
     * at the upstream end of each path segment.
     *
     * Carbon factors (kg CO₂ per unit of energy):
     *   Solar edges (from=0)  : 0.01  — near-zero lifecycle emissions
     *   Wind  edges (from=1)  : 0.02  — very low lifecycle emissions
     *   Hydro edges (from=2)  : 0.05  — low but non-zero (reservoir methane)
     *   Substation→Zone edges : 0.03  — transmission line losses / infrastructure
     *
     * Called once from buildTopology() after all edges are created.
     */
    private void assignCarbonCosts() {
        for (List<Edge> edges : graph.getAdjList().values()) {
            for (Edge e : edges) {
                if (e.from == 0) {
                    e.carbonCost = 0.01; // Solar
                } else if (e.from == 1) {
                    e.carbonCost = 0.02; // Wind
                } else if (e.from == 2) {
                    e.carbonCost = 0.05; // Hydro
                } else {
                    e.carbonCost = 0.03; // Substation → Zone transmission
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Carbon footprint computation
    // -------------------------------------------------------------------------

    /**
     * Computes total carbon footprint for the most recent MCMF run and stores
     * the result in stats.totalCarbonKg and stats.carbonPerUnit.
     *
     * Formula: totalCarbonKg = Σ (edge.carbonCost × edge.flow) for all edges
     *
     * Time complexity: O(E)
     */
    private void computeCarbonFootprint() {
        double totalCarbon = 0.0;
        for (List<Edge> edges : graph.getAdjList().values()) {
            for (Edge e : edges) {
                totalCarbon += e.carbonCost * e.flow;
            }
        }
        stats.totalCarbonKg = totalCarbon;
        stats.carbonPerUnit = stats.totalEnergyRouted > 0
                ? totalCarbon / stats.totalEnergyRouted
                : 0.0;
    }

    // -------------------------------------------------------------------------
    // SegmentTree helpers
    // -------------------------------------------------------------------------

    /**
     * Updates node.currentLoad for each CityZone from the edges flowing into it,
     * then refreshes the SegmentTree so range queries reflect the latest MCMF result.
     *
     * currentLoad = sum of flow on all incoming edges to this zone.
     * Time complexity: O(V + E) to scan edges + O(Z log Z) to update SegmentTree
     *                  where Z = number of CityZones (4).
     */
    private void refreshSegmentTree() {
        // Reset all zone loads
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE) n.currentLoad = 0;
        }

        // Accumulate incoming flow into each CityZone's currentLoad
        for (List<Edge> edges : graph.getAdjList().values()) {
            for (Edge e : edges) {
                Node dst = graph.getNode(e.to);
                if (dst != null && dst.type == NodeType.CITY_ZONE) {
                    dst.currentLoad += e.flow;
                }
            }
        }

        // Push updated loads into the SegmentTree (1-based zone index)
        int zoneIdx = 1;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE) {
                segTree.update(zoneIdx, n.currentLoad);
                zoneIdx++;
            }
        }
    }

    // -------------------------------------------------------------------------
    // UnionFind helpers
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the UnionFind from scratch using the current graph's nodes and edges.
     * Treats the directed graph as undirected for connectivity purposes — an edge
     * u→v means u and v are in the same physical component.
     *
     * Time complexity: O((V + E) * α(V))
     */
    private void rebuildUnionFind() {
        // Collect all current node IDs
        List<Integer> ids = new ArrayList<>();
        for (Node n : graph.getNodes()) ids.add(n.id);
        unionFind = new UnionFind(ids);

        // Union nodes connected by edges (treat directed edges as undirected)
        for (Map.Entry<Integer, List<Edge>> entry : graph.getAdjList().entrySet()) {
            for (Edge e : entry.getValue()) {
                if (unionFind.contains(e.from) && unionFind.contains(e.to)) {
                    unionFind.union(e.from, e.to);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Normal Distribution
    // -------------------------------------------------------------------------

    /**
     * Resets all nodes to ACTIVE, recomputes edge costs, runs MCMF, updates FenwickTree.
     * Time complexity: dominated by MCMF — O(V * E * F)
     */
    public void runNormalDistribution() {
        for (Node n : graph.getNodes()) n.status = NodeStatus.ACTIVE;

        recomputeEffectiveCosts();

        MCMFAlgorithm mcmf = new MCMFAlgorithm(graph, weights);
        MCMFAlgorithm.Result result = mcmf.run(stats);
        preFaultFlow = result.totalFlow;
        lastFlowReceivers = result.flowReceivers;

        // Adaptive: update edge costs from observed performance
        if (adaptiveMode) {
            weightUpdateService.processRun(graph.getAdjList(), currentFaultedIds);
            recomputeEffectiveCosts();
        }

        // Refresh SegmentTree with latest zone loads from MCMF flow
        refreshSegmentTree();
        computeCarbonFootprint();

        int fenwickIdx = 1;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE && lastFlowReceivers.contains(n.id)) {
                fenwick.update(fenwickIdx, stats.totalEnergyRouted / Math.max(1, lastFlowReceivers.size()));
            }
            if (n.type == NodeType.CITY_ZONE) fenwickIdx++;
        }
    }

    // -------------------------------------------------------------------------
    // Fault Injection
    // -------------------------------------------------------------------------

    /**
     * Injects a fault into the specified node.
     * Runs BFS to mark affected CityZones, DFS to isolate the subgraph,
     * then removes the node from the graph and MinHeap.
     *
     * @param faultedId ID of the node to fault
     * @return false if the node doesn't exist or is already removed
     */
    public boolean injectFault(int faultedId) {
        faultTimestamp = System.currentTimeMillis();

        Node faultedNode = graph.getNode(faultedId);
        if (faultedNode == null) return false;

        faultedNode.status = NodeStatus.ISOLATED;

        BFSAlgorithm bfs = new BFSAlgorithm(graph);
        stats.zonesAffected = bfs.run(faultedId);

        DFSAlgorithm dfs = new DFSAlgorithm(graph);
        dfs.run(faultedId);

        // Track faulted node for WeightUpdateService in the next processRun call
        currentFaultedIds.clear();
        currentFaultedIds.add(faultedId);

        // Record failure in PredictionEngine sliding window
        int totalZones = (int) graph.getNodes().stream()
                .filter(n -> n.type == NodeType.CITY_ZONE).count();
        double severity = totalZones > 0
                ? (double) stats.zonesAffected / totalZones
                : 0.0;
        predictionEngine.recordFailure(faultedId, faultedNode.label, severity);

        graph.removeNode(faultedId);
        minHeap.remove(faultedNode);

        // Rebuild UnionFind to reflect the new graph topology after node removal
        rebuildUnionFind();
        return true;
    }

    // -------------------------------------------------------------------------
    // Auto Reroute
    // -------------------------------------------------------------------------

    /**
     * Re-runs MCMF excluding ISOLATED nodes, computes recovery time and flow loss.
     * Time complexity: dominated by MCMF — O(V * E * F)
     */
    public void autoReroute() {
        recomputeEffectiveCosts();

        MCMFAlgorithm mcmf = new MCMFAlgorithm(graph, weights);
        MCMFAlgorithm.Result result = mcmf.run(stats);
        int postRerouteFlow = result.totalFlow;
        lastFlowReceivers = result.flowReceivers;

        // Adaptive: update edge costs from post-fault observed performance
        if (adaptiveMode) {
            weightUpdateService.processRun(graph.getAdjList(), currentFaultedIds);
            recomputeEffectiveCosts();
        }

        // Refresh SegmentTree with post-reroute zone loads
        refreshSegmentTree();
        computeCarbonFootprint();

        stats.recoveryTimeMs = System.currentTimeMillis() - faultTimestamp;

        if (preFaultFlow > 0) {
            stats.flowLossPercent = (double)(preFaultFlow - postRerouteFlow) / preFaultFlow * 100.0;
        } else {
            stats.flowLossPercent = 0.0;
        }

        for (int id : lastFlowReceivers) {
            Node n = graph.getNode(id);
            if (n != null && n.type == NodeType.CITY_ZONE) n.status = NodeStatus.REROUTED;
        }

        int stillAffected = 0;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE && n.status == NodeStatus.FAULT) stillAffected++;
        }
        stats.zonesAffected = stillAffected;
    }

    // -------------------------------------------------------------------------
    // Dijkstra
    // -------------------------------------------------------------------------

    /**
     * Runs Dijkstra from the given node to the nearest CityZone.
     * Time complexity: O((V + E) log V)
     */
    public List<Node> runDijkstra(int fromNodeId) {
        recomputeEffectiveCosts();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph, weights);
        dijkstraPath = dijkstra.run(fromNodeId);
        return dijkstraPath;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SimStats getStats()                 { return stats; }
    public Graph getGraph()                    { return graph; }
    public List<Node> getDijkstraPath()        { return dijkstraPath; }
    public Set<Integer> getLastFlowReceivers() { return lastFlowReceivers; }

    /** Returns the current cost weight vector. */
    public CostWeights getWeights() { return weights; }

    /**
     * Replaces the cost weight vector and immediately recomputes effectiveCost
     * on all edges so the next algorithm run picks up the new weights.
     */
    public void setWeights(CostWeights weights) {
        this.weights = weights;
        recomputeEffectiveCosts();
    }

    // -------------------------------------------------------------------------
    // Adaptive services accessors
    // -------------------------------------------------------------------------

    /** Returns the WeightUpdateService for inspection or tuning (alpha/beta). */
    public WeightUpdateService getWeightUpdateService() { return weightUpdateService; }

    /** Returns the PredictionEngine for risk score queries and failure prediction. */
    public PredictionEngine getPredictionEngine() { return predictionEngine; }

    /**
     * Predicts the node ID most likely to fail next based on the sliding window.
     * @return node ID with highest risk score, or -1 if no failure history exists
     */
    public int predictNextFailureNode() {
        return predictionEngine.predictNextFailureNode();
    }

    // -------------------------------------------------------------------------
    // SegmentTree query API
    // -------------------------------------------------------------------------

    /**
     * Returns the total energy load across CityZones l..r (1-based, inclusive).
     * Time complexity: O(log Z) where Z = number of CityZones
     *
     * @param l 1-based left zone index
     * @param r 1-based right zone index
     * @return sum of currentLoad for zones in [l, r]
     */
    public int queryEnergyRange(int l, int r) {
        return segTree.queryEnergyRange(l, r);
    }

    /**
     * Returns the 1-based index of the CityZone currently carrying the highest load.
     * Time complexity: O(1)
     *
     * @return 1-based zone index of the max-load zone, or 0 if no data
     */
    public int queryMaxLoadZone() {
        return segTree.queryMaxLoadZone();
    }

    /**
     * Returns the maximum load value among CityZones l..r (1-based, inclusive).
     * Time complexity: O(log Z)
     */
    public int queryMaxLoad(int l, int r) {
        return segTree.queryMaxLoad(l, r);
    }

    /** Returns the SegmentTree for direct access if needed. */
    public SegmentTree getSegmentTree() { return segTree; }

    // -------------------------------------------------------------------------
    // UnionFind query API
    // -------------------------------------------------------------------------

    /**
     * Returns true if nodes a and b are in the same connected component.
     * Time complexity: O(α(V))
     */
    public boolean areConnected(int nodeIdA, int nodeIdB) {
        if (!unionFind.contains(nodeIdA) || !unionFind.contains(nodeIdB)) return false;
        return unionFind.connected(nodeIdA, nodeIdB);
    }

    /**
     * Returns the number of connected components in the current graph.
     * Time complexity: O(1)
     */
    public int getComponentCount() {
        return unionFind.componentCount();
    }

    /**
     * Returns all node IDs in the same component as the given node.
     * Used to identify the full faulty subgraph after a fault.
     * Time complexity: O(V * α(V))
     */
    public Set<Integer> getComponentOf(int nodeId) {
        if (!unionFind.contains(nodeId)) return Collections.emptySet();
        return unionFind.getComponent(nodeId);
    }

    /** Returns the UnionFind for direct access if needed. */
    public UnionFind getUnionFind() { return unionFind; }

    public boolean isAdaptiveMode()            { return adaptiveMode; }
    public void setAdaptiveMode(boolean on)    { this.adaptiveMode = on; }
}
