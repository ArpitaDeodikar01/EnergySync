package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * Topological Sort via Kahn's Algorithm (BFS-based, in-degree counting).
 *
 * ONLY triggered when Bellman-Ford detects a stress cycle.
 *
 * Load shedding priority (spec-exact):
 *   Commercial → Residential-4 → Residential-3 → Industrial → Hospital
 *   Hospital (Zone-1) NEVER appears in shed sequence.
 *
 * Time complexity: O(V + E)
 */
public class TopologicalSort {

    private final Graph graph;

    // Shedding priority: lower number = shed first
    private static final Map<ZoneClassification, Integer> SHED_PRIORITY = new HashMap<>();
    static {
        SHED_PRIORITY.put(ZoneClassification.COMMERCIAL,   1);
        SHED_PRIORITY.put(ZoneClassification.RESIDENTIAL,  2);
        SHED_PRIORITY.put(ZoneClassification.INDUSTRY,     3);
        SHED_PRIORITY.put(ZoneClassification.UNCLASSIFIED, 4);
        SHED_PRIORITY.put(ZoneClassification.HOSPITAL,     Integer.MAX_VALUE); // never shed
    }

    public static class Result {
        public final List<Node> topoOrder;       // full topological order
        public final boolean hasCycle;
        public final List<Node> sheddingOrder;   // zones to shed, Hospital excluded
        public final String summary;

        public Result(List<Node> topoOrder, boolean hasCycle,
                      List<Node> sheddingOrder, String summary) {
            this.topoOrder    = topoOrder;
            this.hasCycle     = hasCycle;
            this.sheddingOrder = sheddingOrder;
            this.summary      = summary;
        }
    }

    public TopologicalSort(Graph graph) {
        this.graph = graph;
    }

    public Result run() {
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (Node n : graph.getNodes()) {
            if (n.status != NodeStatus.ISOLATED) inDegree.put(n.id, 0);
        }
        for (Node n : graph.getNodes()) {
            if (n.status == NodeStatus.ISOLATED) continue;
            for (Edge e : graph.getNeighbors(n.id)) {
                Node dst = graph.getNode(e.to);
                if (dst != null && dst.status != NodeStatus.ISOLATED) {
                    inDegree.merge(e.to, 1, Integer::sum);
                }
            }
        }

        Queue<Integer> queue = new LinkedList<>();
        for (Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.offer(entry.getKey());
        }

        List<Node> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            Node curNode = graph.getNode(cur);
            if (curNode != null) order.add(curNode);
            for (Edge e : graph.getNeighbors(cur)) {
                Node dst = graph.getNode(e.to);
                if (dst == null || dst.status == NodeStatus.ISOLATED) continue;
                int newDeg = inDegree.merge(e.to, -1, Integer::sum);
                if (newDeg == 0) queue.offer(e.to);
            }
        }

        boolean hasCycle = order.size() < inDegree.size();

        // Build shedding order: zones only, sorted by SHED_PRIORITY, Hospital excluded
        List<Node> zones = new ArrayList<>();
        for (Node n : order) {
            if (n.type == NodeType.CITY_ZONE) {
                ZoneClassification zc = n.zoneClass != null ? n.zoneClass : ZoneClassification.UNCLASSIFIED;
                if (zc != ZoneClassification.HOSPITAL) zones.add(n);
            }
        }
        zones.sort((a, b) -> {
            ZoneClassification za = a.zoneClass != null ? a.zoneClass : ZoneClassification.UNCLASSIFIED;
            ZoneClassification zb = b.zoneClass != null ? b.zoneClass : ZoneClassification.UNCLASSIFIED;
            return Integer.compare(
                SHED_PRIORITY.getOrDefault(za, 99),
                SHED_PRIORITY.getOrDefault(zb, 99)
            );
        });

        StringBuilder sb = new StringBuilder("Shed order: ");
        for (int i = 0; i < zones.size(); i++) {
            sb.append(zones.get(i).label);
            if (i < zones.size() - 1) sb.append(" → ");
        }
        if (!zones.isEmpty()) sb.append(" (Hospital protected)");

        return new Result(order, hasCycle, zones, sb.toString());
    }
}
