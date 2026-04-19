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

    /** True after a fault is injected and before Auto Reroute is run */
    private boolean faultPending = false;

    // Weight slider value labels
    private final JLabel lblW1 = sliderVal("1.00");
    private final JLabel lblW2 = sliderVal("0.15");
    private final JLabel lblW3 = sliderVal("0.00");
    private final JLabel lblW4 = sliderVal("0.00");

    // Adaptive mode toggle
    private final JToggleButton btnAdaptive = new JToggleButton("Adaptive: ON", true);

    // Fenwick prefix-sum labels — updated on every refresh()
    private final java.util.List<JLabel> fenwickLabels = new java.util.ArrayList<>();

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

        // Zone classification legend
        panel.add(zoneLegend());
        panel.add(Box.createVerticalStrut(8));

        // Live Fenwick prefix-sum panel
        panel.add(fenwickPanel());
        panel.add(Box.createVerticalStrut(12));

        JButton btnRun = makeButton("Run Distribution", new Color(34, 139, 34));
        btnRun.addActionListener(e -> { engine.runNormalDistribution(); refresh(); });

        JButton btnFault = makeButton("Inject Fault", new Color(180, 40, 40));
        btnFault.addActionListener(e -> injectFaultAction());

        JButton btnReroute = makeButton("Auto Reroute", new Color(180, 110, 0));
        btnReroute.addActionListener(e -> { engine.autoReroute(); faultPending = false; refresh(); });

        JButton btnDijkstra = makeButton("Dijkstra Path", new Color(40, 80, 160));
        btnDijkstra.addActionListener(e -> dijkstraAction());

        JButton btnPredict = makeButton("Run Prediction", new Color(60, 100, 180));
        btnPredict.addActionListener(e -> runPrediction());

        JButton btnReset = makeButton("Reset", new Color(55, 55, 110));
        btnReset.addActionListener(e -> { engine.reset(); faultPending = false; detailPanel.showEmpty(); refresh(); });

        panel.add(btnRun);      panel.add(vgap(6));
        panel.add(btnFault);    panel.add(vgap(6));
        panel.add(btnReroute);  panel.add(vgap(6));
        panel.add(btnDijkstra); panel.add(vgap(6));
        panel.add(btnPredict);  panel.add(vgap(6));
        panel.add(btnReset);    panel.add(vgap(14));

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
        faultPending = true;
        refresh();
    }

    // -------------------------------------------------------------------------
    // Dijkstra src → dst action
    // -------------------------------------------------------------------------

    private void dijkstraAction() {
        java.util.List<Node> nodes = new java.util.ArrayList<>(engine.getGraph().getNodes());
        nodes.sort((a, b) -> Integer.compare(a.id, b.id));

        String[] names = nodes.stream()
                .map(n -> n.label + " [" + n.type.name().charAt(0) + "]")
                .toArray(String[]::new);

        // Source picker
        String srcChoice = (String) JOptionPane.showInputDialog(
                this, "Select SOURCE node:", "Dijkstra — Source",
                JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (srcChoice == null) return;

        // Destination picker — exclude the source
        String[] dstNames = java.util.Arrays.stream(names)
                .filter(n -> !n.equals(srcChoice))
                .toArray(String[]::new);
        String dstChoice = (String) JOptionPane.showInputDialog(
                this, "Select DESTINATION node:", "Dijkstra — Destination",
                JOptionPane.PLAIN_MESSAGE, null, dstNames, dstNames[0]);
        if (dstChoice == null) return;

        int srcIdx = java.util.Arrays.asList(names).indexOf(srcChoice);
        int dstIdx = java.util.Arrays.asList(names).indexOf(dstChoice);
        Node src = nodes.get(srcIdx);
        Node dst = nodes.get(dstIdx);

        java.util.List<Node> path = engine.runDijkstra(src.id, dst.id);
        refresh();

        if (path.isEmpty()) {
            statsBar.setText(String.format(
                " <html><font color='#ff6666'>No path found: %s → %s</font></html>",
                src.label, dst.label));
            JOptionPane.showMessageDialog(this,
                "No path found between " + src.label + " and " + dst.label + ".\n"
                + "(Node may be isolated or unreachable.)",
                "Dijkstra Result", JOptionPane.WARNING_MESSAGE);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).label);
                if (i < path.size() - 1) sb.append(" → ");
            }
            double cost = engine.getDijkstraPathCost();
            statsBar.setText(String.format(
                "<html> <font color='#66aaff'>Dijkstra %s → %s</font>"
                + " &nbsp; Path: %s &nbsp; Cost: %.2f</html>",
                src.label, dst.label, sb, cost));
        }
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

        // Fenwick prefix-sum: cumulative load across all zones
        List<Node> zones = engine.getZoneNodes();
        int totalZones = zones.size();
        int fenwickTotal = totalZones > 0 ? engine.queryFenwickPrefix(totalZones) : 0;
        int maxLoadZoneIdx = engine.queryMaxLoadZone(); // 1-based
        String maxZoneLabel = (maxLoadZoneIdx > 0 && maxLoadZoneIdx <= zones.size())
                ? zones.get(maxLoadZoneIdx - 1).label : "—";

        String pathInfo = "";
        if (!path.isEmpty()) {
            StringBuilder sb = new StringBuilder("  Path: ");
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).label);
                if (i < path.size() - 1) sb.append(" → ");
            }
            // Show zone weights used if path ends at a zone
            Node dest = path.get(path.size() - 1);
            if (dest.zoneClass != null) {
                sb.append(" [").append(dest.zoneClass.displayName).append(" weights]");
            }
            sb.append(String.format("  cost=%.2f", engine.getDijkstraPathCost()));
            pathInfo = sb.toString();
        }

        String faultWarning = faultPending
            ? " &nbsp;<font color='#ffaa00'>&#9888; Fault active — click Auto Reroute</font>"
            : "";

        statsBar.setText(String.format(
            "<html> Energy: %d &nbsp; Cost: %d &nbsp;"
            + "<font color='#aaddff'>Fenwick&#8721;: %d &nbsp; MaxLoad: %s</font> &nbsp;"
            + "Loss: %.1f%% &nbsp;"
            + "<font color='#66ff99'>CO&#8322;: %.3f kg</font>"
            + "%s%s%s</html>",
            s.totalEnergyRouted, s.totalCost,
            fenwickTotal, maxZoneLabel,
            s.flowLossPercent,
            s.totalCarbonKg,
            s.carbonIncreasePct != 0.0
                ? String.format(" &nbsp;<font color='#ff6666'>&#8679;CO&#8322; +%.1f%%</font>",
                                s.carbonIncreasePct)
                : "",
            faultWarning,
            pathInfo.isEmpty() ? "" : " &nbsp;<font color='#88ccff'>" + pathInfo + "</font>"
        ));
    }

    private void refresh() {
        updateStatsBar();
        refreshFenwickPanel();
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

    /** Builds the zone classification legend panel. */
    private JPanel zoneLegend() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(20, 20, 38));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 100)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)
        ));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JLabel title = new JLabel("Zone Priority");
        title.setForeground(new Color(100, 160, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        p.add(title);
        p.add(vgap(3));
        p.add(zoneRow(ZoneClassification.HOSPITAL,    "Hospital",    "Reliability"));
        p.add(vgap(2));
        p.add(zoneRow(ZoneClassification.INDUSTRY,    "Industry",    "CO\u2082 Min"));
        p.add(vgap(2));
        p.add(zoneRow(ZoneClassification.RESIDENTIAL, "Residential", "Cost Min"));
        p.add(vgap(2));
        p.add(zoneRow(ZoneClassification.COMMERCIAL,  "Commercial",  "Balanced"));
        return p;
    }

    /**
     * Live Fenwick prefix-sum panel — shows cumulative zone loads queried
     * from the BIT in O(log Z). Updates on every refresh().
     */
    private JPanel fenwickPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(15, 25, 40));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(40, 80, 120)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)
        ));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel title = new JLabel("Fenwick Prefix Sums");
        title.setForeground(new Color(80, 200, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        p.add(title);
        p.add(vgap(3));

        // We'll store labels so refresh() can update them
        fenwickLabels.clear();
        List<Node> zones = engine.getZoneNodes();
        for (int i = 1; i <= zones.size(); i++) {
            final int idx = i;
            String zoneName = zones.get(i - 1).label;
            int prefixVal = engine.queryFenwickPrefix(i);
            JLabel lbl = new JLabel(String.format("Z1..%d (%s): %d", i, zoneName, prefixVal));
            lbl.setForeground(new Color(140, 210, 255));
            lbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
            fenwickLabels.add(lbl);
            p.add(lbl);
        }
        return p;
    }

    /** Updates the Fenwick prefix-sum labels in the control panel. */
    private void refreshFenwickPanel() {
        List<Node> zones = engine.getZoneNodes();
        for (int i = 0; i < fenwickLabels.size() && i < zones.size(); i++) {
            int prefixVal = engine.queryFenwickPrefix(i + 1);
            fenwickLabels.get(i).setText(
                String.format("Z1..%d (%s): %d", i + 1, zones.get(i).label, prefixVal));
        }
    }

    private JPanel zoneRow(ZoneClassification zc, String name, String priority) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(new Color(20, 20, 38));
        JLabel dot = new JLabel("■");
        dot.setForeground(zc.color);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JLabel txt = new JLabel("<html><b>" + name + "</b> <font color='#aaaacc'>→ " + priority + "</font></html>");
        txt.setFont(new Font("SansSerif", Font.PLAIN, 10));
        row.add(dot);
        row.add(txt);
        return row;
    }
}
