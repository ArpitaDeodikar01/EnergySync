package smartgrid;

import javax.swing.SwingUtilities;

/**
 * DSA Role: Application entry point.
 * Instantiates the GridSimulator (which builds the hardcoded topology)
 * and passes it to GridUI. All components share the same GridSimulator instance.
 *
 * Swing UI is launched on the Event Dispatch Thread (EDT) as required
 * by Swing's single-threaded model.
 */
public class Main {

    /**
     * Entry point. Schedules UI creation on the Swing EDT.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // Schedule on the EDT — Swing components must be created and accessed
        // only from the Event Dispatch Thread to avoid race conditions
        SwingUtilities.invokeLater(() -> {
            // Create the simulation engine; buildTopology() is called in the constructor
            GridSimulator simulator = new GridSimulator();

            // Create and show the UI, passing the shared simulator instance
            new GridUI(simulator);
        });
    }
}
