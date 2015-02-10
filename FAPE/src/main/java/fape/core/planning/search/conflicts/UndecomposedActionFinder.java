package fape.core.planning.search.conflicts;

import fape.core.planning.search.Flaw;
import fape.core.planning.search.UndecomposedAction;
import fape.core.planning.states.State;
import planstack.anml.model.concrete.Action;

import java.util.LinkedList;
import java.util.List;

public class UndecomposedActionFinder implements FlawFinder {
    @Override
    public List<Flaw> getFlaws(State st) {
        List<Flaw> flaws = new LinkedList<>();

        for(Action refinable : st.getOpenLeaves())
            flaws.add(new UndecomposedAction(refinable));

        return flaws;
    }
}
