package smartgrid.model;

/**
 * State machine for Graph vertices during simulation lifecycle.
 * Drives GridUI color rendering and controls which nodes participate
 * in MCMF / Dijkstra computations after a fault event.
 */
public enum NodeStatus {
    ACTIVE,
    FAULT,
    ISOLATED,
    REROUTED
}
