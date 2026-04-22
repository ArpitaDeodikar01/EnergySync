package smartgrid.algorithms;

import smartgrid.model.*;

import java.util.*;

/**
 * DFS faulty subgraph isolation.
 * Traverses forward-directed edges from the faulted node and marks
 * every visited node as ISOLATED — BUT only if that node has no
 * alternate supply path from a remaining active EnergySource.
 *
 * Why: a node reachable from the faulted source may also be reachable
 * from Wind or Hydro. Marking it ISOLATED would wrongly cut it off from
 * rerouting. We only truly isolate nodes that are EXCLUSIVELY fed by
 * the faulted source.
 *
 * Time complexity: O(V + E)
 */
public class DFSAlgorithm {

    private final Graph graph;

    public DFSAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Runs DFS from {@code faultedNodeId}, marking reachable nodes ISOLATED
     * only when they have no alternate path from a surviving active EnergySource.
     *
     * @param faultedNodeId the node that just failed (already removed from graph
     *                      or marked ISOLATED by the caller before this runs)
     */
    public void run(int faultedNodeId) {
        // Step 1: collect all nodes reachable from the faulted node via DFS
        Set<Integer> reachableFromFault = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(faultedNodeId);

        while (!stack.isEmpty()) {
            int cur = stack.pop();
            if (reachableFromFault.contains(cur)) continue;
            reachableFromFault.add(cur);
            for (Edge e : graph.getNeighbors(cur)) {
                if (!reachableFromFault.contains(e.to)) stack.push(e.to);
            }
        }

        // Step 2: collect all nodes reachable from ANY other active EnergySource
        // These nodes still have a live supply path — do NOT isolate them
        Set<Integer> reachableFromSurvivors = new HashSet<>();
        for (Node n : graph.getNodes()) {
            if (n.type == NodeType.ENERGY_SOURCE
                    && n.status == NodeStatus.ACTIVE
                    && n.id != faultedNodeId) {
                // BFS/DFS from this surviving source
                Deque<Integer> q = new ArrayDeque<>();
                q.push(n.id);
                while (!q.isEmpty()) {
                    int cur = q.pop();
                    if (reachableFromSurvivors.contains(cur)) continue;
                    reachableFromSurvivors.add(cur);
                    for (Edge e : graph.getNeighbors(cur)) {
                        if (!reachableFromSurvivors.contains(e.to)) q.push(e.to);
                    }
                }
            }
        }

        // Step 3: mark ISOLATED only nodes that are reachable from the fault
        // but NOT reachable from any surviving source
        // — these are truly cut off and should be excluded from routing
        for (int id : reachableFromFault) {
            if (id == faultedNodeId) continue; // already marked by caller
            if (!reachableFromSurvivors.contains(id)) {
                Node n = graph.getNode(id);
                if (n != null) n.status = NodeStatus.ISOLATED;
            }
            // If reachable from a survivor, leave status as-is (ACTIVE or FAULT)
            // autoReroute will set it to REROUTED once flow reaches it
        }
    }
}
