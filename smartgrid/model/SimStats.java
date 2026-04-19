package smartgrid.model;

/**
 * Value object that aggregates simulation metrics per run.
 * Passed from SimulationEngine to GridUI after each algorithm run.
 */
public class SimStats {

    public int    totalEnergyRouted;
    public int    totalCost;
    public int    zonesAffected;
    public long   recoveryTimeMs;
    public double flowLossPercent;

    /**
     * Total carbon emitted this run (kg CO₂).
     * Computed as: sum over all edges of (edge.carbonCost * edge.flow).
     */
    public double totalCarbonKg;

    /**
     * Carbon intensity: kg CO₂ per unit of energy routed.
     * 0.0 when no energy was routed.
     * Formula: totalCarbonKg / totalEnergyRouted
     */
    public double carbonPerUnit;

    /**
     * Carbon emitted in the pre-fault normal distribution run.
     * Used to compute carbonIncreasePct after rerouting.
     */
    public double preFaultCarbonKg;

    /**
     * Percentage increase in carbon emissions after rerouting vs pre-fault.
     * Positive means more CO₂ (e.g. Solar lost, Hydro/Wind picked up load).
     * Formula: (totalCarbonKg - preFaultCarbonKg) / preFaultCarbonKg * 100
     */
    public double carbonIncreasePct;

    public SimStats() {
        totalEnergyRouted = 0;
        totalCost         = 0;
        zonesAffected     = 0;
        recoveryTimeMs    = 0;
        flowLossPercent   = 0.0;
        totalCarbonKg     = 0.0;
        carbonPerUnit     = 0.0;
        preFaultCarbonKg  = 0.0;
        carbonIncreasePct = 0.0;
    }
}
