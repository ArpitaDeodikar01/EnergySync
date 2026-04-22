package smartgrid.services;

import smartgrid.model.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks per-edge performance and adaptively updates edge costs after each run.
 *
 * Cost update formula:
 *   newBaseCost = oldBaseCost + alpha * failureRate - beta * efficiencyScore
 *
 * The result is clamped to [MIN_COST, MAX_COST] so costs never go negative
 * or grow unbounded.
 *
 * After updating baseCost and failureRisk on each edge, the caller is
 * responsible for triggering recomputeEffectiveCosts() on the engine.
 */
public class WeightUpdateService {

    /** Learning rate for failure penalty — how strongly failures raise cost */
    private double alpha;

    /** Learning rate for efficiency reward — how strongly good performance lowers cost */
    private double beta;

    private static final double MIN_COST = 0.1;
    private static final double MAX_COST = 100.0;

    /**
     * Per-edge performance records keyed by "from,to" string.
     * Persists across runs so history accumulates.
     */
    private final Map<String, EdgePerformanceRecord> records = new HashMap<>();

    /** Default learning rates */
    public WeightUpdateService() {
        this.alpha = 0.3;
        this.beta  = 0.1;
    }

    public WeightUpdateService(double alpha, double beta) {
        this.alpha = alpha;
        this.beta  = beta;
    }

    // -------------------------------------------------------------------------
    // Observation recording
    // -------------------------------------------------------------------------

    /**
     * Records one observation for an edge after a simulation run.
     * Updates efficiency, congestion, and failure rate in the performance record.
     *
     * @param edge        the edge being observed
     * @param flowOnEdge  flow that passed through this edge in the last run
     * @param failed      true if this edge was involved in a fault this run
     */
    public void recordObservation(Edge edge, int flowOnEdge, boolean failed) {
        String key = edge.from + "," + edge.to;
        EdgePerformanceRecord rec = records.computeIfAbsent(key,
                k -> new EdgePerformanceRecord(edge.from, edge.to));

        rec.observationCount++;

        // Efficiency: ratio of actual flow to capacity (0 if capacity is 0)
        double efficiency = edge.capacity > 0
                ? Math.min(1.0, (double) flowOnEdge / edge.capacity)
                : 0.0;

        // Running average of efficiency
        rec.efficiencyScore = runningAverage(rec.efficiencyScore, efficiency, rec.observationCount);

        // Congestion: flag when edge is >= 80% saturated
        double congestion = (edge.capacity > 0 && flowOnEdge >= 0.8 * edge.capacity) ? 1.0 : 0.0;
        rec.congestionLevel = runningAverage(rec.congestionLevel, congestion, rec.observationCount);

        if (failed) rec.failureCount++;
        rec.failureRate = (double) rec.failureCount / rec.observationCount;
    }

    // -------------------------------------------------------------------------
    // Cost update
    // -------------------------------------------------------------------------

    /**
     * Applies the adaptive cost formula to every edge that has a performance record.
     * Also updates edge.failureRisk from the recorded failure rate.
     *
     * newBaseCost = clamp(oldBaseCost + alpha*failureRate - beta*efficiencyScore)
     *
     * @param edges all edges currently in the graph
     */
    public void updateEdgeCosts(Collection<Edge> edges) {
        for (Edge edge : edges) {
            String key = edge.from + "," + edge.to;
            EdgePerformanceRecord rec = records.get(key);
            if (rec == null) continue; // no observations yet — leave cost unchanged

            double newCost = edge.baseCost
                    + alpha * rec.failureRate
                    - beta  * rec.efficiencyScore;

            edge.baseCost    = clamp(newCost, MIN_COST, MAX_COST);
            edge.failureRisk = rec.failureRate;   // keep failureRisk in sync
        }
    }

    // -------------------------------------------------------------------------
    // Convenience: observe all edges from an adjacency list then update costs
    // -------------------------------------------------------------------------

    /**
     * Single-call helper used by SimulationEngine after each MCMF run.
     * Records observations for all edges (using edge.flow set by MCMF),
     * then applies the cost update formula.
     *
     * @param adjList    adjacency list from Graph.getAdjList()
     * @param faultedIds set of node IDs that were faulted this run (may be empty)
     */
    public void processRun(Map<Integer, List<Edge>> adjList, java.util.Set<Integer> faultedIds) {
        // Collect all edges
        for (List<Edge> edgeList : adjList.values()) {
            for (Edge e : edgeList) {
                boolean edgeFailed = faultedIds.contains(e.from) || faultedIds.contains(e.to);
                recordObservation(e, e.flow, edgeFailed);
            }
        }
        // Apply cost updates
        for (List<Edge> edgeList : adjList.values()) {
            updateEdgeCosts(edgeList);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public EdgePerformanceRecord getRecord(int from, int to) {
        return records.get(from + "," + to);
    }

    public Map<String, EdgePerformanceRecord> getAllRecords() {
        return java.util.Collections.unmodifiableMap(records);
    }

    public double getAlpha() { return alpha; }
    public double getBeta()  { return beta; }

    public void setAlpha(double alpha) { this.alpha = alpha; }
    public void setBeta(double beta)   { this.beta = beta; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Incremental running average: avg_n = avg_{n-1} + (newVal - avg_{n-1}) / n */
    private double runningAverage(double currentAvg, double newValue, int n) {
        return currentAvg + (newValue - currentAvg) / n;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
