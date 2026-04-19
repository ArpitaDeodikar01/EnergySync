package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * Min-Cost Max-Flow via SPFA (Bellman-Ford with queue) on a residual graph.
 *
 * Edge cost used for path selection is {@link Edge#scaledEffectiveCost()}, which
 * is computed from the composite formula:
 *   effectiveCost = w1*baseCost + w2*carbonCost + w3*failureRisk + w4*latency
 *
 * When default weights (1,0,0,0) are used, effectiveCost == baseCost == original
 * cost, so behavior is identical to the pre-multi-objective version.
 *
 * Time complexity: O(V * E * F)
 */
public class MCMFAlgorithm {

    private final Graph graph;
    private final CostWeights weights;

    private Set<Integer> lastFlowReceivers = new HashSet<>();

    /** Uses default weights — pure baseCost, identical to original behavior. */
    public MCMFAlgorithm(Graph graph) {
        this(graph, new CostWeights());
    }

    /** Uses the supplied weight vector for composite cost calculation. */
    public MCMFAlgorithm(Graph graph, CostWeights weights) {
        this.graph = graph;
        this.weights = weights;
    }

    /**
     * Runs MCMF and returns a result containing total flow, total cost,
     * and the set of CityZone node IDs that received flow.
     */
    public Result run(SimStats stats) {
        List<Node> activeNodes = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (n.status != NodeStatus.ISOLATED) activeNodes.add(n);
        }

        Map<Integer, Integer> idToIdx = new HashMap<>();
        int idx = 0;
        for (Node n : activeNodes) idToIdx.put(n.id, idx++);

        int superSrc = idx;
        int superSnk = idx + 1;
        int total = idx + 2;

        List<ResidualEdge>[] residual = buildResidualGraph(total, idToIdx, superSrc, superSnk, activeNodes);

        int totalFlow = 0;
        int totalCost = 0;
        lastFlowReceivers = new HashSet<>();

        while (true) {
            int[] dist     = new int[total];
            int[] prevNode = new int[total];
            int[] prevEdge = new int[total];
            Arrays.fill(dist, Integer.MAX_VALUE);
            Arrays.fill(prevNode, -1);
            Arrays.fill(prevEdge, -1);
            boolean[] inQueue = new boolean[total];

            dist[superSrc] = 0;
            Queue<Integer> queue = new LinkedList<>();
            queue.offer(superSrc);
            inQueue[superSrc] = true;

            while (!queue.isEmpty()) {
                int u = queue.poll();
                inQueue[u] = false;
                for (int i = 0; i < residual[u].size(); i++) {
                    ResidualEdge e = residual[u].get(i);
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

            if (dist[superSnk] == Integer.MAX_VALUE) break;

            // Find bottleneck
            int flow = Integer.MAX_VALUE;
            int cur = superSnk;
            while (cur != superSrc) {
                int prev = prevNode[cur];
                flow = Math.min(flow, residual[prev].get(prevEdge[cur]).cap);
                cur = prev;
            }

            // Augment
            cur = superSnk;
            while (cur != superSrc) {
                int prev = prevNode[cur];
                ResidualEdge fwd = residual[prev].get(prevEdge[cur]);
                fwd.cap -= flow;
                residual[cur].get(fwd.rev).cap += flow;

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

            totalFlow += flow;
            totalCost += flow * dist[superSnk];
        }

        stats.totalEnergyRouted = totalFlow;
        stats.totalCost = totalCost;

        return new Result(totalFlow, totalCost, lastFlowReceivers);
    }

    public Set<Integer> getLastFlowReceivers() {
        return lastFlowReceivers;
    }

    @SuppressWarnings("unchecked")
    private List<ResidualEdge>[] buildResidualGraph(int total, Map<Integer, Integer> idToIdx,
                                                     int superSrc, int superSnk,
                                                     List<Node> activeNodes) {
        List<ResidualEdge>[] res = new ArrayList[total];
        for (int i = 0; i < total; i++) res[i] = new ArrayList<>();

        for (Node n : activeNodes) {
            if (!idToIdx.containsKey(n.id)) continue;
            int u = idToIdx.get(n.id);
            for (Edge e : graph.getNeighbors(n.id)) {
                if (!idToIdx.containsKey(e.to)) continue;
                int v = idToIdx.get(e.to);
                int fwdIdx = res[u].size();
                int bwdIdx = res[v].size();
                int edgeCost = e.scaledEffectiveCost();
                res[u].add(new ResidualEdge(v, e.capacity, edgeCost, bwdIdx));
                res[v].add(new ResidualEdge(u, 0, -edgeCost, fwdIdx));
            }
        }

        for (Node n : activeNodes) {
            if (n.type == NodeType.ENERGY_SOURCE && n.status == NodeStatus.ACTIVE) {
                int v = idToIdx.get(n.id);
                int fwdIdx = res[superSrc].size();
                int bwdIdx = res[v].size();
                res[superSrc].add(new ResidualEdge(v, Integer.MAX_VALUE / 2, 0, bwdIdx));
                res[v].add(new ResidualEdge(superSrc, 0, 0, fwdIdx));
            }
        }

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

    /** Result returned by run() */
    public static class Result {
        public final int totalFlow;
        public final int totalCost;
        public final Set<Integer> flowReceivers;

        public Result(int totalFlow, int totalCost, Set<Integer> flowReceivers) {
            this.totalFlow = totalFlow;
            this.totalCost = totalCost;
            this.flowReceivers = flowReceivers;
        }
    }
}
