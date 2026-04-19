package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * BFS fault impact detection.
 * Finds CityZones that are TRULY cut off after a fault — i.e. zones that
 * were exclusively supplied through the faulted node and have no alternate
 * path from any surviving active EnergySource.
 *
 * Why this matters: a zone like Zone-2 may be reachable via both
 * Solar→Sub-A and Wind→Sub-B. Losing Solar should NOT mark Zone-2 as FAULT
 * because Wind can still supply it via Sub-B.
 *
 * Time complexity: O(V + E)
 */
public class BFSAlgorithm {

    private final Graph graph;

    public BFSAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Runs BFS from {@code faultedNodeId} to find affected CityZones.
     * Only marks a zone FAULT if it is NOT reachable from any surviving
     * active EnergySource.
     *
     * @return number of CityZones marked FAULT (truly isolated zones)
     */
    public int run(int faultedNodeId) {
        // Step 1: find all CityZones reachable from the faulted node
        Set<Integer> reachableFromFault = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(faultedNodeId);
        reachableFromFault.add(faultedNodeId);

        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (Edge e : graph.getNeighbors(cur)) {
                if (!reachableFromFault.contains(e.to)) {
                    reachableFromFault.add(e.to);
                    queue.offer(e.to);
                }
            }
        }

        // Step 2: find all nodes reachable from surviving active EnergySources
        Set<Integer> reachableFromSurvivors = new HashSet<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.ENERGY_SOURCE
                    && n.status == NodeStatus.ACTIVE
                    && n.id != faultedNodeId) {
                Queue<Integer> q = new LinkedList<>();
                q.offer(n.id);
                reachableFromSurvivors.add(n.id);
                while (!q.isEmpty()) {
                    int cur = q.poll();
                    for (Edge e : graph.getNeighbors(cur)) {
                        if (!reachableFromSurvivors.contains(e.to)) {
                            reachableFromSurvivors.add(e.to);
                            q.offer(e.to);
                        }
                    }
                }
            }
        }

        // Step 3: mark FAULT only zones that lost their ONLY supply path
        int affectedZones = 0;
        for (int id : reachableFromFault) {
            Node n = graph.getNode(id);
            if (n != null && n.type == NodeType.CITY_ZONE
                    && !reachableFromSurvivors.contains(id)) {
                n.status = NodeStatus.FAULT;
                affectedZones++;
            }
        }

        return affectedZones;
    }
}
