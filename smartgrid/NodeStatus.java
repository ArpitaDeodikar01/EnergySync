package smartgrid;

/**
 * DSA Role: State machine for Graph vertices during simulation lifecycle.
 * Drives GridUI color rendering and controls which nodes participate
 * in MCMF / Dijkstra computations after a fault event.
 */
public enum NodeStatus {
    /** Node is operational and participates in all algorithms */
    ACTIVE,
    /** CityZone whose supply path passed through a faulted node (detected by BFS) */
    FAULT,
    /** Node is part of the faulty subgraph (detected by DFS); excluded from routing */
    ISOLATED,
    /** CityZone that received flow in the post-fault MCMF re-run */
    REROUTED
}
