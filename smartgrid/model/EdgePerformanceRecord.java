package smartgrid.model;

/**
 * Tracks observed performance metrics for a single edge over time.
 * Updated by WeightUpdateService after each simulation run.
 *
 * Fields are mutable so the service can accumulate values incrementally.
 */
public class EdgePerformanceRecord {

    /** Edge this record belongs to (from → to key) */
    public final int from;
    public final int to;

    /** Number of times this edge has been observed carrying flow */
    public int observationCount;

    /**
     * Cumulative efficiency score [0.0, 1.0].
     * efficiency = flow / capacity for each observation; averaged over time.
     * Higher = edge is being used well relative to its capacity.
     */
    public double efficiencyScore;

    /**
     * Congestion level [0.0, 1.0].
     * congestion = flow / capacity when flow >= 80% of capacity.
     * Tracks how often the edge is near saturation.
     */
    public double congestionLevel;

    /** Total number of failures recorded on this edge */
    public int failureCount;

    /**
     * Failure rate = failureCount / observationCount.
     * Recomputed by WeightUpdateService after each update.
     */
    public double failureRate;

    public EdgePerformanceRecord(int from, int to) {
        this.from = from;
        this.to = to;
        this.observationCount = 0;
        this.efficiencyScore = 1.0;  // assume perfect until observed otherwise
        this.congestionLevel = 0.0;
        this.failureCount = 0;
        this.failureRate = 0.0;
    }

    @Override
    public String toString() {
        return String.format("EdgePerf(%d->%d eff=%.2f fail=%.2f)",
                from, to, efficiencyScore, failureRate);
    }
}
