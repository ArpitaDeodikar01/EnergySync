package smartgrid.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed cost breakdown for a Dijkstra path.
 * Holds per-hop costs and totals for all 4 cost factors.
 * Displayed in the Path Analysis panel after any Dijkstra run.
 */
public class PathCostBreakdown {

    public static class HopCost {
        public final String fromLabel;
        public final String toLabel;
        public final double baseCost;
        public final double carbonCost;
        public final double failureRisk;
        public final double latency;
        public final double effectiveCost;
        public final int    flow;
        public final int    capacity;

        public HopCost(String fromLabel, String toLabel,
                       double baseCost, double carbonCost,
                       double failureRisk, double latency,
                       double effectiveCost, int flow, int capacity) {
            this.fromLabel    = fromLabel;
            this.toLabel      = toLabel;
            this.baseCost     = baseCost;
            this.carbonCost   = carbonCost;
            this.failureRisk  = failureRisk;
            this.latency      = latency;
            this.effectiveCost = effectiveCost;
            this.flow         = flow;
            this.capacity     = capacity;
        }
    }

    public final List<HopCost> hops = new ArrayList<>();
    public double totalBaseCost;
    public double totalCarbonCost;
    public double totalFailureRisk;
    public double totalLatency;
    public double totalEffectiveCost;
    public String pathString;
    public String zoneWeightsUsed; // e.g. "Hospital (w3=0.70)"
}
