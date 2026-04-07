package smartgrid;

import java.util.Arrays;

/**
 * DSA Role: Binary Indexed Tree (BIT / Fenwick Tree) for O(log n) cumulative
 * energy tracking per CityZone per time slot.
 *
 * A Fenwick Tree stores partial sums in a 1-based array where each index i
 * is responsible for a range of values determined by the lowest set bit of i
 * (i.e., i & -i). This allows both point updates and prefix sum queries in
 * O(log n) time using only integer bit operations — no extra memory needed.
 *
 * Usage in SmartGrid:
 *   - GridSimulator calls update(zoneId, energyAmount) after each MCMF run.
 *   - query(k) returns total energy consumed by zones 1..k.
 *   - reset() is called on [Reset] to clear all accumulated values.
 *
 * Index convention: 1-based. CityZone IDs must be mapped to [1..n] before use.
 */
public class FenwickTree {

    /** 1-based internal array; tree[0] is unused */
    private int[] tree;

    /** Number of tracked elements (= number of CityZones) */
    private final int n;

    /**
     * Constructs a Fenwick Tree for {@code n} elements, all initialised to 0.
     *
     * @param n number of CityZones to track (indices 1..n are valid)
     */
    public FenwickTree(int n) {
        this.n = n;
        // Allocate n+1 slots; index 0 is a dummy to keep 1-based indexing clean
        this.tree = new int[n + 1];
    }

    /**
     * Point update: adds {@code delta} to position {@code i}.
     * Propagates the update upward through all ancestor nodes in the BIT
     * by repeatedly adding the lowest set bit (i += i & -i).
     *
     * Time complexity: O(log n)
     *
     * @param i     1-based index of the CityZone to update
     * @param delta energy amount to add (must be non-negative for correct prefix sums)
     */
    public void update(int i, int delta) {
        // Walk up the tree: each step moves to the next responsible ancestor
        // i & -i isolates the lowest set bit, which determines the range each node covers
        for (; i <= n; i += i & -i) {
            tree[i] += delta;
        }
    }

    /**
     * Prefix sum query: returns the total energy consumed by zones 1..i inclusive.
     * Traverses down the tree by repeatedly stripping the lowest set bit (i -= i & -i).
     *
     * Time complexity: O(log n)
     *
     * @param i 1-based upper bound of the prefix sum query
     * @return sum of all values at positions 1 through i
     */
    public int query(int i) {
        int sum = 0;
        // Walk down the tree: each step subtracts the lowest set bit to reach the
        // next disjoint range that contributes to the prefix sum
        for (; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    /**
     * Resets all entries to zero.
     * Called when [Reset] is clicked to clear accumulated energy history.
     * Time complexity: O(n)
     */
    public void reset() {
        // Overwrite the entire array with zeros; faster than re-allocating
        Arrays.fill(tree, 0);
    }

    /**
     * Returns the number of elements this tree tracks.
     * Time complexity: O(1)
     */
    public int size() {
        return n;
    }
}
