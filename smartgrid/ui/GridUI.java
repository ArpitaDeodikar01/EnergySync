package smartgrid.ui;

import smartgrid.model.*;
import smartgrid.services.SimulationEngine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * Main application window.
 *
 * Layout:
 *   CENTER — GraphCanvas (graph rendering + animation)
 *   EAST   — ControlPanel (buttons + weight sliders + adaptive toggle)
 *   WEST   — NodeDetailPanel (selected node info)
 *   SOUTH  — Stats bar
 */
public class GridUI extends JFrame {

    private static final Color BG_DARK  = new Color(22, 22, 40);
    private static final Color BG_MID   = new Color(30, 30, 52);
    private static final Color FG_LIGHT = Color.WHITE;

    private final SimulationEngine engine;
    private final GraphCanvas      canvas;
    private final NodeDetailPanel  detailPanel;
    private final JLabel           statsBar;

    /** Last node clicked on the canvas — used as the fault target. */
    private Node selectedNode = null;

    // Weight slider value labels
    private final JLabel lblW1 = sliderVal("1.00");
    private final JLabel lblW2 = sliderVal("0.15");
    private final JLabel lblW3 = sliderVal("0.00");
    private final JLabel lblW4 = sliderVal("0.00");

    // Adaptive mode toggle
    private final JToggleButton btnAdaptive = new JToggleButton("Adaptive: ON", true);

    public GridUI(SimulationEngine engine) {
        super("SmartGrid — Adaptive Energy Distribution");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        // Stats bar
        statsBar = new JLabel();
        statsBar.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statsBar.setForeground(FG_LIGHT);
        statsBar.setBackground(new Color(15, 15, 28));
        statsBar.setOpaque(true);
        statsBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        updateStatsBar();

        // Node detail panel (west)
        detailPanel = new NodeDetailPanel(engine);

        // Graph canvas (center)
        canvas = new GraphCanvas(engine, new GraphCanvas.StatsBarUpdater() {
            @Override public void update()           { updateStatsBar(); }
            @Override public void setText(String t)  { statsBar.setText(t); }
        });
        canvas.setNodeSelectionListener(node -> {
            selectedNode = node;
            detailPanel.showNode(node);
        });

        add(canvas,              BorderLayout.CENTER);
        add(buildControlPanel(), BorderLayout.EAST);
        add(detailPanel,         BorderLayout.WEST);
        add(statsBar,            BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Control panel
    // -------------------------------------------------------------------------

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MID);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        panel.setPreferredSize(new Dimension(195, 0));

        panel.add(sectionTitle("Simulation"));
        panel.add(Box.createVerticalStrut(8));

        JButton btnRun = makeButton("Run Distribution", new Color(34, 139, 34));
        btnRun.addActionListener(e -> { engine.runNormalDistribution(); refresh(); });

        JButton btnFault = makeButton("Inject Fault", new Color(180, 40, 40));
        btnFault.addActionListener(e -> injectFaultAction());

        JButton btnReroute = makeButton("Auto Reroute", new Color(180, 110, 0));
        btnReroute.addActionListener(e -> { engine.autoReroute(); refresh(); });

        JButton btnPredict = makeButton("Run Prediction", new Color(60, 100, 180));
        btnPredict.addActionListener(e -> runPrediction());

        JButton btnReset = makeButton("Reset", new Color(55, 55, 110));
        btnReset.addActionListener(e -> { engine.reset(); detailPanel.showEmpty(); refresh(); });

        panel.add(btnRun);     panel.add(vgap(6));
        panel.add(btnFault);   panel.add(vgap(6));
        panel.add(btnReroute); panel.add(vgap(6));
        panel.add(btnPredict); panel.add(vgap(6));
        panel.add(btnReset);   panel.add(vgap(14));

        // Adaptive mode toggle
        panel.add(sectionTitle("Mode"));
        panel.add(vgap(6));
        styleToggle(btnAdaptive);
        btnAdaptive.addActionListener(e -> {
            boolean on = btnAdaptive.isSelected();
            engine.setAdaptiveMode(on);
            btnAdaptive.setText("Adaptive: " + (on ? "ON" : "OFF"));
            btnAdaptive.setBackground(on ? new Color(30, 110, 60) : new Color(90, 40, 40));
        });
        panel.add(btnAdaptive); panel.add(vgap(14));

        // Weight sliders
        panel.add(sectionTitle("Cost Weights"));
        panel.add(vgap(6));
        panel.add(sliderRow("w1 Economy",  lblW1, 100, (int)(engine.getWeights().w1 * 100), v -> {
            engine.getWeights().w1 = v / 100.0;
            lblW1.setText(String.format("%.2f", v / 100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(4));
        panel.add(sliderRow("w2 Carbon",   lblW2, 100, (int)(engine.getWeights().w2 * 100), v -> {
            engine.getWeights().w2 = v / 100.0;
            lblW2.setText(String.format("%.2f", v / 100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(4));
        panel.add(sliderRow("w3 Risk",     lblW3, 100, (int)(engine.getWeights().w3 * 100), v -> {
            engine.getWeights().w3 = v / 100.0;
            lblW3.setText(String.format("%.2f", v / 100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(4));
        panel.add(sliderRow("w4 Latency",  lblW4, 100, (int)(engine.getWeights().w4 * 100), v -> {
            engine.getWeights().w4 = v / 100.0;
            lblW4.setText(String.format("%.2f", v / 100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(14));

        // Legend
        panel.add(sectionTitle("Legend"));
        panel.add(vgap(6));
        panel.add(makeLegend());

        return panel;
    }

    // -------------------------------------------------------------------------
    // Fault injection action
    // -------------------------------------------------------------------------

    /**
     * If a node is selected on the canvas, faults that node directly.
     * Otherwise shows a dropdown dialog listing all non-isolated nodes.
     */
    private void injectFaultAction() {
        // Collect faultable nodes (non-isolated, still in graph)
        java.util.List<Node> candidates = new java.util.ArrayList<>();
        for (Node n : engine.getGraph().getNodes()) {
            if (n.status != NodeStatus.ISOLATED && n.status != NodeStatus.FAULT) {
                candidates.add(n);
            }
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No nodes available to fault.",
                    "Inject Fault", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Node target = null;

        // If the currently selected node is still faultable, use it directly
        if (selectedNode != null && candidates.contains(selectedNode)) {
            target = selectedNode;
        } else {
            // Show a picker
            String[] names = candidates.stream()
                    .map(n -> n.label + " (" + n.type.name() + ")")
                    .toArray(String[]::new);
            String choice = (String) JOptionPane.showInputDialog(
                    this,
                    "Select a node to inject a fault into:",
                    "Inject Fault",
                    JOptionPane.WARNING_MESSAGE,
                    null, names, names[0]);
            if (choice == null) return; // cancelled
            int idx = java.util.Arrays.asList(names).indexOf(choice);
            target = candidates.get(idx);
        }

        boolean ok = engine.injectFault(target.id);
        if (!ok) {
            JOptionPane.showMessageDialog(this,
                    "Node '" + target.label + "' could not be faulted (already removed?).",
                    "Inject Fault", JOptionPane.ERROR_MESSAGE);
            return;
        }

        selectedNode = null;
        detailPanel.showEmpty();
        refresh();
    }

    // -------------------------------------------------------------------------
    // Prediction action
    // -------------------------------------------------------------------------

    private void runPrediction() {
        int nodeId = engine.predictNextFailureNode();
        if (nodeId == -1) {
            JOptionPane.showMessageDialog(this,
                "No failure history yet.\nRun a fault first to build prediction data.",
                "Prediction", JOptionPane.INFORMATION_MESSAGE);
        } else {
            Node n = engine.getGraph().getNode(nodeId);
            String name = n != null ? n.label : "Node #" + nodeId;
            double risk = engine.getPredictionEngine().getRiskScore(nodeId);
            JOptionPane.showMessageDialog(this,
                String.format("Predicted next failure:\n  Node: %s\n  Risk score: %.4f", name, risk),
                "Prediction Result", JOptionPane.WARNING_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Stats bar
    // -------------------------------------------------------------------------

    private void updateStatsBar() {
        SimStats s = engine.getStats();
        List<Node> path = engine.getDijkstraPath();

        String pathInfo = "";
        if (!path.isEmpty()) {
            StringBuilder sb = new StringBuilder("  Path: ");
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).label);
                if (i < path.size() - 1) sb.append(" → ");
            }
            pathInfo = sb.toString();
        }

        statsBar.setText(String.format(
            "<html> Energy: %d &nbsp; Cost: %d &nbsp; Zones: %d &nbsp;"
            + "Recovery: %d ms &nbsp; Loss: %.1f%% &nbsp;"
            + "<font color='#66ff99'>&#9679; CO&#8322;: %.3f kg (%.4f/u)</font>"
            + "%s</html>",
            s.totalEnergyRouted, s.totalCost, s.zonesAffected,
            s.recoveryTimeMs, s.flowLossPercent,
            s.totalCarbonKg, s.carbonPerUnit,
            pathInfo
        ));
    }

    private void refresh() {
        updateStatsBar();
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // Widget helpers
    // -------------------------------------------------------------------------

    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton("<html><center>" + text + "</center></html>");
        btn.setBackground(bg);
        btn.setForeground(FG_LIGHT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(175, 40));
        btn.setPreferredSize(new Dimension(175, 40));
        return btn;
    }

    private void styleToggle(JToggleButton btn) {
        btn.setBackground(new Color(30, 110, 60));
        btn.setForeground(FG_LIGHT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(175, 36));
        btn.setPreferredSize(new Dimension(175, 36));
    }

    /** Builds a labeled slider row with a live value label. */
    private JPanel sliderRow(String label, JLabel valLabel, int max, int initial,
                              java.util.function.IntConsumer onChange) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(BG_MID);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG_MID);
        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(160, 160, 200));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        top.add(lbl, BorderLayout.WEST);
        top.add(valLabel, BorderLayout.EAST);

        JSlider slider = new JSlider(0, max, initial);
        slider.setBackground(BG_MID);
        slider.setForeground(new Color(100, 160, 255));
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        slider.addChangeListener(e -> onChange.accept(slider.getValue()));

        row.add(top);
        row.add(slider);
        return row;
    }

    private JLabel sliderVal(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(120, 200, 255));
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(100, 160, 255));
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private Component vgap(int h) { return Box.createVerticalStrut(h); }

    private JPanel makeLegend() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_MID);
        p.add(legendRow(new Color(34, 168, 74),  "Active (normal)"));
        p.add(vgap(3));
        p.add(legendRow(new Color(210, 190, 0),  "High load / risk"));
        p.add(vgap(3));
        p.add(legendRow(new Color(200, 40, 40),  "Fault / Isolated"));
        p.add(vgap(3));
        p.add(legendRow(new Color(220, 130, 0),  "Rerouted"));
        p.add(vgap(3));
        p.add(legendRow(new Color(30, 100, 220), "Dijkstra path"));
        return p;
    }

    private JPanel legendRow(Color color, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(BG_MID);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        JLabel txt = new JLabel(label);
        txt.setForeground(new Color(170, 170, 190));
        txt.setFont(new Font("SansSerif", Font.PLAIN, 10));
        row.add(dot);
        row.add(txt);
        return row;
    }
}
