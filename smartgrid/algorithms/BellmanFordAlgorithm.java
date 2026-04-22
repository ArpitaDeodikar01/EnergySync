package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * Bellman-Ford with negative-cycle detection for GRID STRESS ANALYSIS.
 *
 * Use case (why not a threshold check):
 *   A threshold check tells you one edge is overloaded.
 *   Bellman-Ford tells you a CYCLE of mutually-overloaded edges exists —
 *   a cascading failure loop that a threshold check cannot detect.
 *   That distinction is why Bellman-Ford belongs here.
 *
 * Stress cost assignment:
 *   Edges at >90% capacity get cost = -(overcapacity_amount).
 *   If a negative-cost cycle exists in the residual graph, it means
 *   a stress loop is present → automatically triggers Topological Sort
 *   for load shedding.
 *
 * Time complexity: O(V * E)
 */
public class BellmanFordAlgorithm {

    private final Graph graph;

    public static class Result {
        public final Map<Integer, Double> dist;
        public final Map<Integer, Integer> prev;
        public final boolean hasNegativeCycle;
        public final List<Integer> stressedNodeIds; // nodes in negative cycle
        public final String summary;

        public Result(Map<Integer, Double> dist, Map<Integer, Integer> prev,
                      boolean hasNegativeCycle, List<Integer> stressedNodeIds, String summary) {
            this.dist = dist;
            this.prev = prev;
            this.hasNegativeCycle = hasNegativeCycle;
            this.stressedNodeIds = stressedNodeIds;
            this.summary = summary;
        }
    }

    public BellmanFordAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Runs Bellman-Ford from {@code sourceId} using STRESS costs:
     *   - Edges at >90% capacity: cost = -(flow - 0.9*capacity)  [negative = stressed]
     *   - All other edges: cost = effectiveCost (normal)
     *
     * A negative cycle means a loop of stressed edges exists → cascade risk.
     */
    public Result run(int sourceId) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();

        for (Node n : graph.getNodes()) {
            dist.put(n.id, Double.MAX_VALUE / 2);
            prev.put(n.id, -1);
        }
        dist.put(sourceId, 0.0);

        // Build stress-adjusted edge list
        List<Edge> allEdges = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (n.status != NodeStatus.ISOLATED) {
                for (Edge e : graph.getNeighbors(n.id)) {
                    allEdges.add(e);
                }
            }
        }

        int V = graph.getNodes().size();

        // Relax V-1 times using stress costs
        for (int i = 0; i < V - 1; i++) {
            boolean updated = false;
            for (Edge e : allEdges) {
                Node src = graph.getNode(e.from);
                Node dst = graph.getNode(e.to);
                if (src == null || dst == null) continue;
                if (src.status == NodeStatus.ISOLATED || dst.status == NodeStatus.ISOLATED) continue;
                if (dist.get(e.from) >= Double.MAX_VALUE / 2) continue;

                double stressCost = stressCost(e);
                double newDist = dist.get(e.from) + stressCost;
                if (newDist < dist.get(e.to)) {
                    dist.put(e.to, newDist);
                    prev.put(e.to, e.from);
                    updated = true;
                }
            }
            if (!updated) break;
        }

        // V-th relaxation: detect negative cycles
        boolean hasNegativeCycle = false;
        List<Integer> stressedNodes = new ArrayList<>();
        for (Edge e : allEdges) {
            Node src = graph.getNode(e.from);
            Node dst = graph.getNode(e.to);
            if (src == null || dst == null) continue;
            if (src.status == NodeStatus.ISOLATED || dst.status == NodeStatus.ISOLATED) continue;
            if (dist.get(e.from) >= Double.MAX_VALUE / 2) continue;

            double stressCost = stressCost(e);
            if (dist.get(e.from) + stressCost < dist.get(e.to)) {
                hasNegativeCycle = true;
                if (!stressedNodes.contains(e.from)) stressedNodes.add(e.from);
                if (!stressedNodes.contains(e.to))   stressedNodes.add(e.to);
            }
        }

        String summary = hasNegativeCycle
            ? "Bellman-Ford: stress cycle detected → load shed initiated (" + stressedNodes.size() + " nodes)"
            : "Bellman-Ford: grid stable, no stress cycles";

        return new Result(dist, prev, hasNegativeCycle, stressedNodes, summary);
    }

    /**
     * Stress cost for an edge:
     *   >90% capacity → negative cost (signals stress)
     *   otherwise     → effectiveCost (normal routing cost)
     */
    private double stressCost(Edge e) {
        if (e.capacity > 0 && e.flow > 0.9 * e.capacity) {
            return -(e.flow - 0.9 * e.capacity); // negative = stressed
        }
        return e.effectiveCost;
    }

    public List<Integer> reconstructPath(Map<Integer, Integer> prev, int sourceId, int targetId) {
        List<Integer> path = new ArrayList<>();
        int cur = targetId;
        int safety = 0;
        while (cur != -1 && cur != sourceId && safety++ < 100) {
            path.add(0, cur);
            cur = prev.getOrDefault(cur, -1);
        }
        if (cur == sourceId) path.add(0, sourceId);
        else path.clear();
        return path;
    }
}
