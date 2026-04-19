package smartgrid.model;

/**
 * Vertex in the weighted directed Graph representing the energy network.
 * Implements Comparable so Node objects can be inserted into a PriorityQueue
 * ordered by cost (MinHeap for load balancing).
 *
 * Extended fields (capacity, currentLoad, failureProbability, reliabilityScore,
 * carbonFactor) prepare the model for multi-objective optimization without
 * affecting existing algorithm behavior.
 */
public class Node implements Comparable<Node> {

    public final int id;
    public final String label;
    public final NodeType type;
    public NodeStatus status;
    public int x;
    public int y;

    /** Cost per unit — used by MinHeap and MCMF. Unchanged from original. */
    public int cost;

    // -------------------------------------------------------------------------
    // Extended fields — defaults keep existing behavior intact
    // -------------------------------------------------------------------------

    /** Maximum energy units this node can handle (0 = unconstrained) */
    public int capacity;

    /** Energy units currently flowing through this node */
    public int currentLoad;

    /** Probability [0.0, 1.0] that this node fails under stress */
    public double failureProbability;

    /**
     * Reliability score [0.0, 1.0] — higher is more reliable.
     * 1.0 = fully reliable (default), 0.0 = completely unreliable.
     */
    public double reliabilityScore;

    /**
     * Carbon emission factor per unit of energy (kg CO₂ per unit).
     * 0.0 for non-source nodes; set per source type in topology.
     */
    public double carbonFactor;

    /**
     * Original constructor — all extended fields get safe defaults.
     * Existing call sites in SimulationEngine are unaffected.
     */
    public Node(int id, String label, NodeType type, int x, int y, int cost) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.status = NodeStatus.ACTIVE;
        this.x = x;
        this.y = y;
        this.cost = cost;

        // Safe defaults — preserve existing behavior
        this.capacity = 0;
        this.currentLoad = 0;
        this.failureProbability = 0.0;
        this.reliabilityScore = 1.0;
        this.carbonFactor = 0.0;
    }

    @Override
    public int compareTo(Node other) {
        return Integer.compare(this.cost, other.cost);
    }

    @Override
    public String toString() {
        return label + "(id=" + id + ", " + status + ")";
    }
}
