package smartgrid.model;

/**
 * Directed weighted edge in the energy network Graph.
 * Represents a transmission line between two nodes.
 *
 * The original {@code capacity} and {@code cost} fields are preserved unchanged
 * so MCMF, Dijkstra, and GraphCanvas continue to work without modification.
 *
 * Extended fields (flow, baseCost, carbonCost, latency, failureRisk, effectiveCost)
 * support multi-objective optimization via {@link #computeEffectiveCost(CostWeights)}.
 *
 * Algorithms that need an integer cost use {@link #scaledEffectiveCost()} which
 * multiplies effectiveCost by COST_SCALE to preserve two decimal places of precision.
 */
public class Edge {

    /**
     * Scaling factor applied when converting effectiveCost (double) to an integer
     * for use inside the residual graph. Preserves two decimal places of precision.
     * Both MCMF and Dijkstra use scaledEffectiveCost() consistently, so relative
     * ordering between edges is maintained.
     */
    public static final int COST_SCALE = 100;

    public final int from;
    public final int to;

    /** Maximum flow capacity — used by MCMF. Unchanged from original. */
    public final int capacity;

    /**
     * Cost per unit — used by MCMF and Dijkstra. Unchanged from original.
     * Mirrors baseCost; kept as a separate field so no algorithm call sites break.
     */
    public final int cost;

    // -------------------------------------------------------------------------
    // Extended fields — defaults keep existing behavior intact
    // -------------------------------------------------------------------------

    /** Current flow on this edge (updated after each MCMF run) */
    public int flow;

    /** Base economic cost per unit of energy transmitted */
    public double baseCost;

    /** Carbon emission cost per unit of energy transmitted (kg CO₂ per unit) */
    public double carbonCost;

    /** Transmission latency in milliseconds */
    public double latency;

    /** Probability [0.0, 1.0] that this edge fails under load */
    public double failureRisk;

    /**
     * Composite cost for multi-objective routing.
     * Formula (to be applied by optimizer): baseCost + carbonCost + latency penalty
     * Defaults to the same value as {@code cost} so existing behavior is unchanged.
     */
    public double effectiveCost;

    /**
     * Original constructor — all extended fields get safe defaults.
     * Existing call sites in Graph.addEdge() are unaffected.
     */
    public Edge(int from, int to, int capacity, int cost) {
        this.from = from;
        this.to = to;
        this.capacity = capacity;
        this.cost = cost;

        // Safe defaults — preserve existing behavior
        this.flow = 0;
        this.baseCost = cost;        // mirrors cost so effectiveCost is consistent
        this.carbonCost = 0.0;
        this.latency = 0.0;
        this.failureRisk = 0.0;
        this.effectiveCost = cost;   // same as cost until optimizer sets it
    }

    /**
     * Recomputes and stores effectiveCost using the supplied weight vector.
     * Call this on every edge before running MCMF or Dijkstra whenever weights change.
     *
     * effectiveCost = w1*baseCost + w2*carbonCost + w3*failureRisk + w4*latency
     */
    public void computeEffectiveCost(CostWeights w) {
        this.effectiveCost = w.w1 * baseCost
                           + w.w2 * carbonCost
                           + w.w3 * failureRisk
                           + w.w4 * latency;
    }

    /**
     * Returns effectiveCost scaled to an integer for use in residual graph arithmetic.
     * Scaling preserves relative ordering so algorithm correctness is unchanged.
     */
    public int scaledEffectiveCost() {
        return (int) Math.round(effectiveCost * COST_SCALE);
    }

    @Override
    public String toString() {
        return from + "->" + to + "(cap=" + capacity + ", cost=" + cost + ")";
    }
}
