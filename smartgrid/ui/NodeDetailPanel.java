package smartgrid.ui;

import smartgrid.model.*;
import smartgrid.services.SimulationEngine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * Side panel that displays detailed information about the currently selected node.
 * Updated by GraphCanvas whenever the user clicks a node.
 * Pure view — reads from model objects, never mutates them.
 */
public class NodeDetailPanel extends JPanel {

    private static final Color BG      = new Color(22, 22, 40);
    private static final Color FG      = new Color(210, 210, 230);
    private static final Color FG_DIM  = new Color(130, 130, 160);
    private static final Color ACCENT  = new Color(80, 180, 255);

    private final SimulationEngine engine;

    // Labels updated on selection
    private final JLabel lblName        = detail("");
    private final JLabel lblType        = detail("");
    private final JLabel lblStatus      = detail("");
    private final JLabel lblLoad        = detail("");
    private final JLabel lblCapacity    = detail("");
    private final JLabel lblFailProb    = detail("");
    private final JLabel lblCarbon      = detail("");
    private final JLabel lblRisk        = detail("");
    private final JLabel lblReliability = detail("");
    private final JLabel lblZoneClass   = detail("");
    private final JLabel lblPriority    = detail("");
    private final JTextArea txtNeighbors;

    public NodeDetailPanel(SimulationEngine engine) {
        this.engine = engine;
        setBackground(BG);
        setPreferredSize(new Dimension(200, 0));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 100)),
                "Node Detail");
        border.setTitleColor(ACCENT);
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        setBorder(border);

        txtNeighbors = new JTextArea(3, 14);
        txtNeighbors.setEditable(false);
        txtNeighbors.setBackground(new Color(30, 30, 50));
        txtNeighbors.setForeground(FG_DIM);
        txtNeighbors.setFont(new Font("Monospaced", Font.PLAIN, 10));
        txtNeighbors.setLineWrap(true);
        txtNeighbors.setWrapStyleWord(true);

        add(row("Name",        lblName));
        add(row("Type",        lblType));
        add(row("Status",      lblStatus));
        add(vgap(6));
        add(row("Load",        lblLoad));
        add(row("Capacity",    lblCapacity));
        add(vgap(6));
        add(row("Fail Prob",   lblFailProb));
        add(row("Reliability", lblReliability));
        add(row("Risk Score",  lblRisk));
        add(vgap(6));
        add(row("Carbon",      lblCarbon));
        add(vgap(8));
        add(sectionLabel("Zone Classification"));
        add(row("Type",        lblZoneClass));
        add(row("Priority",    lblPriority));
        add(vgap(8));
        add(sectionLabel("Neighbors"));
        JScrollPane scroll = new JScrollPane(txtNeighbors);
        scroll.setBackground(BG);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 80)));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        add(scroll);

        showEmpty();
    }

    /** Called by GraphCanvas when a node is clicked. */
    public void showNode(Node node) {
        lblName.setText(node.label);
        lblType.setText(node.type.name());
        lblStatus.setText(node.status.name());
        lblStatus.setForeground(statusColor(node.status));

        int cap = node.capacity > 0 ? node.capacity : -1;
        lblLoad.setText(node.currentLoad + (cap > 0 ? " / " + cap : ""));
        lblCapacity.setText(cap > 0 ? String.valueOf(cap) : "—");

        lblFailProb.setText(String.format("%.1f%%", node.failureProbability * 100));
        lblReliability.setText(String.format("%.2f", node.reliabilityScore));

        double risk = engine.getPredictionEngine().getRiskScore(node.id);
        lblRisk.setText(String.format("%.3f", risk));
        lblRisk.setForeground(riskColor(risk));

        lblCarbon.setText(String.format("%.3f kg/u", node.carbonFactor));

        // Zone classification info
        if (node.zoneClass != null) {
            lblZoneClass.setText(node.zoneClass.displayName);
            lblZoneClass.setForeground(node.zoneClass.color);
            lblPriority.setText(node.zoneClass.description);
            lblPriority.setFont(new Font("SansSerif", Font.ITALIC, 9));
        } else {
            lblZoneClass.setText("—");
            lblZoneClass.setForeground(FG_DIM);
            lblPriority.setText("");
        }

        // Neighbors
        List<smartgrid.model.Edge> neighbors = engine.getGraph().getNeighbors(node.id);
        if (neighbors.isEmpty()) {
            txtNeighbors.setText("none");
        } else {
            StringBuilder sb = new StringBuilder();
            for (smartgrid.model.Edge e : neighbors) {
                Node dst = engine.getGraph().getNode(e.to);
                String name = dst != null ? dst.label : "?" + e.to;
                sb.append(name)
                  .append(" (cap=").append(e.capacity)
                  .append(", flow=").append(e.flow).append(")\n");
            }
            txtNeighbors.setText(sb.toString().trim());
        }
    }

    /** Clears the panel when nothing is selected. */
    public void showEmpty() {
        lblName.setText("—");
        lblType.setText("—");
        lblStatus.setText("—");
        lblStatus.setForeground(FG_DIM);
        lblLoad.setText("—");
        lblCapacity.setText("—");
        lblFailProb.setText("—");
        lblReliability.setText("—");
        lblRisk.setText("—");
        lblRisk.setForeground(FG_DIM);
        lblCarbon.setText("—");
        lblZoneClass.setText("—");
        lblZoneClass.setForeground(FG_DIM);
        lblPriority.setText("");
        txtNeighbors.setText("");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JPanel row(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel k = new JLabel(key + ":");
        k.setForeground(FG_DIM);
        k.setFont(new Font("SansSerif", Font.PLAIN, 11));
        value.setFont(new Font("SansSerif", Font.BOLD, 11));
        row.add(k, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private JLabel detail(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG);
        return l;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Component vgap(int h) {
        return Box.createVerticalStrut(h);
    }

    private Color statusColor(NodeStatus s) {
        switch (s) {
            case FAULT:
            case ISOLATED: return new Color(255, 80, 80);
            case REROUTED: return new Color(255, 180, 0);
            default:       return new Color(80, 220, 80);
        }
    }

    private Color riskColor(double risk) {
        if (risk > 0.5) return new Color(255, 80, 80);
        if (risk > 0.2) return new Color(255, 200, 0);
        return FG_DIM;
    }
}
