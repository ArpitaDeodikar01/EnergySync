package smartgrid;

/**
 * DSA Role: Vertex in the weighted directed Graph representing the energy network.
 * Nodes are classified by NodeType (EnergySource / Substation / CityZone) and
 * carry a mutable NodeStatus that drives fault detection, rerouting, and UI rendering.
 * EnergySources also carry a cost field used by the MinHeap for load balancing (Req 6).
 *
 * Implements Comparable so Node objects can be inserted directly into a
 * PriorityQueue<Node> ordered by cost — the MinHeap used in GridSimulator.
 */
public class Node implements Comparable<Node> {

    /** Unique integer ID assigned by Graph on creation */
    public final int id;

    /** Human-readable display label (e.g. "Solar", "Zone-1") */
    public final String label;

    /** Classifies this node as EnergySource, Substation, or CityZone */
    public final NodeType type;

    /** Mutable simulation state; drives UI color and algorithm inclusion */
    public NodeStatus status;

    /** Canvas X coordinate for GridUI rendering */
    public int x;

    /** Canvas Y coordinate for GridUI rendering */
    public int y;

    /**
     * Cost per unit of energy — meaningful only for ENERGY_SOURCE nodes.
     * Used as the MinHeap key so the cheapest source is always served first.
     */
    public int cost;

    /**
     * Constructs a Node with all fields.
     *
     * @param id     unique identifier assigned by Graph
     * @param label  display name
     * @param type   NodeType classification
     * @param x      canvas x coordinate
     * @param y      canvas y coordinate
     * @param cost   cost per unit (EnergySources); 0 for others
     */
    public Node(int id, String label, NodeType type, int x, int y, int cost) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.status = NodeStatus.ACTIVE; // all nodes start active
        this.x = x;
        this.y = y;
        this.cost = cost;
    }

    /**
     * Natural ordering by cost — required for PriorityQueue<Node> MinHeap.
     * Lower cost = higher priority (min-heap semantics).
     * Time complexity: O(1)
     */
    @Override
    public int compareTo(Node other) {
        return Integer.compare(this.cost, other.cost);
    }

    @Override
    public String toString() {
        return label + "(id=" + id + ", " + status + ")";
    }
}
