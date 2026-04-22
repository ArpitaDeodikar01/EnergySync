package smartgrid.ui;

import smartgrid.model.*;
import smartgrid.algorithms.BellmanFordAlgorithm;
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

    private Node selectedNode = null;
    private boolean faultPending = false;

    // Weight slider labels
    private final JLabel lblW1 = sliderVal("1.00");
    private final JLabel lblW2 = sliderVal("0.15");
    private final JLabel lblW3 = sliderVal("0.00");
    private final JLabel lblW4 = sliderVal("0.00");

    private final JToggleButton btnAdaptive = new JToggleButton("Adaptive: ON", true);
    private final java.util.List<JLabel> fenwickLabels = new java.util.ArrayList<>();

    // Algorithm banner (top-right toast)
    private final JLabel bannerLabel = new JLabel(" ");
    private javax.swing.Timer bannerTimer;

    // DSA log panel labels
    private final JTextArea dsaLogArea = new JTextArea(8, 22);

    // Segment Tree panel labels
    private final JLabel[] segTreeLabels = new JLabel[5];
    private final JLabel segTreeMaxLabel = new JLabel("—");
    private final JLabel segTreeSumLabel = new JLabel("—");
    private final JLabel segTreeMinLabel = new JLabel("—");

    // Path cost breakdown panel
    private final JTextArea pathBreakdownArea = new JTextArea(6, 22);

    // Prediction widget
    private final JLabel predNodeLabel  = new JLabel("—");
    private final JLabel predRiskLabel  = new JLabel("—");
    private final JLabel predHistLabel  = new JLabel("—");
    private final JProgressBar predBar  = new JProgressBar(0, 100);

    // CO₂ dashboard
    private final JLabel co2RenewLabel  = new JLabel("—");
    private final JLabel co2TotalLabel  = new JLabel("—");
    private final JLabel co2SavedLabel  = new JLabel("—");

    public GridUI(SimulationEngine engine) {
        super("SmartGrid — Adaptive Energy Distribution  |  DSA Competition");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        // Algorithm banner (top)
        bannerLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bannerLabel.setForeground(new Color(255, 220, 60));
        bannerLabel.setBackground(new Color(10, 10, 25));
        bannerLabel.setOpaque(true);
        bannerLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        bannerLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        // Stats bar
        statsBar = new JLabel();
        statsBar.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statsBar.setForeground(FG_LIGHT);
        statsBar.setBackground(new Color(15, 15, 28));
        statsBar.setOpaque(true);
        statsBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        updateStatsBar();

        // Top bar = banner + stats
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(10, 10, 25));
        topBar.add(statsBar,    BorderLayout.CENTER);
        topBar.add(bannerLabel, BorderLayout.EAST);

        detailPanel = new NodeDetailPanel(engine);

        canvas = new GraphCanvas(engine, new GraphCanvas.StatsBarUpdater() {
            @Override public void update()           { updateStatsBar(); }
            @Override public void setText(String t)  { statsBar.setText(t); }
        });
        canvas.setNodeSelectionListener(node -> {
            selectedNode = node;
            detailPanel.showNode(node);
        });

        // Right panel = scrollable control panel
        JScrollPane ctrlScroll = new JScrollPane(buildControlPanel());
        ctrlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ctrlScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ctrlScroll.setPreferredSize(new Dimension(210, 0));
        ctrlScroll.setBorder(null);

        add(canvas,      BorderLayout.CENTER);
        add(ctrlScroll,  BorderLayout.EAST);
        add(detailPanel, BorderLayout.WEST);
        add(topBar,      BorderLayout.NORTH);
        add(buildBottomPanel(), BorderLayout.SOUTH);

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
        panel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        panel.setPreferredSize(new Dimension(200, 0));

        // DEMO MODE — top priority
        JButton btnDemo = makeButton("▶ DEMO MODE", new Color(120, 40, 160));
        btnDemo.addActionListener(e -> runDemoMode());
        panel.add(btnDemo); panel.add(vgap(8));

        panel.add(sectionTitle("Simulation"));
        panel.add(vgap(4));
        panel.add(zoneLegend());
        panel.add(vgap(6));

        JButton btnRun = makeButton("Run Distribution", new Color(34, 139, 34));
        btnRun.addActionListener(e -> { engine.runNormalDistribution(); refresh(); });

        JButton btnFault = makeButton("Inject Fault", new Color(180, 40, 40));
        btnFault.addActionListener(e -> injectFaultAction());

        JButton btnReroute = makeButton("Auto Reroute", new Color(180, 110, 0));
        btnReroute.addActionListener(e -> { engine.autoReroute(); faultPending = false; refresh(); });

        JButton btnDijkstra = makeButton("Dijkstra Path", new Color(40, 80, 160));
        btnDijkstra.addActionListener(e -> dijkstraAction());

        JButton btnPredict = makeButton("Run Prediction", new Color(60, 100, 180));
        btnPredict.addActionListener(e -> { runPrediction(); refresh(); });

        JButton btnReset = makeButton("Reset", new Color(55, 55, 110));
        btnReset.addActionListener(e -> { engine.reset(); faultPending = false; detailPanel.showEmpty(); refresh(); });

        panel.add(btnRun);      panel.add(vgap(5));
        panel.add(btnFault);    panel.add(vgap(5));
        panel.add(btnReroute);  panel.add(vgap(5));
        panel.add(btnDijkstra); panel.add(vgap(5));
        panel.add(btnPredict);  panel.add(vgap(5));
        panel.add(btnReset);    panel.add(vgap(10));

        // Adaptive mode
        panel.add(sectionTitle("Mode"));
        panel.add(vgap(4));
        styleToggle(btnAdaptive);
        btnAdaptive.addActionListener(e -> {
            boolean on = btnAdaptive.isSelected();
            engine.setAdaptiveMode(on);
            btnAdaptive.setText("Adaptive: " + (on ? "ON" : "OFF"));
            btnAdaptive.setBackground(on ? new Color(30, 110, 60) : new Color(90, 40, 40));
        });
        panel.add(btnAdaptive); panel.add(vgap(10));

        // Weight sliders
        panel.add(sectionTitle("Cost Weights"));
        panel.add(vgap(4));
        panel.add(sliderRow("w1 Economy",  lblW1, 100, (int)(engine.getWeights().w1 * 100), v -> {
            engine.getWeights().w1 = v / 100.0; lblW1.setText(String.format("%.2f", v/100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(3));
        panel.add(sliderRow("w2 Carbon",   lblW2, 100, (int)(engine.getWeights().w2 * 100), v -> {
            engine.getWeights().w2 = v / 100.0; lblW2.setText(String.format("%.2f", v/100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(3));
        panel.add(sliderRow("w3 Risk",     lblW3, 100, (int)(engine.getWeights().w3 * 100), v -> {
            engine.getWeights().w3 = v / 100.0; lblW3.setText(String.format("%.2f", v/100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(3));
        panel.add(sliderRow("w4 Latency",  lblW4, 100, (int)(engine.getWeights().w4 * 100), v -> {
            engine.getWeights().w4 = v / 100.0; lblW4.setText(String.format("%.2f", v/100.0));
            engine.setWeights(engine.getWeights());
        }));
        panel.add(vgap(10));

        // Prediction widget
        panel.add(buildPredictionWidget());
        panel.add(vgap(8));

        // Fenwick panel
        panel.add(fenwickPanel());
        panel.add(vgap(8));

        // Segment Tree panel
        panel.add(buildSegTreePanel());
        panel.add(vgap(8));

        // CO₂ dashboard
        panel.add(buildCO2Panel());
        panel.add(vgap(8));

        // Legend
        panel.add(sectionTitle("Legend"));
        panel.add(vgap(4));
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
                    "Node '" + target.label + "' could not be faulted.",
                    "Inject Fault", JOptionPane.ERROR_MESSAGE);
            return;
        }
        showBanner("▶ BFS → Union-Find → DFS → Bellman-Ford");
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

        String srcChoice = (String) JOptionPane.showInputDialog(this, "Select SOURCE node:",
                "Dijkstra — Source", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (srcChoice == null) return;

        String[] dstNames = java.util.Arrays.stream(names)
                .filter(n -> !n.equals(srcChoice)).toArray(String[]::new);
        String dstChoice = (String) JOptionPane.showInputDialog(this, "Select DESTINATION node:",
                "Dijkstra — Destination", JOptionPane.PLAIN_MESSAGE, null, dstNames, dstNames[0]);
        if (dstChoice == null) return;

        int srcIdx = java.util.Arrays.asList(names).indexOf(srcChoice);
        int dstIdx = java.util.Arrays.asList(names).indexOf(dstChoice);
        Node src = nodes.get(srcIdx);
        Node dst = nodes.get(dstIdx);

        String zoneName = dst.zoneClass != null ? dst.zoneClass.displayName : "Global";
        showBanner("▶ Running: Dijkstra [" + zoneName + " priority]");
        engine.runDijkstra(src.id, dst.id);
        refresh();

        if (engine.getDijkstraPath().isEmpty()) {
            statsBar.setText("<html><font color='#ff6666'>No path: " + src.label + " → " + dst.label + "</font></html>");
        }
    }

    // -------------------------------------------------------------------------
    // Prediction action
    // -------------------------------------------------------------------------

    private void runPrediction() {
        showBanner("▶ Running: Sliding Window + Max-Heap predictor");
        engine.getPredictionEngine().refreshRiskScores();
        int nodeId = engine.predictNextFailureNode();
        refreshPredictionWidget();
        if (nodeId == -1) {
            JOptionPane.showMessageDialog(this,
                "No failure history yet.\nRun a fault first to build prediction data.",
                "Prediction", JOptionPane.INFORMATION_MESSAGE);
        } else {
            Node n = engine.getGraph().getNode(nodeId);
            String name = n != null ? n.label : "Node #" + nodeId;
            double risk = engine.getPredictionEngine().getRiskScore(nodeId);
            int windowSize = engine.getPredictionEngine().getWindowSize();
            JOptionPane.showMessageDialog(this,
                String.format("⚠ Next Predicted Failure\n  Node: %s\n  Risk score: %.1f%%\n  Window: %d events",
                    name, risk * 100, windowSize),
                "Prediction Result", JOptionPane.WARNING_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Stats bar
    // -------------------------------------------------------------------------

    private void updateStatsBar() {
        SimStats s = engine.getStats();
        List<Node> zones = engine.getZoneNodes();
        int fenwickTotal = zones.size() > 0 ? engine.queryFenwickPrefix(zones.size()) : 0;
        int maxZoneIdx   = engine.queryMaxLoadZone();
        String maxZone   = (maxZoneIdx > 0 && maxZoneIdx <= zones.size())
                ? zones.get(maxZoneIdx - 1).label : "—";

        String pathInfo = "";
        List<Node> path = engine.getDijkstraPath();
        if (!path.isEmpty()) {
            StringBuilder sb = new StringBuilder(" Path: ");
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).label);
                if (i < path.size() - 1) sb.append("→");
            }
            Node dest = path.get(path.size() - 1);
            if (dest.zoneClass != null) sb.append(" [").append(dest.zoneClass.displayName).append("]");
            sb.append(String.format(" cost=%.2f", engine.getDijkstraPathCost()));
            pathInfo = sb.toString();
        }

        String faultWarn = faultPending
            ? " &nbsp;<font color='#ffaa00'>⚠ Fault — click Auto Reroute</font>" : "";

        statsBar.setText(String.format(
            "<html>Energy:%d &nbsp;Cost:%d &nbsp;"
            + "<font color='#aaddff'>BIT∑:%d MaxLoad:%s</font> &nbsp;"
            + "Loss:%.1f%% &nbsp;"
            + "<font color='#66ff99'>CO₂:%.3fkg</font>"
            + "%s%s%s</html>",
            s.totalEnergyRouted, s.totalCost,
            fenwickTotal, maxZone,
            s.flowLossPercent, s.totalCarbonKg,
            s.carbonIncreasePct != 0.0
                ? String.format(" &nbsp;<font color='#ff6666'>↑CO₂+%.1f%%</font>", s.carbonIncreasePct) : "",
            faultWarn,
            pathInfo.isEmpty() ? "" : " &nbsp;<font color='#88ccff'>" + pathInfo + "</font>"
        ));
    }

    private void refresh() {
        updateStatsBar();
        refreshFenwickPanel();
        refreshSegTreePanel();
        refreshPredictionWidget();
        refreshCO2Panel();
        refreshDSALog();
        refreshPathBreakdown();
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // Bottom panel: DSA log + path breakdown
    // -------------------------------------------------------------------------

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(new Color(12, 12, 24));
        p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        p.setPreferredSize(new Dimension(0, 130));

        // DSA log
        dsaLogArea.setEditable(false);
        dsaLogArea.setBackground(new Color(8, 18, 30));
        dsaLogArea.setForeground(new Color(100, 220, 100));
        dsaLogArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        dsaLogArea.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(30, 80, 30)),
            "Algorithm Activity", 0, 0,
            new Font("SansSerif", Font.BOLD, 10), new Color(80, 200, 80)));
        JScrollPane logScroll = new JScrollPane(dsaLogArea);
        logScroll.setPreferredSize(new Dimension(400, 0));

        // Path breakdown
        pathBreakdownArea.setEditable(false);
        pathBreakdownArea.setBackground(new Color(8, 18, 30));
        pathBreakdownArea.setForeground(new Color(180, 200, 255));
        pathBreakdownArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        pathBreakdownArea.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(40, 60, 120)),
            "Path Cost Breakdown", 0, 0,
            new Font("SansSerif", Font.BOLD, 10), new Color(100, 140, 255)));
        JScrollPane bdScroll = new JScrollPane(pathBreakdownArea);

        p.add(logScroll, BorderLayout.WEST);
        p.add(bdScroll,  BorderLayout.CENTER);
        return p;
    }

    private void refreshDSALog() {
        java.util.Deque<String> log = engine.getDSALog();
        StringBuilder sb = new StringBuilder();
        for (String line : log) sb.append(line).append("\n");
        dsaLogArea.setText(sb.toString());
        dsaLogArea.setCaretPosition(dsaLogArea.getDocument().getLength());
    }

    private void refreshPathBreakdown() {
        PathCostBreakdown bd = engine.getPathCostBreakdown();
        if (bd.hops.isEmpty()) { pathBreakdownArea.setText("No path selected."); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("Zone: ").append(bd.zoneWeightsUsed).append("\n");
        sb.append("Path: ").append(bd.pathString).append("\n");
        sb.append(String.format("%-14s %6s %6s %6s %6s %7s\n",
            "Hop", "Base", "CO₂", "Risk", "Lat", "Total"));
        sb.append("─".repeat(50)).append("\n");
        for (PathCostBreakdown.HopCost h : bd.hops) {
            sb.append(String.format("%-14s %6.2f %6.3f %6.3f %6.2f %7.3f\n",
                h.fromLabel + "→" + h.toLabel,
                h.baseCost, h.carbonCost, h.failureRisk, h.latency, h.effectiveCost));
        }
        sb.append("─".repeat(50)).append("\n");
        sb.append(String.format("%-14s %6.2f %6.3f %6.3f %6.2f %7.3f\n",
            "TOTAL",
            bd.totalBaseCost, bd.totalCarbonCost,
            bd.totalFailureRisk, bd.totalLatency, bd.totalEffectiveCost));
        pathBreakdownArea.setText(sb.toString());
    }

    // -------------------------------------------------------------------------
    // Prediction widget
    // -------------------------------------------------------------------------

    private JPanel buildPredictionWidget() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(20, 15, 35));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 50, 150)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel title = new JLabel("⚠ Next Predicted Failure");
        title.setForeground(new Color(220, 150, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        p.add(title); p.add(vgap(3));

        predBar.setForeground(new Color(220, 60, 60));
        predBar.setBackground(new Color(40, 20, 40));
        predBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        predBar.setStringPainted(false);

        p.add(predRow("Node:", predNodeLabel));
        p.add(predRow("Risk:", predRiskLabel));
        p.add(predBar);
        p.add(predRow("History:", predHistLabel));
        return p;
    }

    private JPanel predRow(String key, JLabel val) {
        JPanel r = new JPanel(new BorderLayout(4, 0));
        r.setBackground(new Color(20, 15, 35));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel k = new JLabel(key);
        k.setForeground(new Color(160, 120, 200));
        k.setFont(new Font("SansSerif", Font.PLAIN, 10));
        val.setForeground(new Color(220, 200, 255));
        val.setFont(new Font("SansSerif", Font.BOLD, 10));
        r.add(k, BorderLayout.WEST); r.add(val, BorderLayout.EAST);
        return r;
    }

    private void refreshPredictionWidget() {
        int nodeId = engine.predictNextFailureNode();
        if (nodeId == -1) {
            predNodeLabel.setText("—"); predRiskLabel.setText("—");
            predHistLabel.setText("0 events"); predBar.setValue(0);
        } else {
            Node n = engine.getGraph().getNode(nodeId);
            String name = n != null ? n.label : "#" + nodeId;
            double risk = engine.getPredictionEngine().getRiskScore(nodeId);
            int pct = (int) Math.min(100, risk * 200);
            predNodeLabel.setText(name);
            predRiskLabel.setText(String.format("%.0f%%  ", risk * 100));
            predHistLabel.setText(engine.getPredictionEngine().getWindowSize() + " events");
            predBar.setValue(pct);
        }
    }

    // -------------------------------------------------------------------------
    // Segment Tree panel
    // -------------------------------------------------------------------------

    private JPanel buildSegTreePanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(15, 25, 20));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(30, 100, 60)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel title = new JLabel("Segment Tree — Zone Loads");
        title.setForeground(new Color(80, 220, 120));
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        p.add(title); p.add(vgap(3));

        List<Node> zones = engine.getZoneNodes();
        for (int i = 0; i < 5; i++) {
            String lbl = i < zones.size() ? zones.get(i).label : "Z" + (i+1);
            segTreeLabels[i] = new JLabel(lbl + ": 0 MW");
            segTreeLabels[i].setForeground(new Color(140, 220, 160));
            segTreeLabels[i].setFont(new Font("Monospaced", Font.PLAIN, 9));
            p.add(segTreeLabels[i]);
        }
        p.add(vgap(2));
        segTreeMaxLabel.setForeground(new Color(255, 200, 80));
        segTreeMaxLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        segTreeSumLabel.setForeground(new Color(100, 200, 255));
        segTreeSumLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        segTreeMinLabel.setForeground(new Color(180, 255, 180));
        segTreeMinLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        p.add(segTreeMaxLabel);
        p.add(segTreeSumLabel);
        p.add(segTreeMinLabel);
        return p;
    }

    private void refreshSegTreePanel() {
        List<Node> zones = engine.getZoneNodes();
        for (int i = 0; i < segTreeLabels.length; i++) {
            if (segTreeLabels[i] == null) continue;
            int load = i < zones.size() ? zones.get(i).currentLoad : 0;
            String lbl = i < zones.size() ? zones.get(i).label : "Z" + (i+1);
            segTreeLabels[i].setText(lbl + ": " + load + " MW");
        }
        int maxIdx = engine.queryMaxLoadZone();
        int sumAll = engine.queryEnergyRange(1, 5);
        int minLoad = engine.queryMaxLoad(2, 4);
        String maxLabel = maxIdx > 0 && maxIdx <= zones.size() ? zones.get(maxIdx-1).label : "—";
        int maxLoadVal = maxIdx > 0 ? engine.queryMaxLoad(maxIdx, maxIdx) : 0;
        segTreeMaxLabel.setText("Max[1-5]: " + maxLabel + " (" + maxLoadVal + " MW)");
        segTreeSumLabel.setText("Sum[1-5]: " + sumAll + " MW");
        segTreeMinLabel.setText("Max[2-4]: " + minLoad + " MW");
    }

    // -------------------------------------------------------------------------
    // CO₂ dashboard
    // -------------------------------------------------------------------------

    private JPanel buildCO2Panel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(15, 25, 15));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(30, 100, 30)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel title = new JLabel("CO₂ Dashboard (GreenTech)");
        title.setForeground(new Color(80, 220, 80));
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        p.add(title); p.add(vgap(3));

        co2RenewLabel.setForeground(new Color(100, 255, 100));
        co2RenewLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        co2TotalLabel.setForeground(new Color(200, 255, 150));
        co2TotalLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        co2SavedLabel.setForeground(new Color(150, 255, 200));
        co2SavedLabel.setFont(new Font("Monospaced", Font.BOLD, 9));
        p.add(co2RenewLabel);
        p.add(co2TotalLabel);
        p.add(co2SavedLabel);
        return p;
    }

    private void refreshCO2Panel() {
        SimStats s = engine.getStats();
        co2RenewLabel.setText(String.format("Renewable: %.0f%%", s.renewableFraction * 100));
        co2TotalLabel.setText(String.format("CO₂ session: %.2f kg", s.sessionCarbonKg));
        co2SavedLabel.setText(String.format("Saved vs coal: %.2f kg  ← BIT", s.co2SavedKg));
    }

    // -------------------------------------------------------------------------
    // Banner
    // -------------------------------------------------------------------------

    /** Shows a 2-second algorithm banner in the top-right. */
    public void showBanner(String text) {
        bannerLabel.setText("  " + text + "  ");
        if (bannerTimer != null) bannerTimer.stop();
        bannerTimer = new javax.swing.Timer(2000, e -> bannerLabel.setText(" "));
        bannerTimer.setRepeats(false);
        bannerTimer.start();
    }

    // -------------------------------------------------------------------------
    // Demo Mode
    // -------------------------------------------------------------------------

    private void runDemoMode() {
        javax.swing.Timer t = new javax.swing.Timer(0, null);
        final int[] step = {0};
        t.addActionListener(e -> {
            switch (step[0]++) {
                case 0:
                    showBanner("▶ SPFA Min-Cost Max-Flow");
                    engine.runNormalDistribution(); refresh(); break;
                case 1: break; // pause
                case 2:
                    // Click Hospital zone
                    Node hospital = null;
                    for (Node n : engine.getGraph().getNodes())
                        if (n.zoneClass == ZoneClassification.HOSPITAL) { hospital = n; break; }
                    if (hospital != null) {
                        showBanner("▶ Dijkstra [Hospital priority] + Pareto paths");
                        engine.runDijkstraSmartClick(hospital.id);
                        detailPanel.showNode(hospital);
                        refresh();
                    }
                    break;
                case 3: break;
                case 4:
                    // Inject fault on Sub-B (id=5)
                    showBanner("▶ BFS → Union-Find → DFS → Bellman-Ford");
                    engine.injectFault(5); faultPending = true; refresh(); break;
                case 5: break;
                case 6:
                    showBanner("▶ SPFA rerouting");
                    engine.autoReroute(); faultPending = false; refresh(); break;
                case 7: break;
                case 8:
                    showBanner("▶ Sliding Window + Max-Heap predictor");
                    runPrediction(); refresh(); break;
                case 9: break;
                case 10:
                    showBanner("▶ Adaptive Mode — edge costs self-adjusting");
                    engine.setAdaptiveMode(true); btnAdaptive.setSelected(true);
                    btnAdaptive.setText("Adaptive: ON");
                    engine.runNormalDistribution(); refresh(); break;
                case 11:
                    engine.runNormalDistribution(); refresh(); break;
                case 12: break;
                case 13:
                    // Trigger load shedding manually
                    showBanner("▶ Bellman-Ford → Topological Sort load shedding");
                    Node src = null;
                    for (Node n : engine.getGraph().getNodes())
                        if (n.type == NodeType.ENERGY_SOURCE && n.status == NodeStatus.ACTIVE) { src = n; break; }
                    if (src != null) {
                        BellmanFordAlgorithm.Result bf = engine.runBellmanFord(src.id);
                        engine.runTopologicalSort();
                        refresh();
                    }
                    break;
                case 14: break;
                default:
                    t.stop();
                    showBanner("✓ Demo complete");
                    break;
            }
        });
        t.setDelay(3000);
        t.setInitialDelay(0);
        t.start();
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
