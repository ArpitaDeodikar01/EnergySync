package smartgrid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * DSA Role: Java Swing view for the SmartGrid simulation.
 * Pure view — owns no simulation state. All actions delegate to GridSimulator.
 *
 * Layout:
 *   LEFT  — GraphCanvas (custom JPanel, Graphics2D drawing)
 *   RIGHT — Control panel with 4 JButtons
 *   BOTTOM — Stats bar JLabel
 *
 * Rendering conventions:
 *   GREEN  = NodeStatus.ACTIVE
 *   RED    = NodeStatus.FAULT or ISOLATED
 *   ORANGE = NodeStatus.REROUTED
 *   BLUE   = Dijkstra path highlight
 */
public class GridUI extends JFrame {

    private static final int NODE_RADIUS = 22;

    private final GridSimulator simulator;
    private final GraphCanvas canvas;
    private final JLabel statsBar;

    /**
     * Constructs the main window, wires all components, and makes the frame visible.
     *
     * @param simulator the shared simulation engine
     */
    public GridUI(GridSimulator simulator) {
        super("SmartGrid — Graph-Based Adaptive Energy Distribution");
        this.simulator = simulator;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- Left: graph canvas ---
        canvas = new GraphCanvas();
        canvas.setPreferredSize(new Dimension(660, 540));
        canvas.setBackground(new Color(20, 20, 35)); // dark background for contrast
        add(canvas, BorderLayout.CENTER);

        // --- Right: control panel ---
        JPanel controlPanel = buildControlPanel();
        add(controlPanel, BorderLayout.EAST);

        // --- Bottom: stats bar ---
        statsBar = new JLabel(" Total Energy: 0   Cost: 0   Zones Affected: 0   Recovery: 0 ms   Flow Loss: 0.0%");
        statsBar.setFont(new Font("Monospaced", Font.PLAIN, 13));
        statsBar.setForeground(Color.WHITE);
        statsBar.setBackground(new Color(30, 30, 50));
        statsBar.setOpaque(true);
        statsBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(statsBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Control Panel
    // -------------------------------------------------------------------------

    /**
     * Builds the right-side control panel with 4 simulation buttons.
     * Each button delegates to the corresponding GridSimulator method,
     * then repaints the canvas and updates the stats bar.
     */
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(30, 30, 50));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 12, 20, 12));
        panel.setPreferredSize(new Dimension(190, 540));

        JLabel title = new JLabel("Controls");
        title.setForeground(Color.LIGHT_GRAY);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(20));

        // [Run Normal Distribution] — triggers MCMF on the full graph
        JButton btnRun = makeButton("Run Normal Distribution", new Color(34, 139, 34));
        btnRun.addActionListener(e -> {
            simulator.runNormalDistribution();
            updateStatsBar();
            canvas.repaint();
        });

        // [Inject Fault] — removes Solar node, runs BFS + DFS
        JButton btnFault = makeButton("Inject Fault", new Color(180, 40, 40));
        btnFault.addActionListener(e -> {
            simulator.injectFault();
            updateStatsBar();
            canvas.repaint();
        });

        // [Auto Reroute] — re-runs MCMF excluding isolated nodes
        JButton btnReroute = makeButton("Auto Reroute", new Color(200, 120, 0));
        btnReroute.addActionListener(e -> {
            simulator.autoReroute();
            updateStatsBar();
            canvas.repaint();
        });

        // [Reset] — rebuilds topology, clears FenwickTree and MinHeap
        JButton btnReset = makeButton("Reset", new Color(60, 60, 120));
        btnReset.addActionListener(e -> {
            simulator.reset();
            updateStatsBar();
            canvas.repaint();
        });

        panel.add(btnRun);
        panel.add(Box.createVerticalStrut(12));
        panel.add(btnFault);
        panel.add(Box.createVerticalStrut(12));
        panel.add(btnReroute);
        panel.add(Box.createVerticalStrut(12));
        panel.add(btnReset);

        // Legend
        panel.add(Box.createVerticalStrut(30));
        panel.add(makeLegend());

        return panel;
    }

    /** Creates a styled button with the given label and background color */
    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton("<html><center>" + text + "</center></html>");
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(165, 48));
        btn.setPreferredSize(new Dimension(165, 48));
        return btn;
    }

    /** Builds a small color legend panel */
    private JPanel makeLegend() {
        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setBackground(new Color(30, 30, 50));
        legend.add(legendRow(new Color(34, 139, 34), "Active"));
        legend.add(Box.createVerticalStrut(4));
        legend.add(legendRow(Color.RED,              "Fault / Isolated"));
        legend.add(Box.createVerticalStrut(4));
        legend.add(legendRow(Color.ORANGE,           "Rerouted"));
        legend.add(Box.createVerticalStrut(4));
        legend.add(legendRow(new Color(30, 144, 255),"Dijkstra Path"));
        return legend;
    }

    private JPanel legendRow(Color color, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(new Color(30, 30, 50));
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        JLabel txt = new JLabel(label);
        txt.setForeground(Color.LIGHT_GRAY);
        txt.setFont(new Font("SansSerif", Font.PLAIN, 11));
        row.add(dot);
        row.add(txt);
        return row;
    }

    // -------------------------------------------------------------------------
    // Stats Bar
    // -------------------------------------------------------------------------

    /**
     * Reads SimStats from the simulator and updates the bottom stats bar label.
     * Called after every button action.
     */
    private void updateStatsBar() {
        SimStats s = simulator.getStats();
        List<Node> path = simulator.getDijkstraPath();

        String pathInfo = "";
        if (!path.isEmpty()) {
            // Show the path as a sequence of node labels
            StringBuilder sb = new StringBuilder("  Path: ");
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).label);
                if (i < path.size() - 1) sb.append(" → ");
            }
            pathInfo = sb.toString();
        }

        statsBar.setText(String.format(
            " Energy: %d   Cost: %d   Zones Affected: %d   Recovery: %d ms   Flow Loss: %.1f%%%s",
            s.totalEnergyRouted, s.totalCost, s.zonesAffected, s.recoveryTimeMs, s.flowLossPercent, pathInfo
        ));
    }

    // -------------------------------------------------------------------------
    // Graph Canvas
    // -------------------------------------------------------------------------

    /**
     * DSA Role: Custom JPanel that renders the energy network graph using Graphics2D.
     * Draws nodes as colored circles and edges as directed arrows with capacity/cost labels.
     * Handles mouse clicks to trigger Dijkstra path highlighting.
     */
    private class GraphCanvas extends JPanel {

        GraphCanvas() {
            // Mouse click listener: find nearest node and run Dijkstra from it
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Find the node whose center is closest to the click point
                    // Time complexity: O(V) — linear scan over all nodes
                    Node nearest = null;
                    double minDist = Double.MAX_VALUE;
                    for (Node n : simulator.getGraph().getNodes()) {
                        double d = Math.hypot(n.x - e.getX(), n.y - e.getY());
                        if (d < minDist) {
                            minDist = d;
                            nearest = n;
                        }
                    }

                    // Only trigger Dijkstra if click is within NODE_RADIUS of a node center
                    if (nearest != null && minDist <= NODE_RADIUS * 2.5) {
                        List<Node> path = simulator.runDijkstra(nearest.id);
                        if (path.isEmpty()) {
                            statsBar.setText(" No path available from " + nearest.label);
                        } else {
                            updateStatsBar();
                        }
                        repaint();
                    }
                }
            });
        }

        /**
         * Renders the full graph: edges first (so nodes draw on top), then nodes.
         * Highlights the Dijkstra path in blue.
         * Time complexity: O(V + E) — visits every node and edge once
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            // Enable anti-aliasing for smoother circles and lines
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Graph graph = simulator.getGraph();
            List<Node> dijkPath = simulator.getDijkstraPath();

            // Build a set of consecutive (from, to) pairs in the Dijkstra path for O(1) lookup
            java.util.Set<String> pathEdges = new java.util.HashSet<>();
            for (int i = 0; i < dijkPath.size() - 1; i++) {
                pathEdges.add(dijkPath.get(i).id + "," + dijkPath.get(i + 1).id);
            }

            // --- Draw edges ---
            for (Node src : graph.getNodes()) {
                for (Edge e : graph.getNeighbors(src.id)) {
                    Node dst = graph.getNode(e.to);
                    if (dst == null) continue;

                    boolean isPathEdge = pathEdges.contains(src.id + "," + dst.id);

                    if (isPathEdge) {
                        // Dijkstra path: thick blue arrow
                        g2.setColor(new Color(30, 144, 255));
                        g2.setStroke(new BasicStroke(3f));
                    } else {
                        // Normal edge: thin gray arrow
                        g2.setColor(new Color(120, 120, 160));
                        g2.setStroke(new BasicStroke(1.5f));
                    }

                    drawArrow(g2, src.x, src.y, dst.x, dst.y);

                    // Draw capacity/cost label at the midpoint of the edge
                    int mx = (src.x + dst.x) / 2;
                    int my = (src.y + dst.y) / 2;
                    g2.setColor(new Color(200, 200, 200));
                    g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                    g2.drawString(e.capacity + "/" + e.cost, mx + 4, my - 4);
                }
            }

            // --- Draw nodes ---
            for (Node n : graph.getNodes()) {
                // Choose fill color based on NodeStatus
                Color fill;
                switch (n.status) {
                    case FAULT:
                    case ISOLATED: fill = new Color(200, 40, 40);   break;
                    case REROUTED: fill = new Color(220, 130, 0);   break;
                    default:       fill = new Color(34, 139, 34);   break; // ACTIVE
                }

                // Highlight nodes on the Dijkstra path with a blue tint
                boolean onPath = dijkPath.stream().anyMatch(p -> p.id == n.id);
                if (onPath) fill = new Color(30, 100, 220);

                // Draw filled circle
                g2.setColor(fill);
                g2.fillOval(n.x - NODE_RADIUS, n.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

                // Draw circle border
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(n.x - NODE_RADIUS, n.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

                // Draw node label centered inside the circle
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(n.label);
                g2.setColor(Color.WHITE);
                g2.drawString(n.label, n.x - tw / 2, n.y + fm.getAscent() / 2 - 1);

                // Draw node type tag below the circle
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(new Color(180, 180, 180));
                String tag = n.type == NodeType.ENERGY_SOURCE ? "src" :
                             n.type == NodeType.SUBSTATION     ? "sub" : "zone";
                g2.drawString(tag, n.x - 8, n.y + NODE_RADIUS + 12);
            }
        }

        /**
         * Draws a directed arrow from (x1,y1) to (x2,y2) with an arrowhead.
         * Offsets the endpoints by NODE_RADIUS so arrows start/end at circle edges.
         */
        private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
            // Compute direction vector and offset endpoints to circle edges
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len == 0) return;
            double ux = dx / len, uy = dy / len;

            // Start and end points offset by node radius
            int sx = (int)(x1 + ux * NODE_RADIUS);
            int sy = (int)(y1 + uy * NODE_RADIUS);
            int ex = (int)(x2 - ux * NODE_RADIUS);
            int ey = (int)(y2 - uy * NODE_RADIUS);

            // Draw the line
            g2.drawLine(sx, sy, ex, ey);

            // Draw arrowhead as a small filled triangle
            double arrowLen = 10;
            double arrowAngle = Math.PI / 7;
            double angle = Math.atan2(ey - sy, ex - sx);

            int ax1 = (int)(ex - arrowLen * Math.cos(angle - arrowAngle));
            int ay1 = (int)(ey - arrowLen * Math.sin(angle - arrowAngle));
            int ax2 = (int)(ex - arrowLen * Math.cos(angle + arrowAngle));
            int ay2 = (int)(ey - arrowLen * Math.sin(angle + arrowAngle));

            int[] xPts = {ex, ax1, ax2};
            int[] yPts = {ey, ay1, ay2};
            g2.fillPolygon(xPts, yPts, 3);
        }
    }
}
