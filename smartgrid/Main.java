package smartgrid;

import smartgrid.services.SimulationEngine;
import smartgrid.ui.GridUI;

import javax.swing.SwingUtilities;

/**
 * Application entry point.
 * Instantiates SimulationEngine and passes it to GridUI.
 * UI is launched on the Swing Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulationEngine engine = new SimulationEngine();
            new GridUI(engine);
        });
    }
}
