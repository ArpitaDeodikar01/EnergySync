package smartgrid;

/**
 * DSA Role: Directed weighted edge in the energy network Graph.
 * Represents a transmission line between two nodes with a maximum
 * capacity (flow limit) and a cost per unit of energy transmitted.
 * Used by MCMF for flow augmentation, Dijkstra for cost-based path finding,
 * and GridUI for drawing directed arrows on the canvas.
 */
public class Edge {

    /** ID of the source Node (tail of the directed edge) */
    public final int from;

    /** ID of the destination Node (head of the directed edge) */
    public final int to;

    /** Maximum energy units this transmission line can carry */
    public final int capacity;

    /** Cost per unit of energy transmitted along this line */
    public final int cost;

    /**
     * Constructs a directed edge from {@code from} to {@code to}.
     *
     * @param from     source node ID
     * @param to       destination node ID
     * @param capacity maximum flow capacity
     * @param cost     cost per unit of flow
     */
    public Edge(int from, int to, int capacity, int cost) {
        this.from = from;
        this.to = to;
        this.capacity = capacity;
        this.cost = cost;
    }

    @Override
    public String toString() {
        return from + "->" + to + "(cap=" + capacity + ", cost=" + cost + ")";
    }
}
