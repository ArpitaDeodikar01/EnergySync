package smartgrid;

import java.util.*;

/**
 * DSA Role: Central simulation engine that owns all graph state and orchestrates
 * every DSA algorithm in SmartGrid.
 *
 * Algorithms implemented here:
 *   1. Min-Cost Max-Flow (MCMF) via SPFA on a ResidualGraph  — O(V * E * F)
 *   2. Dijkstra shortest path via min-heap PriorityQueue      — O((V+E) log V)
 *   3. BFS fault impact detection                             — O(V + E)
 *   4. DFS faulty subgraph isolation                          — O(V + E)
 *   5. MinHeap load balancing (PriorityQueue<Node> by cost)   — O(log n) per op
 *   6. FenwickTree energy tracking                            — O(log n) per op
 *
 * GridUI is a pure view; it reads state from this class and delegates all
 * simulation actions back to it via method calls.
 */
public class GridSimulator {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The live energy network graph; mutated by fault injection and reset */
    private Graph graph;

    /**
     * Fenwick Tree tracking cumulative energy per CityZone.
     * Sized to the number of CityZones (4 in the hardcoded topology).
     */
    private FenwickTree fenwick;

    /**
     * MinHeap of active EnergySources ordered by cost (cheapest first).
     * Backed by Java's PriorityQueue which is a binary min-heap.
     * Insert: O(log n), extract-min: O(log n)
     */
    private PriorityQueue<Node> minHeap;

    /** Aggregated metrics exposed to GridUI for the stats bar */
    private SimStats stats;

    /** System time (ms) recorded when injectFault() is called; used for RecoveryTime */
    private long faultTimestamp;

    /** Total flow from the most recent pre-fault MCMF run; used to compute FlowLoss */
    private int preFaultFlow;

    /**
     * Nodes that received flow in the last MCMF run, keyed by node ID.
     * Used to mark CityZones as REROUTED after auto-reroute.
     */
    private Set<Integer> lastFlowReceivers;

    /**
     * Dijkstra path from the last node click; stored so GridUI can highlight it.
     * Empty list means no path was found.
     */
    private List<Node> dijkstraPath;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /** Constructs the simulator and builds the initial hardcoded topology */
    public GridSimulator() {
        stats = new SimStats();
        lastFlowReceivers = new HashSet<>();
        dijkstraPath = new ArrayList<>();
        buildTopology();
    }

    // -------------------------------------------------------------------------
    // Topology
    // -------------------------------------------------------------------------

    /**
     * Builds the hardcoded 10-node, 12-edge energy network topology.
     * Also initialises the FenwickTree (4 CityZones) and MinHeap.
     *
     * Node layout on a 700x500 canvas:
     *   EnergySources — left column
     *   Substations   — middle column
     *   CityZones     — right column
     *
     * Time complexity: O(V + E) for graph construction
     */
    public void buildTopology() {
        graph = new Graph();

        // --- Energy Sources (left column, x=80) ---
        // ID 0: Solar  — cheapest source (cost=1)
        graph.addNode("Solar",  NodeType.ENERGY_SOURCE, 80,  80,  1);
        // ID 1: Wind   — medium cost (cost=2)
        graph.addNode("Wind",   NodeType.ENERGY_SOURCE, 80,  240, 2);
        // ID 2: Hydro  — most expensive source (cost=3)
        graph.addNode("Hydro",  NodeType.ENERGY_SOURCE, 80,  400, 3);

        // --- Substations (middle column, x=300) ---
        // ID 3: Sub-A
        graph.addNode("Sub-A",  NodeType.SUBSTATION,    300, 120, 0);
        // ID 4: Sub-B
        graph.addNode("Sub-B",  NodeType.SUBSTATION,    300, 280, 0);
        // ID 5: Sub-C
        graph.addNode("Sub-C",  NodeType.SUBSTATION,    300, 420, 0);

        // --- City Zones (right column, x=540) ---
        // IDs 6-9: Zone-1 through Zone-4
        graph.addNode("Zone-1", NodeType.CITY_ZONE,     540, 80,  0);
        graph.addNode("Zone-2", NodeType.CITY_ZONE,     540, 200, 0);
        graph.addNode("Zone-3", NodeType.CITY_ZONE,     540, 340, 0);
        graph.addNode("Zone-4", NodeType.CITY_ZONE,     540, 460, 0);

        // --- Transmission lines (from design table) ---
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

        // Initialise FenwickTree for 4 CityZones (indices 1..4 map to Zone-1..Zone-4)
        fenwick = new FenwickTree(4);

        // Populate MinHeap with all active EnergySources ordered by cost
        // PriorityQueue uses Node.compareTo which orders by cost — O(log n) per insert
        minHeap = new PriorityQueue<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.ENERGY_SOURCE) {
                minHeap.offer(n); // O(log n) insert
            }
        }

        // Reset stats to zero
        stats = new SimStats();
        preFaultFlow = 0;
        faultTimestamp = 0;
        lastFlowReceivers = new HashSet<>();
        dijkstraPath = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    /**
     * Restores the simulation to its initial state.
     * Rebuilds the graph topology, reinitialises FenwickTree, and repopulates MinHeap.
     * Called when [Reset] is clicked in GridUI.
     * Time complexity: O(V + E)
     */
    public void reset() {
        // Rebuild everything from scratch — simplest way to guarantee clean state
        buildTopology();
    }

    // -------------------------------------------------------------------------
    // MCMF — Min-Cost Max-Flow
    // -------------------------------------------------------------------------

    /**
     * Runs Min-Cost Max-Flow on the current graph (excluding ISOLATED nodes).
     * Uses SPFA (Bellman-Ford on the ResidualGraph) to find minimum-cost
     * augmenting paths from a virtual SuperSource to a virtual SuperSink.
     *
     * Algorithm outline:
     *   1. Build a ResidualGraph from the current graph (forward + backward edges).
     *   2. Add SuperSource (id = V) with zero-cost edges to all active EnergySources.
     *   3. Add SuperSink   (id = V+1) with zero-cost edges from all active CityZones.
     *   4. Repeat:
     *      a. Run SPFA to find the min-cost path from SuperSource to SuperSink.
     *      b. If no path exists, stop.
     *      c. Find the bottleneck capacity along the path.
     *      d. Augment flow along the path; update residual capacities.
     *      e. Accumulate total flow and total cost.
     *   5. Return total flow; update stats.
     *
     * Time complexity: O(V * E * F) where F = maximum flow value
     *   — Each SPFA call is O(V * E) in the worst case (Bellman-Ford bound).
     *   — At most F augmentation rounds are needed.
     *
     * @return total flow routed (total energy distributed)
     */
    private int runMCMF() {
        // Collect active (non-ISOLATED) nodes to determine residual graph size
        List<Node> activeNodes = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (n.status != NodeStatus.ISOLATED) {
                activeNodes.add(n);
            }
        }

        // Map real node IDs to compact indices [0..activeCount-1] for the residual arrays
        // SuperSource = activeCount, SuperSink = activeCount + 1
        Map<Integer, Integer> idToIdx = new HashMap<>();
        int idx = 0;
        for (Node n : activeNodes) {
            idToIdx.put(n.id, idx++);
        }
        int superSrc = idx;     // virtual super-source index
        int superSnk = idx + 1; // virtual super-sink index
        int total = idx + 2;    // total nodes in residual graph

        // --- Build ResidualGraph as adjacency lists of ResidualEdge objects ---
        // Each real edge becomes a forward edge (full capacity) and a backward edge (0 capacity).
        // Backward edges allow flow cancellation, which is essential for MCMF correctness.
        List<ResidualEdge>[] residual = buildResidualGraph(total, idToIdx, superSrc, superSnk, activeNodes);

        int totalFlow = 0;
        int totalCost = 0;
        lastFlowReceivers = new HashSet<>();

        // --- Main MCMF loop: augment until no more augmenting paths exist ---
        while (true) {
            // SPFA: find min-cost path from superSrc to superSnk in residual graph
            int[] dist = new int[total];       // min cost to reach each node
            int[] prevNode = new int[total];   // predecessor node on the path
            int[] prevEdge = new int[total];   // index of the edge used to reach each node
            Arrays.fill(dist, Integer.MAX_VALUE);
            Arrays.fill(prevNode, -1);
            Arrays.fill(prevEdge, -1);
            boolean[] inQueue = new boolean[total];

            dist[superSrc] = 0;
            Queue<Integer> queue = new LinkedList<>();
            queue.offer(superSrc);
            inQueue[superSrc] = true;

            // SPFA relaxation — Bellman-Ford style but with a queue for efficiency
            while (!queue.isEmpty()) {
                int u = queue.poll();
                inQueue[u] = false;

                // Relax all outgoing residual edges from u
                for (int i = 0; i < residual[u].size(); i++) {
                    ResidualEdge e = residual[u].get(i);
                    // Only traverse edges with remaining capacity
                    if (e.cap > 0 && dist[u] != Integer.MAX_VALUE && dist[u] + e.cost < dist[e.to]) {
                        dist[e.to] = dist[u] + e.cost;
                        prevNode[e.to] = u;
                        prevEdge[e.to] = i;
                        if (!inQueue[e.to]) {
                            queue.offer(e.to);
                            inQueue[e.to] = true;
                        }
                    }
                }
            }

            // If superSink is unreachable, no more augmenting paths — stop
            if (dist[superSnk] == Integer.MAX_VALUE) break;

            // Find bottleneck capacity along the path from superSrc to superSnk
            int flow = Integer.MAX_VALUE;
            int cur = superSnk;
            while (cur != superSrc) {
                int prev = prevNode[cur];
                ResidualEdge e = residual[prev].get(prevEdge[cur]);
                flow = Math.min(flow, e.cap);
                cur = prev;
            }

            // Augment flow along the path: reduce forward capacity, increase backward capacity
            cur = superSnk;
            while (cur != superSrc) {
                int prev = prevNode[cur];
                ResidualEdge fwd = residual[prev].get(prevEdge[cur]);
                fwd.cap -= flow;                          // consume capacity on forward edge
                residual[cur].get(fwd.rev).cap += flow;  // restore capacity on backward edge

                // Track which real CityZone nodes received flow (for REROUTED coloring)
                // Reverse-map the compact index back to the real node ID
                for (Map.Entry<Integer, Integer> entry : idToIdx.entrySet()) {
                    if (entry.getValue() == cur) {
                        Node n = graph.getNode(entry.getKey());
                        if (n != null && n.type == NodeType.CITY_ZONE) {
                            lastFlowReceivers.add(n.id);
                        }
                    }
                }
                cur = prev;
            }

            // Accumulate totals
            totalFlow += flow;
            totalCost += flow * dist[superSnk]; // cost of this augmentation round
        }

        // Persist results into SimStats
        stats.totalEnergyRouted = totalFlow;
        stats.totalCost = totalCost;

        return totalFlow;
    }

    /**
     * Builds the residual graph for MCMF.
     * Each real edge (u→v, cap, cost) becomes:
     *   - A forward residual edge  (u→v, cap=capacity, cost=+cost)
     *   - A backward residual edge (v→u, cap=0,        cost=-cost)
     * SuperSource connects to all active EnergySources (cap=∞, cost=0).
     * SuperSink connects from all active CityZones (cap=∞, cost=0).
     *
     * Time complexity: O(V + E)
     */
    @SuppressWarnings("unchecked")
    private List<ResidualEdge>[] buildResidualGraph(int total, Map<Integer, Integer> idToIdx,
                                                     int superSrc, int superSnk,
                                                     List<Node> activeNodes) {
        List<ResidualEdge>[] res = new ArrayList[total];
        for (int i = 0; i < total; i++) res[i] = new ArrayList<>();

        // Add real graph edges as forward + backward residual edge pairs
        for (Node n : activeNodes) {
            if (!idToIdx.containsKey(n.id)) continue;
            int u = idToIdx.get(n.id);
            for (Edge e : graph.getNeighbors(n.id)) {
                if (!idToIdx.containsKey(e.to)) continue; // skip edges to isolated nodes
                int v = idToIdx.get(e.to);
                // Forward edge index in res[u], backward edge index in res[v]
                int fwdIdx = res[u].size();
                int bwdIdx = res[v].size();
                res[u].add(new ResidualEdge(v, e.capacity, e.cost, bwdIdx));
                res[v].add(new ResidualEdge(u, 0, -e.cost, fwdIdx));
            }
        }

        // Connect SuperSource to all active EnergySources with infinite capacity, zero cost
        for (Node n : activeNodes) {
            if (n.type == NodeType.ENERGY_SOURCE && n.status == NodeStatus.ACTIVE) {
                int v = idToIdx.get(n.id);
                int fwdIdx = res[superSrc].size();
                int bwdIdx = res[v].size();
                res[superSrc].add(new ResidualEdge(v, Integer.MAX_VALUE / 2, 0, bwdIdx));
                res[v].add(new ResidualEdge(superSrc, 0, 0, fwdIdx));
            }
        }

        // Connect all active CityZones to SuperSink with infinite capacity, zero cost
        for (Node n : activeNodes) {
            if (n.type == NodeType.CITY_ZONE && n.status == NodeStatus.ACTIVE) {
                int u = idToIdx.get(n.id);
                int fwdIdx = res[u].size();
                int bwdIdx = res[superSnk].size();
                res[u].add(new ResidualEdge(superSnk, Integer.MAX_VALUE / 2, 0, bwdIdx));
                res[superSnk].add(new ResidualEdge(u, 0, 0, fwdIdx));
            }
        }

        return res;
    }

    // -------------------------------------------------------------------------
    // Normal Distribution
    // -------------------------------------------------------------------------

    /**
     * Runs normal energy distribution:
     *   1. Runs MCMF on the full (unfaulted) graph.
     *   2. Updates the FenwickTree for each CityZone that received flow.
     * Called when [Run Normal Distribution] is clicked.
     * Time complexity: dominated by MCMF — O(V * E * F)
     */
    public void runNormalDistribution() {
        // Reset all node statuses to ACTIVE before a fresh distribution run
        for (Node n : graph.getNodes()) {
            n.status = NodeStatus.ACTIVE;
        }

        // Run MCMF and record the pre-fault flow for later FlowLoss calculation
        preFaultFlow = runMCMF();

        // Update FenwickTree: for each CityZone that received flow, record the energy
        // CityZone IDs are 6-9; map to FenwickTree indices 1-4
        int fenwickIdx = 1;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE && lastFlowReceivers.contains(n.id)) {
                // Point update: add the proportional share of total flow to this zone's index
                // Using totalEnergyRouted / number of receiving zones as a simple distribution
                fenwick.update(fenwickIdx, stats.totalEnergyRouted / Math.max(1, lastFlowReceivers.size()));
            }
            if (n.type == NodeType.CITY_ZONE) fenwickIdx++;
        }
    }

    // -------------------------------------------------------------------------
    // Fault Injection
    // -------------------------------------------------------------------------

    /**
     * Injects a fault into the Solar node (ID=0):
     *   1. Records the fault timestamp for RecoveryTime measurement.
     *   2. Removes the Solar node and all its incident edges from the graph.
     *   3. Removes Solar from the MinHeap.
     *   4. Runs BFS to mark affected CityZones as FAULT.
     *   5. Runs DFS to mark the faulty subgraph as ISOLATED.
     * Called when [Inject Fault] is clicked.
     * Time complexity: O(V + E) dominated by BFS + DFS
     */
    public void injectFault() {
        // Record when the fault occurred — used to compute RecoveryTime later
        faultTimestamp = System.currentTimeMillis();

        // The Solar node always has ID=0 (first node added in buildTopology)
        int faultedId = 0;
        Node faultedNode = graph.getNode(faultedId);
        if (faultedNode == null) return; // already removed

        // Mark the faulted node itself as ISOLATED before graph removal
        faultedNode.status = NodeStatus.ISOLATED;

        // Run BFS first (before removing from graph) to find affected CityZones
        // BFS needs the edges still present to traverse forward
        runBFS(faultedId);

        // Run DFS to mark the full faulty subgraph
        runDFS(faultedId);

        // Remove the faulted node and all its edges from the graph
        graph.removeNode(faultedId);

        // Remove Solar from the MinHeap — it's no longer available for routing
        // PriorityQueue.remove is O(n) but heap is small; acceptable for simulation
        minHeap.remove(faultedNode);
    }

    // -------------------------------------------------------------------------
    // BFS — Fault Impact Detection
    // -------------------------------------------------------------------------

    /**
     * BFS from the faulted node over forward-directed edges.
     * Marks every reachable CityZone as FAULT and counts them in stats.zonesAffected.
     *
     * Why BFS: finds ALL reachable zones level by level — guarantees no zone is missed.
     * Time complexity: O(V + E) — each node and edge visited at most once
     *
     * @param faultedNodeId ID of the node that failed
     */
    public void runBFS(int faultedNodeId) {
        // Standard BFS with a visited set to avoid revisiting nodes
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();

        queue.offer(faultedNodeId);
        visited.add(faultedNodeId);
        int affectedZones = 0;

        while (!queue.isEmpty()) {
            int cur = queue.poll();

            // Check if this node is a CityZone — if so, mark it as FAULT
            Node curNode = graph.getNode(cur);
            if (curNode != null && curNode.type == NodeType.CITY_ZONE) {
                curNode.status = NodeStatus.FAULT;
                affectedZones++;
            }

            // Enqueue all unvisited neighbors reachable via forward edges
            for (Edge e : graph.getNeighbors(cur)) {
                if (!visited.contains(e.to)) {
                    visited.add(e.to);
                    queue.offer(e.to);
                }
            }
        }

        // Update stats so GridUI can display the count in the stats bar
        stats.zonesAffected = affectedZones;
    }

    // -------------------------------------------------------------------------
    // DFS — Faulty Subgraph Isolation
    // -------------------------------------------------------------------------

    /**
     * DFS from the faulted node over forward-directed edges.
     * Marks every visited node as ISOLATED so MCMF and Dijkstra skip them.
     *
     * Why DFS: naturally traces the full connected subgraph reachable from the fault.
     * Time complexity: O(V + E) — each node and edge visited at most once
     *
     * @param faultedNodeId ID of the node that failed
     */
    public void runDFS(int faultedNodeId) {
        Set<Integer> visited = new HashSet<>();
        // Use an explicit stack to avoid recursion stack overflow on large graphs
        Deque<Integer> stack = new ArrayDeque<>();

        stack.push(faultedNodeId);

        while (!stack.isEmpty()) {
            int cur = stack.pop();

            // Skip already-visited nodes to prevent infinite loops in cyclic graphs
            if (visited.contains(cur)) continue;
            visited.add(cur);

            // Mark this node as ISOLATED — excluded from future routing computations
            Node curNode = graph.getNode(cur);
            if (curNode != null) {
                curNode.status = NodeStatus.ISOLATED;
            }

            // Push all unvisited forward neighbors onto the stack
            for (Edge e : graph.getNeighbors(cur)) {
                if (!visited.contains(e.to)) {
                    stack.push(e.to);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Auto Reroute
    // -------------------------------------------------------------------------

    /**
     * Re-runs MCMF on the graph with all ISOLATED nodes excluded.
     * Records RecoveryTime, computes FlowLoss, and marks rerouted CityZones.
     * Called when [Auto Reroute] is clicked.
     * Time complexity: dominated by MCMF — O(V * E * F)
     */
    public void autoReroute() {
        // Re-run MCMF; runMCMF already skips ISOLATED nodes
        int postRerouteFlow = runMCMF();

        // RecoveryTime = elapsed ms from fault injection to MCMF completion
        stats.recoveryTimeMs = System.currentTimeMillis() - faultTimestamp;

        // FlowLoss = percentage of pre-fault flow that could not be rerouted
        if (preFaultFlow > 0) {
            stats.flowLossPercent = (double)(preFaultFlow - postRerouteFlow) / preFaultFlow * 100.0;
        } else {
            stats.flowLossPercent = 0.0;
        }

        // Mark CityZones that received flow in this reroute run as REROUTED
        for (int id : lastFlowReceivers) {
            Node n = graph.getNode(id);
            if (n != null && n.type == NodeType.CITY_ZONE) {
                n.status = NodeStatus.REROUTED;
            }
        }

        // Count zones still without power (FAULT status, not rerouted)
        int stillAffected = 0;
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.CITY_ZONE && n.status == NodeStatus.FAULT) {
                stillAffected++;
            }
        }
        stats.zonesAffected = stillAffected;
    }

    // -------------------------------------------------------------------------
    // Dijkstra — Shortest Path
    // -------------------------------------------------------------------------

    /**
     * Runs Dijkstra's algorithm from the given node to find the cheapest path
     * to the nearest reachable CityZone. Skips ISOLATED nodes.
     *
     * Uses a min-heap PriorityQueue of int[] {cost, nodeId} for O(log V) extraction.
     * Path is reconstructed via a predecessor map after the target is reached.
     *
     * Time complexity: O((V + E) log V)
     *   — Each node is extracted from the heap at most once: O(V log V)
     *   — Each edge is relaxed at most once: O(E log V)
     *
     * @param fromNodeId ID of the node that was clicked in GridUI
     * @return ordered list of Nodes from source to nearest CityZone; empty if no path
     */
    public List<Node> runDijkstra(int fromNodeId) {
        // dist[id] = minimum cost found so far to reach node with this id
        Map<Integer, Integer> dist = new HashMap<>();
        // prev[id] = predecessor node id on the cheapest path
        Map<Integer, Integer> prev = new HashMap<>();

        // Initialise all distances to infinity
        for (Node n : graph.getNodes()) {
            dist.put(n.id, Integer.MAX_VALUE);
        }
        dist.put(fromNodeId, 0);

        // Min-heap: int[] {cost, nodeId} — ordered by cost (index 0)
        PriorityQueue<int[]> heap = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        heap.offer(new int[]{0, fromNodeId});

        int targetId = -1; // ID of the nearest CityZone found

        while (!heap.isEmpty()) {
            int[] top = heap.poll();
            int curCost = top[0];
            int curId   = top[1];

            // Skip stale entries (a shorter path was already found)
            if (curCost > dist.get(curId)) continue;

            Node curNode = graph.getNode(curId);
            if (curNode == null || curNode.status == NodeStatus.ISOLATED) continue;

            // If we've reached a CityZone, this is the nearest one — stop
            if (curNode.type == NodeType.CITY_ZONE) {
                targetId = curId;
                break;
            }

            // Relax all outgoing edges from the current node
            for (Edge e : graph.getNeighbors(curId)) {
                Node neighbor = graph.getNode(e.to);
                if (neighbor == null || neighbor.status == NodeStatus.ISOLATED) continue;

                int newCost = dist.get(curId) + e.cost;
                if (newCost < dist.getOrDefault(e.to, Integer.MAX_VALUE)) {
                    dist.put(e.to, newCost);
                    prev.put(e.to, curId);
                    heap.offer(new int[]{newCost, e.to});
                }
            }
        }

        // Reconstruct path by walking backwards from target through predecessor map
        dijkstraPath = new ArrayList<>();
        if (targetId == -1) return dijkstraPath; // no path found

        int cur = targetId;
        while (cur != fromNodeId) {
            dijkstraPath.add(0, graph.getNode(cur)); // prepend to build forward order
            cur = prev.get(cur);
        }
        dijkstraPath.add(0, graph.getNode(fromNodeId));

        return dijkstraPath;
    }

    // -------------------------------------------------------------------------
    // Accessors for GridUI
    // -------------------------------------------------------------------------

    /** Returns current simulation metrics for the stats bar */
    public SimStats getStats() { return stats; }

    /** Returns the graph for GridUI rendering */
    public Graph getGraph() { return graph; }

    /** Returns the last Dijkstra path for GridUI path highlighting */
    public List<Node> getDijkstraPath() { return dijkstraPath; }

    /** Returns the set of node IDs that received flow in the last MCMF run */
    public Set<Integer> getLastFlowReceivers() { return lastFlowReceivers; }

    // -------------------------------------------------------------------------
    // Inner class: ResidualEdge
    // -------------------------------------------------------------------------

    /**
     * DSA Role: Edge in the ResidualGraph used internally by MCMF.
     * Stores remaining capacity and a back-pointer to the reverse edge index
     * so flow can be cancelled (backward edge augmentation).
     */
    private static class ResidualEdge {
        int to;   // destination node index (compact, not real ID)
        int cap;  // remaining capacity
        int cost; // cost per unit (negative for backward edges)
        int rev;  // index of the reverse edge in residual[to]

        ResidualEdge(int to, int cap, int cost, int rev) {
            this.to = to;
            this.cap = cap;
            this.cost = cost;
            this.rev = rev;
        }
    }
}
