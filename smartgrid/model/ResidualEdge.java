package smartgrid.model;

/**
 * Edge in the residual graph used internally by MCMF.
 * Stores remaining capacity and a back-pointer to the reverse edge index
 * so flow can be cancelled (backward edge augmentation).
 * Also holds a reference to the real Edge so flow can be written back
 * to the graph after augmentation (null for backward/virtual edges).
 */
public class ResidualEdge {

    public int to;       // destination node index (compact, not real ID)
    public int cap;      // remaining capacity
    public int cost;     // cost per unit (negative for backward edges)
    public int rev;      // index of the reverse edge in residual[to]
    public Edge realEdge; // reference to the original graph Edge (null for backward/virtual edges)

    /** Constructor without real edge reference — for backward and virtual edges */
    public ResidualEdge(int to, int cap, int cost, int rev) {
        this(to, cap, cost, rev, null);
    }

    /** Constructor with real edge reference — for forward edges mapped from the graph */
    public ResidualEdge(int to, int cap, int cost, int rev, Edge realEdge) {
        this.to = to;
        this.cap = cap;
        this.cost = cost;
        this.rev = rev;
        this.realEdge = realEdge;
    }
}
