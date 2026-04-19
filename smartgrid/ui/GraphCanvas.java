package smartgrid.ui;

import smartgrid.model.*;
import smartgrid.services.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Custom JPanel that renders the energy network graph using Graphics2D.
 *
 * Node coloring:
 *   Green  — ACTIVE, normal load/risk
 *   Yellow — ACTIVE, high load or high failure risk
 *   Red    — FAULT or ISOLATED
 *   Orange — REROUTED
 *   Blue   — on Dijkstra path
 *
 * Edge rendering:
 *   Heatmap band  : semi-transparent band whose color shifts blue→red with congestion
 *   Flow animation: small dots travel along edges carrying flow (javax.swing.Timer)
 *   Dijkstra path : thick solid blue arrow
 *
 * Selection: clicking a node fires NodeSelectionListener so NodeDetailPanel
 * can update without GraphCanvas holding a direct reference to it.
 */
public class GraphCanvas extends JPanel {

    private static final int    NODE_RADIUS    = 22;
    private static final int    ANIM_INTERVAL  = 50;
    private static final float  ANIM_SPEED     = 3.0f;
    private static final double HIGH_LOAD_FRAC = 0.75;
    private static final double HIGH_RISK_THR  = 0.3;

    private final SimulationEngine engine;
    private final StatsBarUpdater  statsBarUpdater;

    public interface NodeSelectionListener {
        void onNodeSelected(Node node);
    }

    public interface StatsBarUpdater {
        void update();
        void setText(String text);
    }

    private NodeSelectionListener selectionListener;
    private float animPhase = 0f;
    private final Timer animTimer;

    public GraphCanvas(SimulationEngine engine, StatsBarUpdater statsBarUpdater) {
        this.engine = engine;
        this.statsBarUpdater = statsBarUpdater;
        setPreferredSize(new Dimension(660, 560));
        setBackground(new Color(18, 18, 32));

        animTimer = new Timer(ANIM_INTERVAL, e -> {
            animPhase = (animPhase + ANIM_SPEED) % 100f;
            repaint();
        });
        animTimer.start();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Node nearest = null;
                double minDist = Double.MAX_VALUE;
                for (Node n : engine.getGraph().getNodes()) {
                    double d = Math.hypot(n.x - e.getX(), n.y - e.getY());
                    if (d < minDist) { minDist = d; nearest = n; }
                }
                if (nearest != null && minDist <= NODE_RADIUS * 2.5) {
                    if (selectionListener != null) selectionListener.onNodeSelected(nearest);
                    // Smart click: zones show incoming supply path; sources/subs show outgoing path
                    List<Node> path = engine.runDijkstraSmartClick(nearest.id);
                    if (path.isEmpty()) {
                        statsBarUpdater.setText(
                            "<html><font color='#ff8888'> No path available for "
                            + nearest.label + "</font></html>");
                    } else {
                        statsBarUpdater.update();
                    }
                    repaint();
                }
            }
        });
    }

    public void setNodeSelectionListener(NodeSelectionListener l) { this.selectionListener = l; }

    // -------------------------------------------------------------------------
    // Paint
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Graph graph = engine.getGraph();
        List<Node> dijkPath = engine.getDijkstraPath();

        java.util.Set<String> pathEdges = new java.util.HashSet<>();
        for (int i = 0; i < dijkPath.size() - 1; i++)
            pathEdges.add(dijkPath.get(i).id + "," + dijkPath.get(i + 1).id);

        drawHeatmapBands(g2, graph);
        drawEdges(g2, graph, pathEdges);
        drawFlowAnimation(g2, graph);
        drawNodes(g2, graph, dijkPath);
    }

    // -------------------------------------------------------------------------
    // Heatmap bands
    // -------------------------------------------------------------------------

    private void drawHeatmapBands(Graphics2D g2, Graph graph) {
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Node src : graph.getNodes()) {
            for (Edge e : graph.getNeighbors(src.id)) {
                if (e.capacity <= 0 || e.flow <= 0) continue;
                double cong = Math.min(1.0, (double) e.flow / e.capacity);
                if (cong < 0.05) continue;
                Node dst = graph.getNode(e.to);
                if (dst == null) continue;
                int[] pts = edgeEndpoints(src, dst);
                g2.setColor(heatColor(cong, 90));
                g2.drawLine(pts[0], pts[1], pts[2], pts[3]);
            }
        }
        g2.setStroke(old);
    }

    // -------------------------------------------------------------------------
    // Edge arrows
    // -------------------------------------------------------------------------

    private void drawEdges(Graphics2D g2, Graph graph, java.util.Set<String> pathEdges) {
        for (Node src : graph.getNodes()) {
            for (Edge e : graph.getNeighbors(src.id)) {
                Node dst = graph.getNode(e.to);
                if (dst == null) continue;
                boolean isPath = pathEdges.contains(src.id + "," + dst.id);
                if (isPath) {
                    g2.setColor(new Color(30, 144, 255));
                    g2.setStroke(new BasicStroke(3f));
                } else {
                    g2.setColor(new Color(100, 100, 140));
                    g2.setStroke(new BasicStroke(1.5f));
                }
                drawArrow(g2, src.x, src.y, dst.x, dst.y);
                int mx = (src.x + dst.x) / 2, my = (src.y + dst.y) / 2;
                g2.setColor(new Color(180, 180, 200));
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.drawString(e.flow + "/" + e.capacity, mx + 4, my - 4);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Flow animation dots
    // -------------------------------------------------------------------------

    private void drawFlowAnimation(Graphics2D g2, Graph graph) {
        for (Node src : graph.getNodes()) {
            for (Edge e : graph.getNeighbors(src.id)) {
                if (e.flow <= 0) continue;
                Node dst = graph.getNode(e.to);
                if (dst == null) continue;
                double dx = dst.x - src.x, dy = dst.y - src.y;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len == 0) continue;
                double ux = dx / len, uy = dy / len;
                int sx = (int)(src.x + ux * NODE_RADIUS);
                int sy = (int)(src.y + uy * NODE_RADIUS);
                double usable = len - 2 * NODE_RADIUS;
                if (usable <= 0) continue;
                double spacing = Math.max(12, 40.0 / Math.max(1, e.flow));
                g2.setColor(new Color(160, 255, 160, 180));
                double offset = animPhase % spacing;
                for (double d = offset; d < usable; d += spacing) {
                    int px = (int)(sx + ux * d), py = (int)(sy + uy * d);
                    g2.fillOval(px - 3, py - 3, 6, 6);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Nodes
    // -------------------------------------------------------------------------

    private void drawNodes(Graphics2D g2, Graph graph, List<Node> dijkPath) {
        java.util.Set<Integer> pathIds = new java.util.HashSet<>();
        for (Node p : dijkPath) pathIds.add(p.id);

        for (Node n : graph.getNodes()) {
            double risk = engine.getPredictionEngine().getRiskScore(n.id);

            // Risk glow (red halo for high-risk nodes)
            if (risk > HIGH_RISK_THR && n.status == NodeStatus.ACTIVE) {
                int glowR = NODE_RADIUS + 7 + (int)(risk * 6);
                g2.setColor(new Color(255, 80, 80, 45));
                g2.fillOval(n.x - glowR, n.y - glowR, glowR * 2, glowR * 2);
            }

            // Dijkstra path glow (bright blue halo)
            if (pathIds.contains(n.id)) {
                g2.setColor(new Color(60, 160, 255, 70));
                g2.fillOval(n.x - NODE_RADIUS - 6, n.y - NODE_RADIUS - 6,
                        (NODE_RADIUS + 6) * 2, (NODE_RADIUS + 6) * 2);
            }

            Color fill = nodeColor(n, dijkPath, risk);
            g2.setColor(fill);
            g2.fillOval(n.x - NODE_RADIUS, n.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

            // Zone classification ring
            if (n.type == NodeType.CITY_ZONE && n.zoneClass != null) {
                g2.setColor(n.zoneClass.color);
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(n.x - NODE_RADIUS - 4, n.y - NODE_RADIUS - 4,
                        (NODE_RADIUS + 4) * 2, (NODE_RADIUS + 4) * 2);
            }

            // Node border — thicker + cyan for path nodes
            if (pathIds.contains(n.id)) {
                g2.setColor(new Color(100, 200, 255));
                g2.setStroke(new BasicStroke(3f));
            } else {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
            }
            g2.drawOval(n.x - NODE_RADIUS, n.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

            // Label
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(n.label);
            g2.setColor(Color.WHITE);
            g2.drawString(n.label, n.x - tw / 2, n.y + fm.getAscent() / 2 - 1);

            // Zone classification label below node
            if (n.type == NodeType.CITY_ZONE && n.zoneClass != null) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(n.zoneClass.color);
                String classLabel = n.zoneClass.displayName;
                int labelW = g2.getFontMetrics().stringWidth(classLabel);
                g2.drawString(classLabel, n.x - labelW / 2, n.y + NODE_RADIUS + 14);
            } else {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(new Color(160, 160, 180));
                String tag = n.type == NodeType.ENERGY_SOURCE ? "src"
                           : n.type == NodeType.SUBSTATION     ? "sub" : "zone";
                g2.drawString(tag, n.x - 8, n.y + NODE_RADIUS + 12);
            }

            if (n.type == NodeType.CITY_ZONE && n.capacity > 0) drawLoadBar(g2, n);
            drawRiskBar(g2, n, risk);
        }
    }

    private void drawLoadBar(Graphics2D g2, Node n) {
        int bw = NODE_RADIUS * 2, bh = 4;
        int bx = n.x - NODE_RADIUS, by = n.y + NODE_RADIUS + 16;
        double frac = Math.min(1.0, (double) n.currentLoad / n.capacity);
        g2.setColor(new Color(40, 40, 60));
        g2.fillRect(bx, by, bw, bh);
        Color c = frac < 0.5 ? new Color(50, 200, 80)
                : frac < 0.8 ? new Color(220, 200, 0)
                             : new Color(220, 60, 60);
        g2.setColor(c);
        g2.fillRect(bx, by, (int)(bw * frac), bh);
    }

    /** Draws a small risk bar below the load bar (or below the node for non-zones). */
    private void drawRiskBar(Graphics2D g2, Node n, double risk) {
        if (risk <= 0.01) return;
        int bw = NODE_RADIUS * 2, bh = 3;
        int bx = n.x - NODE_RADIUS;
        // Place below load bar if zone with capacity, else just below node label
        int by = (n.type == NodeType.CITY_ZONE && n.capacity > 0)
                ? n.y + NODE_RADIUS + 22
                : n.y + NODE_RADIUS + 16;
        double frac = Math.min(1.0, risk / 0.8); // saturate at 0.8
        g2.setColor(new Color(60, 20, 20));
        g2.fillRect(bx, by, bw, bh);
        int r = (int)(255 * frac), gr = (int)(80 * (1 - frac));
        g2.setColor(new Color(r, gr, 40));
        g2.fillRect(bx, by, (int)(bw * frac), bh);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Color nodeColor(Node n, List<Node> dijkPath, double risk) {
        if (dijkPath.stream().anyMatch(p -> p.id == n.id)) return new Color(30, 100, 220);
        switch (n.status) {
            case FAULT:
            case ISOLATED: return new Color(200, 40, 40);
            case REROUTED: return new Color(220, 130, 0);
            default: break;
        }
        if (risk > HIGH_RISK_THR) return new Color(210, 190, 0);
        if (n.capacity > 0 && (double) n.currentLoad / n.capacity >= HIGH_LOAD_FRAC)
            return new Color(210, 190, 0);
        return new Color(34, 168, 74);
    }

    private Color heatColor(double t, int alpha) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int)(60 + t * 195), (int)(60 + (1 - t) * 80), (int)(140 - t * 120), alpha);
    }

    private int[] edgeEndpoints(Node src, Node dst) {
        double dx = dst.x - src.x, dy = dst.y - src.y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return new int[]{src.x, src.y, dst.x, dst.y};
        double ux = dx / len, uy = dy / len;
        return new int[]{
            (int)(src.x + ux * NODE_RADIUS), (int)(src.y + uy * NODE_RADIUS),
            (int)(dst.x - ux * NODE_RADIUS), (int)(dst.y - uy * NODE_RADIUS)
        };
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        double ux = dx / len, uy = dy / len;
        int sx = (int)(x1 + ux * NODE_RADIUS), sy = (int)(y1 + uy * NODE_RADIUS);
        int ex = (int)(x2 - ux * NODE_RADIUS), ey = (int)(y2 - uy * NODE_RADIUS);
        g2.drawLine(sx, sy, ex, ey);
        double al = 10, aa = Math.PI / 7, ang = Math.atan2(ey - sy, ex - sx);
        g2.fillPolygon(
            new int[]{ex, (int)(ex - al * Math.cos(ang - aa)), (int)(ex - al * Math.cos(ang + aa))},
            new int[]{ey, (int)(ey - al * Math.sin(ang - aa)), (int)(ey - al * Math.sin(ang + aa))}, 3);
    }
}
