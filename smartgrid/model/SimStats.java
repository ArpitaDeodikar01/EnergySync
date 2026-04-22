package smartgrid.model;

/**
 * Aggregates simulation metrics per run AND across the session.
 * Session totals feed the Fenwick Tree CO₂ dashboard.
 */
public class SimStats {

    // --- Per-run metrics ---
    public int    totalEnergyRouted;
    public int    totalCost;
    public int    zonesAffected;
    public long   recoveryTimeMs;
    public double flowLossPercent;
    public double totalCarbonKg;
    public double carbonPerUnit;
    public double preFaultCarbonKg;
    public double carbonIncreasePct;

    // --- Session-cumulative (updated by FenwickTree) ---
    /** Total MWh delivered across all zones since session start (Fenwick prefix sum). */
    public double sessionEnergyMWh;
    /** Total CO₂ emitted across all runs since session start. */
    public double sessionCarbonKg;
    /**
     * CO₂ saved vs naive routing (all-coal baseline at 0.82 kg/MWh).
     * saved = sessionEnergyMWh * 0.82 - sessionCarbonKg
     */
    public double co2SavedKg;
    /** Renewable fraction: (solar+wind+nuclear flow) / total flow */
    public double renewableFraction;
    /** Number of edge costs updated by adaptive mode this cycle */
    public int adaptiveEdgesUpdated;
    /** Bellman-Ford result from last stress check */
    public boolean stressCycleDetected;
    /** Load-shedding order from last TopologicalSort (zone labels) */
    public String loadSheddingOrder = "";
    /** Union-Find component count after last fault */
    public int componentCount;

    public SimStats() {
        totalEnergyRouted  = 0;
        totalCost          = 0;
        zonesAffected      = 0;
        recoveryTimeMs     = 0;
        flowLossPercent    = 0.0;
        totalCarbonKg      = 0.0;
        carbonPerUnit      = 0.0;
        preFaultCarbonKg   = 0.0;
        carbonIncreasePct  = 0.0;
        sessionEnergyMWh   = 0.0;
        sessionCarbonKg    = 0.0;
        co2SavedKg         = 0.0;
        renewableFraction  = 0.0;
        adaptiveEdgesUpdated = 0;
        stressCycleDetected  = false;
        componentCount       = 1;
    }
}
