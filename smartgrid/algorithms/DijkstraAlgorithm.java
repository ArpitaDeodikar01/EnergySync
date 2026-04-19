package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * Dijkstra shortest path from a given node to the nearest reachable CityZone.
 * Skips ISOLATED nodes. Uses a min-heap PriorityQueue for O(log V) extraction.
 *
 * Edge cost used for relaxation is {@link Edge#scaledEffectiveCost()}, derived from:
 *   effectiveCost = w1*baseCost + w2*carbonCost + w3*failureRisk + w4*latency
 *
 * Default weights (1,0,0,0) reproduce original single-objective behavior.
 *
 * Time complexity: O((V + E) log V)
 */
public class DijkstraAlgorithm {

    private final Graph graph;
    private final CostWeights weights;

    /** Uses default weights — pure baseCost, identical to original behavior. */
    public DijkstraAlgorithm(Graph graph) {
        this(graph, new CostWeights());
    }

    /** Uses the supplied weight vector for composite cost calculation. */
    public DijkstraAlgorithm(Graph graph, CostWeights weights) {
        this.graph = graph;
        this.weights = weights;
    }

    /**
     * Finds the minimum-effectiveCost path from {@code fromNodeId} to the nearest CityZone.
     * @return ordered list of Nodes from source to target; empty if no path found
     */
    public List<Node> run(int fromNodeId) {
        // Use long to avoid overflow when COST_SCALE is applied
        Map<Integer, Long> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();

        for (Node n : graph.getNodes()) dist.put(n.id, Long.MAX_VALUE);
        dist.put(fromNodeId, 0L);

        // Min-heap: long[] {scaledCost, nodeId}
        PriorityQueue<long[]> heap = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        heap.offer(new long[]{0L, fromNodeId});

        int targetId = -1;

        while (!heap.isEmpty()) {
            long[] top = heap.poll();
            long curCost = top[0];
            int  curId   = (int) top[1];

            if (curCost > dist.get(curId)) continue;

            Node curNode = graph.getNode(curId);
            if (curNode == null || curNode.status == NodeStatus.ISOLATED) continue;

            if (curNode.type == NodeType.CITY_ZONE) {
                targetId = curId;
                break;
            }

            for (Edge e : graph.getNeighbors(curId)) {
                Node neighbor = graph.getNode(e.to);
                if (neighbor == null || neighbor.status == NodeStatus.ISOLATED) continue;

                long newCost = dist.get(curId) + e.scaledEffectiveCost();
                if (newCost < dist.getOrDefault(e.to, Long.MAX_VALUE)) {
                    dist.put(e.to, newCost);
                    prev.put(e.to, curId);
                    heap.offer(new long[]{newCost, e.to});
                }
            }
        }

        List<Node> path = new ArrayList<>();
        if (targetId == -1) return path;

        int cur = targetId;
        while (cur != fromNodeId) {
            path.add(0, graph.getNode(cur));
            cur = prev.get(cur);
        }
        path.add(0, graph.getNode(fromNodeId));

        return path;
    }
}
