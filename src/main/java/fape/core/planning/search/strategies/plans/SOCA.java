package fape.core.planning.search.strategies.plans;

import fape.core.planning.planner.APlanner;
import fape.core.planning.search.flaws.finders.AllThreatFinder;
import fape.core.planning.states.State;


/**
 * Evaluation function: num-actions*10 + num-consumers*3 + num-undecomposed*3
 */
public class SOCA implements PartialPlanComparator, Heuristic {

    private final APlanner planner;
    private final AllThreatFinder threatFinder = new AllThreatFinder();

    public SOCA(APlanner planner) { this.planner = planner; }

    public float f(State s) {
        if(s.h < 0)
            s.h = threatFinder.getFlaws(s, planner).size();

        return s.getNumActions()*10 + s.consumers.size()*3 + s.getNumOpenLeaves()*3 + s.h*3;
    }

    @Override
    public int compare(State state, State state2) {
        float f_state = f(state);
        float f_state2 = f(state2);

        // comparison (and not difference) is necessary since the input is a float.
        if(f_state > f_state2)
            return 1;
        else if(f_state2 > f_state)
            return -1;
        else
            return 0;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public String shortName() {
        return "soca";
    }

    @Override
    public float g(State st) {
        return st.getNumActions() * 10;
    }

    @Override
    public float h(State s) {
        if(s.h < 0)
            s.h = threatFinder.getFlaws(s, planner).size();

        return s.consumers.size()*3 + s.getNumOpenLeaves()*3 + s.h*3;
    }

    @Override
    public float hc(State st) {
        return h(st);
    }
}
