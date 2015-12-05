package fape.core.planning.search.flaws.resolvers;

import fape.core.planning.planner.APlanner;
import fape.core.planning.states.State;

/**
 * A resolver is recipe to fix a Flaw.
 * It provides an apply method that modifies a state so that the flaw is fixed.
 */
public abstract class Resolver implements Comparable<Resolver> {

    /**
     * Modifies the state so that the flaw this resolver was created from is fixed.
     * @param st State to modify
     * @param planner The planner from which this method is called. It used to extract options and
     *                results from preprocessing that might be used.
     * @return True if the resolvers was successfully applied. (Note that the state might still e inconsistent, the only
     *         guarantee is that the flaw is fixed.
     */
    public abstract boolean apply(State st, APlanner planner);

    /**
     * Should provide a comparison with another resolver of the same class.
     * This is used to sort resolvers for reproducibility.
     */
    public abstract int compareWithSameClass(Resolver e);

    /**
     * Provides a way to sort resolvers for reproducibility. This is not intended to give information
     * on how much interesting a resolver is but just to make sure that resolvers are always in the same
     * order between two runs.
     */
    @Override
    public int compareTo(Resolver o) {
        String n1 = this.getClass().getCanonicalName();
        String n2 = o.getClass().getCanonicalName();
        int cmp = n1.compareTo(n2);
        if(cmp != 0) {
            assert this.getClass() != o.getClass();
            return -cmp;
        } else {
            assert this.getClass() == o.getClass();
            return -this.compareWithSameClass(o);
        }
    }
}