package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * BFS fault impact detection.
 * Traverses forward-directed edges from the faulted node and marks
 * every reachable CityZone as FAULT.
 *
 * Time complexity: O(V + E)
 */
public class BFSAlgorithm {

    private final Graph graph;

    public BFSAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Runs BFS from {@code faultedNodeId}.
     * @return number of CityZones marked FAULT
     */
    public int run(int faultedNodeId) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();

        queue.offer(faultedNodeId);
        visited.add(faultedNodeId);
        int affectedZones = 0;

        while (!queue.isEmpty()) {
            int cur = queue.poll();
            Node curNode = graph.getNode(cur);
            if (curNode != null && curNode.type == NodeType.CITY_ZONE) {
                curNode.status = NodeStatus.FAULT;
                affectedZones++;
            }
            for (Edge e : graph.getNeighbors(cur)) {
                if (!visited.contains(e.to)) {
                    visited.add(e.to);
                    queue.offer(e.to);
                }
            }
        }

        return affectedZones;
    }
}
