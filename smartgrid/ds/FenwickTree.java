package smartgrid.ds;

import java.util.Arrays;

/**
 * Binary Indexed Tree (BIT / Fenwick Tree) for O(log n) cumulative
 * energy tracking per CityZone.
 *
 * Index convention: 1-based. CityZone IDs must be mapped to [1..n] before use.
 */
public class FenwickTree {

    private int[] tree;
    private final int n;

    /** Constructs a Fenwick Tree for {@code n} elements, all initialised to 0. */
    public FenwickTree(int n) {
        this.n = n;
        this.tree = new int[n + 1];
    }

    /**
     * Point update: adds {@code delta} to position {@code i}. O(log n)
     */
    public void update(int i, int delta) {
        for (; i <= n; i += i & -i) {
            tree[i] += delta;
        }
    }

    /**
     * Prefix sum query: returns total for positions 1..i inclusive. O(log n)
     */
    public int query(int i) {
        int sum = 0;
        for (; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    /** Resets all entries to zero. O(n) */
    public void reset() {
        Arrays.fill(tree, 0);
    }

    public int size() {
        return n;
    }
}
