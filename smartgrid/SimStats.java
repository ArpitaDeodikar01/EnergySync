package smartgrid;

/**
 * DSA Role: Immutable-style value object that aggregates simulation metrics.
 * Passed from GridSimulator to GridUI after each algorithm run so the
 * stats bar can be updated without GridUI holding any simulation state.
 */
public class SimStats {

    /** Total energy units routed in the most recent MCMF run */
    public int totalEnergyRouted;

    /** Total transmission cost of the most recent MCMF run */
    public int totalCost;

    /** Number of CityZones marked FAULT after the most recent BFS run */
    public int zonesAffected;

    /** Elapsed milliseconds from FaultEvent to MCMF completion (auto-reroute) */
    public long recoveryTimeMs;

    /**
     * Percentage of pre-fault flow that could not be rerouted after a fault.
     * Formula: (preFaultFlow - postRerouteFlow) / preFaultFlow * 100
     */
    public double flowLossPercent;

    /** Constructs a zeroed SimStats — default state before any simulation runs */
    public SimStats() {
        totalEnergyRouted = 0;
        totalCost = 0;
        zonesAffected = 0;
        recoveryTimeMs = 0;
        flowLossPercent = 0.0;
    }
}
