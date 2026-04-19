package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * DFS faulty subgraph isolation.
 * Traverses forward-directed edges from the faulted node and marks
 * every visited node as ISOLATED, excluding it from future routing.
 *
 * Time complexity: O(V + E)
 */
public class DFSAlgorithm {

    private final Graph graph;

    public DFSAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Runs DFS from {@code faultedNodeId}, marking all reachable nodes ISOLATED.
     */
    public void run(int faultedNodeId) {
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(faultedNodeId);

        while (!stack.isEmpty()) {
            int cur = stack.pop();
            if (visited.contains(cur)) continue;
            visited.add(cur);

            Node curNode = graph.getNode(cur);
            if (curNode != null) curNode.status = NodeStatus.ISOLATED;

            for (Edge e : graph.getNeighbors(cur)) {
                if (!visited.contains(e.to)) stack.push(e.to);
            }
        }
    }
}
