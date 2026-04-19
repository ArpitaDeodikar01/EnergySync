package smartgrid.ds;

import java.util.Arrays;

/**
 * Segment Tree over CityZone energy loads.
 *
 * Each leaf i stores the currentLoad of CityZone i (1-based, matching FenwickTree
 * convention). Internal nodes store both the sum and max of their range, enabling:
 *   - queryEnergyRange(l, r) — total energy in zones l..r  — O(log n)
 *   - queryMaxLoad(l, r)     — maximum load in zones l..r  — O(log n)
 *   - queryMaxLoadZone()     — 1-based index of the zone with the highest load — O(log n)
 *   - update(i, val)         — set zone i's load to val    — O(log n)
 *
 * Index convention: 1-based externally; internally the tree array is 1-indexed
 * with the root at position 1 and children of node k at 2k and 2k+1.
 *
 * Size: 4*n internal array — standard safe allocation for a segment tree.
 */
public class SegmentTree {

    private final int n;          // number of leaves (= number of CityZones)
    private final int[] sumTree;  // sum of range at each node
    private final int[] maxTree;  // max of range at each node
    private final int[] maxIdx;   // 1-based leaf index of the max element in range

    /**
     * Constructs a SegmentTree for {@code n} zones, all initialised to 0.
     *
     * @param n number of CityZones (leaves); must be >= 1
     */
    public SegmentTree(int n) {
        this.n = n;
        this.sumTree = new int[4 * n];
        this.maxTree = new int[4 * n];
        this.maxIdx  = new int[4 * n];
        // All values start at 0; maxIdx initialised to 0 (no valid zone yet)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sets the load of zone {@code i} to {@code value}.
     * Time complexity: O(log n)
     *
     * @param i     1-based zone index
     * @param value new load value (must be >= 0)
     */
    public void update(int i, int value) {
        update(1, 1, n, i, value);
    }

    /**
     * Returns the total energy load across zones l..r inclusive.
     * Time complexity: O(log n)
     *
     * @param l 1-based left bound (inclusive)
     * @param r 1-based right bound (inclusive)
     * @return sum of loads in [l, r]
     */
    public int queryEnergyRange(int l, int r) {
        return querySum(1, 1, n, l, r);
    }

    /**
     * Returns the maximum load among zones l..r inclusive.
     * Time complexity: O(log n)
     *
     * @param l 1-based left bound (inclusive)
     * @param r 1-based right bound (inclusive)
     * @return maximum load value in [l, r]
     */
    public int queryMaxLoad(int l, int r) {
        return queryMax(1, 1, n, l, r);
    }

    /**
     * Returns the 1-based index of the zone with the highest load across all zones.
     * If multiple zones share the maximum, returns the leftmost one.
     * Time complexity: O(1) — reads the root's maxIdx directly
     *
     * @return 1-based zone index of the maximum-load zone, or 0 if tree is empty
     */
    public int queryMaxLoadZone() {
        return maxIdx[1]; // root covers the full range
    }

    /**
     * Resets all zone loads to zero.
     * Time complexity: O(n)
     */
    public void reset() {
        Arrays.fill(sumTree, 0);
        Arrays.fill(maxTree, 0);
        Arrays.fill(maxIdx,  0);
    }

    public int size() { return n; }

    // -------------------------------------------------------------------------
    // Internal recursive helpers
    // -------------------------------------------------------------------------

    /**
     * Recursive point update.
     * node = current tree node index; [nodeL, nodeR] = range it covers.
     */
    private void update(int node, int nodeL, int nodeR, int i, int value) {
        if (nodeL == nodeR) {
            // Leaf: set both sum and max to the new value
            sumTree[node] = value;
            maxTree[node] = value;
            maxIdx[node]  = nodeL; // leaf index == zone index
            return;
        }

        int mid = (nodeL + nodeR) / 2;
        if (i <= mid) {
            update(2 * node, nodeL, mid, i, value);
        } else {
            update(2 * node + 1, mid + 1, nodeR, i, value);
        }

        // Pull up: sum and max from children
        sumTree[node] = sumTree[2 * node] + sumTree[2 * node + 1];
        if (maxTree[2 * node] >= maxTree[2 * node + 1]) {
            maxTree[node] = maxTree[2 * node];
            maxIdx[node]  = maxIdx[2 * node];
        } else {
            maxTree[node] = maxTree[2 * node + 1];
            maxIdx[node]  = maxIdx[2 * node + 1];
        }
    }

    /** Recursive range sum query over [l, r]. */
    private int querySum(int node, int nodeL, int nodeR, int l, int r) {
        if (l > nodeR || r < nodeL) return 0;           // out of range
        if (l <= nodeL && nodeR <= r) return sumTree[node]; // fully covered
        int mid = (nodeL + nodeR) / 2;
        return querySum(2 * node, nodeL, mid, l, r)
             + querySum(2 * node + 1, mid + 1, nodeR, l, r);
    }

    /** Recursive range max query over [l, r]. */
    private int queryMax(int node, int nodeL, int nodeR, int l, int r) {
        if (l > nodeR || r < nodeL) return Integer.MIN_VALUE; // out of range
        if (l <= nodeL && nodeR <= r) return maxTree[node];   // fully covered
        int mid = (nodeL + nodeR) / 2;
        return Math.max(
                queryMax(2 * node, nodeL, mid, l, r),
                queryMax(2 * node + 1, mid + 1, nodeR, l, r)
        );
    }
}
