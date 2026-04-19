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
    /** Scaled cost of the last Dijkstra run (divide by Edge.COST_SCALE for display). */
    private long dijkstraLastCost = 0;

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
     * Builds the realistic 14-node, 22-edge energy network topology.
     *
     * Sources  (4): Solar, Wind, Hydro, Nuclear
     * Substations (5): Sub-A … Sub-E
     * City Zones  (5): Hospital, Industry, Residential ×2, Commercial
     *
     * Key design decisions for realism:
     *  - Hospital (Zone-1) has TWO feeders (Sub-A + Sub-D) for redundancy
     *  - Nuclear feeds Sub-D exclusively → zero-carbon, high-reliability path to Hospital
     *  - Sub-A ↔ Sub-B and Sub-B ↔ Sub-C backup cross-links enable rerouting
     *  - Industry (Zone-2) has three possible upstream paths
     *  - Commercial (Zone-5) added as a 4th zone type
     *
     * Node IDs (assigned in insertion order):
     *   0=Solar  1=Wind  2=Hydro  3=Nuclear
     *   4=Sub-A  5=Sub-B  6=Sub-C  7=Sub-D  8=Sub-E
     *   9=Zone-1(Hospital)  10=Zone-2(Industry)
     *   11=Zone-3(Residential)  12=Zone-4(Residential)  13=Zone-5(Commercial)
     *
     * Time complexity: O(V + E)
     */
    public void buildTopology() {
        graph = new Graph();

        // --- Energy Sources ---
        graph.addNode("Solar",   NodeType.ENERGY_SOURCE, 70,  60,  1); // id=0
        graph.addNode("Wind",    NodeType.ENERGY_SOURCE, 70,  190, 2); // id=1
        graph.addNode("Hydro",   NodeType.ENERGY_SOURCE, 70,  330, 3); // id=2
        graph.addNode("Nuclear", NodeType.ENERGY_SOURCE, 70,  460, 1); // id=3  high-reliability

        // --- Substations ---
        graph.addNode("Sub-A",   NodeType.SUBSTATION,    260, 90,  0); // id=4
        graph.addNode("Sub-B",   NodeType.SUBSTATION,    260, 230, 0); // id=5
        graph.addNode("Sub-C",   NodeType.SUBSTATION,    260, 370, 0); // id=6
        graph.addNode("Sub-D",   NodeType.SUBSTATION,    260, 480, 0); // id=7  Nuclear feeder
        graph.addNode("Sub-E",   NodeType.SUBSTATION,    260, 150, 0); // id=8  Solar+Wind backup

        // --- City Zones ---
        Node zone1 = graph.addNode("Zone-1", NodeType.CITY_ZONE, 490, 60,  0); // id=9  Hospital
        Node zone2 = graph.addNode("Zone-2", NodeType.CITY_ZONE, 490, 180, 0); // id=10 Industry
        Node zone3 = graph.addNode("Zone-3", NodeType.CITY_ZONE, 490, 300, 0); // id=11 Residential
        Node zone4 = graph.addNode("Zone-4", NodeType.CITY_ZONE, 490, 400, 0); // id=12 Residential
        Node zone5 = graph.addNode("Zone-5", NodeType.CITY_ZONE, 490, 490, 0); // id=13 Commercial

        // Zone classifications
        zone1.zoneClass = ZoneClassification.HOSPITAL;     // Reliability-first
        zone2.zoneClass = ZoneClassification.INDUSTRY;     // CO2-first
        zone3.zoneClass = ZoneClassification.RESIDENTIAL;  // Cost-first
        zone4.zoneClass = ZoneClassification.RESIDENTIAL;  // Cost-first
        zone5.zoneClass = ZoneClassification.COMMERCIAL;   // Balanced

        // Zone capacities (for load bar visualisation)
        zone1.capacity = 12;
        zone2.capacity = 15;
        zone3.capacity = 10;
        zone4.capacity = 10;
        zone5.capacity = 8;

        // --- Source → Substation edges ---
        graph.addEdge(0, 4, 10, 1); // Solar   → Sub-A
        graph.addEdge(0, 8, 8,  2); // Solar   → Sub-E  (backup path)
        graph.addEdge(1, 4, 7,  2); // Wind    → Sub-A
        graph.addEdge(1, 5, 9,  1); // Wind    → Sub-B
        graph.addEdge(2, 5, 6,  3); // Hydro   → Sub-B
        graph.addEdge(2, 6, 8,  2); // Hydro   → Sub-C
        graph.addEdge(3, 7, 12, 1); // Nuclear → Sub-D  (dedicated Hospital feeder)

        // --- Cross-substation backup links (enable rerouting) ---
        graph.addEdge(4, 5, 5,  2); // Sub-A ↔ Sub-B  (backup)
        graph.addEdge(5, 6, 5,  2); // Sub-B ↔ Sub-C  (backup)
        graph.addEdge(8, 4, 6,  1); // Sub-E  → Sub-A  (Solar backup feeds Sub-A)

        // --- Substation → Zone edges ---
        graph.addEdge(4,  9, 5,  1); // Sub-A → Zone-1 (Hospital)  primary
        graph.addEdge(7,  9, 8,  1); // Sub-D → Zone-1 (Hospital)  Nuclear backup ← KEY
        graph.addEdge(4, 10, 6,  2); // Sub-A → Zone-2 (Industry)
        graph.addEdge(5, 10, 4,  1); // Sub-B → Zone-2 (Industry)  backup
        graph.addEdge(8, 10, 5,  2); // Sub-E → Zone-2 (Industry)  3rd path
        graph.addEdge(5, 11, 7,  2); // Sub-B → Zone-3 (Residential)
        graph.addEdge(6, 11, 5,  1); // Sub-C → Zone-3 (Residential) backup
        graph.addEdge(6, 12, 6,  2); // Sub-C → Zone-4 (Residential)
        graph.addEdge(7, 13, 5,  2); // Sub-D → Zone-5 (Commercial)
        graph.addEdge(6, 13, 4,  3); // Sub-C → Zone-5 (Commercial) backup

        assignCarbonCosts();

        // SegmentTree and FenwickTree over 5 CityZones
        int zoneCount = 5;
        fenwick  = new FenwickTree(zoneCount);
        segTree  = new SegmentTree(zoneCount);

        rebuildUnionFind();

        minHeap = new PriorityQueue<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.ENERGY_SOURCE) minHeap.offer(n);
        }

        stats = new SimStats();
        preFaultFlow  = 0;
        faultTimestamp = 0;
        lastFlowReceivers = new HashSet<>();
        dijkstraPath  = new ArrayList<>();
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
     * Assigns carbonCost values to every edge based on the source node's label.
     * This is label-based so it works correctly regardless of node insertion order.
     *
     * Carbon factors (kg CO₂ per unit of energy):
     *   Solar   : 0.01  — near-zero lifecycle emissions
     *   Wind    : 0.02  — very low lifecycle emissions
     *   Hydro   : 0.05  — low but non-zero (reservoir methane)
     *   Nuclear : 0.00  — zero operational carbon (used for Hospital reliability path)
     *   Substation→Zone : 0.03 — transmission line losses
     */
    private void assignCarbonCosts() {
        // Build label lookup for fast access
        Map<Integer, String> idToLabel = new HashMap<>();
        for (Node n : graph.getNodes()) idToLabel.put(n.id, n.label);

        for (List<Edge> edges : graph.getAdjList().values()) {
            for (Edge e : edges) {
                String srcLabel = idToLabel.getOrDefault(e.from, "");
                switch (srcLabel) {
                    case "Solar":   e.carbonCost = 0.01; break;
                    case "Wind":    e.carbonCost = 0.02; break;
                    case "Hydro":   e.carbonCost = 0.05; break;
                    case "Nuclear": e.carbonCost = 0.00; break; // zero-carbon
                    default:        e.carbonCost = 0.03; break; // substation transmission
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
     * Resets all nodes to ACTIVE, recomputes edge costs using per-zone weights,
     * runs MCMF, updates FenwickTree with per-zone energy totals.
     *
     * Per-zone routing: each CityZone's ZoneClassification carries its own
     * CostWeights. We run MCMF once per zone type with that zone's weights,
     * so Hospital paths are optimised for reliability, Industry for carbon, etc.
     *
     * Time complexity: dominated by MCMF — O(Z * V * E * F) where Z = zone types
     */
    public void runNormalDistribution() {
        for (Node n : graph.getNodes()) n.status = NodeStatus.ACTIVE;

        // Single MCMF pass with global weights — routes to ALL zones simultaneously.
        // Per-zone weights are applied in Dijkstra click routing (runDijkstraSmartClick),
        // not here, because MCMF resets edge flows each pass which would erase earlier zones.
        recomputeEffectiveCosts();

        MCMFAlgorithm mcmf = new MCMFAlgorithm(graph, weights);
        MCMFAlgorithm.Result result = mcmf.run(stats);
        preFaultFlow = result.totalFlow;
        lastFlowReceivers = result.flowReceivers;

        if (adaptiveMode) {
            weightUpdateService.processRun(graph.getAdjList(), currentFaultedIds);
            recomputeEffectiveCosts();
        }

        refreshSegmentTree();
        computeCarbonFootprint();
        stats.preFaultCarbonKg = stats.totalCarbonKg;
        stats.carbonIncreasePct = 0.0;

        // FenwickTree: update each zone with its actual currentLoad
        fenwick.reset();
        int fenwickIdx = 1;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE) {
                fenwick.update(fenwickIdx, n.currentLoad);
                fenwickIdx++;
            }
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

        // Compute how much CO2 increased vs the pre-fault baseline
        // Solar (lowest carbon) is gone — Wind/Hydro carry more load → higher CO2
        if (stats.preFaultCarbonKg > 0) {
            stats.carbonIncreasePct = (stats.totalCarbonKg - stats.preFaultCarbonKg)
                                      / stats.preFaultCarbonKg * 100.0;
        } else {
            stats.carbonIncreasePct = 0.0;
        }

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
     * Smart click handler: if the clicked node is a CITY_ZONE, runs Dijkstra
     * from the best available source TO that zone (shows incoming supply path).
     * Otherwise runs from the node to the nearest CityZone.
     */
    public List<Node> runDijkstraSmartClick(int clickedNodeId) {
        Node clicked = graph.getNode(clickedNodeId);
        if (clicked == null) return new ArrayList<>();

        if (clicked.type == NodeType.CITY_ZONE) {
            // Find the best source → this zone using zone's own priority weights
            CostWeights zoneWeights = clicked.zoneClass != null
                    ? clicked.zoneClass.weights : weights;
            for (List<Edge> edges : graph.getAdjList().values()) {
                for (Edge e : edges) e.computeEffectiveCost(zoneWeights);
            }

            // Try every active source, keep the cheapest path to this zone
            List<Node> bestPath = new ArrayList<>();
            long bestCost = Long.MAX_VALUE;
            DijkstraAlgorithm dijk = new DijkstraAlgorithm(graph, zoneWeights);
            for (Node src : graph.getNodes()) {
                if (src.type == NodeType.ENERGY_SOURCE && src.status == NodeStatus.ACTIVE) {
                    List<Node> p = dijk.runTo(src.id, clickedNodeId);
                    if (!p.isEmpty()) {
                        long c = computePathCost(p);
                        if (c < bestCost) { bestCost = c; bestPath = p; }
                    }
                }
            }
            dijkstraPath = bestPath;
            dijkstraLastCost = bestCost == Long.MAX_VALUE ? 0 : bestCost;
            recomputeEffectiveCosts(); // restore global weights
            return dijkstraPath;
        }

        // Non-zone node: run to nearest CityZone as before
        return runDijkstra(clickedNodeId);
    }

    /**
     * Runs Dijkstra from the given node to the nearest CityZone.
     */
    public List<Node> runDijkstra(int fromNodeId) {
        recomputeEffectiveCosts();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph, weights);
        dijkstraPath = dijkstra.run(fromNodeId);
        dijkstraLastCost = computePathCost(dijkstraPath);
        return dijkstraPath;
    }

    /**
     * Runs Dijkstra from {@code fromNodeId} to an explicit {@code toNodeId}.
     */
    public List<Node> runDijkstra(int fromNodeId, int toNodeId) {
        recomputeEffectiveCosts();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph, weights);
        dijkstraPath = dijkstra.runTo(fromNodeId, toNodeId);
        dijkstraLastCost = computePathCost(dijkstraPath);
        return dijkstraPath;
    }

    /** Returns the total effectiveCost of the last Dijkstra path (unscaled). */
    public double getDijkstraPathCost() {
        return dijkstraLastCost / (double) Edge.COST_SCALE;
    }

    private long computePathCost(List<Node> path) {
        long total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            int from = path.get(i).id, to = path.get(i + 1).id;
            for (Edge e : graph.getNeighbors(from)) {
                if (e.to == to) { total += e.scaledEffectiveCost(); break; }
            }
        }
        return total;
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
    // FenwickTree query API
    // -------------------------------------------------------------------------

    /**
     * Returns cumulative energy delivered to zones 1..zoneIdx (1-based, inclusive).
     * Uses the FenwickTree prefix-sum in O(log Z).
     * Displayed live in the UI as "Zones 1–N total load".
     */
    public int queryFenwickPrefix(int zoneIdx) {
        return fenwick.query(zoneIdx);
    }

    /**
     * Returns the ordered list of CityZone nodes (sorted by insertion order / id).
     * Used by the UI to map 1-based Fenwick indices back to zone labels.
     */
    public List<Node> getZoneNodes() {
        List<Node> zones = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE) zones.add(n);
        }
        zones.sort((a, b) -> Integer.compare(a.id, b.id));
        return zones;
    }

    /**
     * Returns all node risk scores as a map nodeId → riskScore.
     * Used by the UI to render the live risk heatmap.
     */
    public Map<Integer, Double> getAllRiskScores() {
        Map<Integer, Double> scores = new HashMap<>();
        for (Node n : graph.getNodes()) {
            scores.put(n.id, predictionEngine.getRiskScore(n.id));
        }
        return scores;
    }

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
