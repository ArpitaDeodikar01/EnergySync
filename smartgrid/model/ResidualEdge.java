package smartgrid.model;

/**
 * Edge in the residual graph used internally by MCMF.
 * Stores remaining capacity and a back-pointer to the reverse edge index
 * so flow can be cancelled (backward edge augmentation).
 */
public class ResidualEdge {

    public int to;   // destination node index (compact, not real ID)
    public int cap;  // remaining capacity
    public int cost; // cost per unit (negative for backward edges)
    public int rev;  // index of the reverse edge in residual[to]

    public ResidualEdge(int to, int cap, int cost, int rev) {
        this.to = to;
        this.cap = cap;
        this.cost = cost;
        this.rev = rev;
    }
}
