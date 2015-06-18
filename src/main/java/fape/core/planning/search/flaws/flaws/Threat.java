package fape.core.planning.search.flaws.flaws;

import fape.core.planning.planner.APlanner;
import fape.core.planning.search.flaws.resolvers.BindingSeparation;
import fape.core.planning.search.flaws.resolvers.Resolver;
import fape.core.planning.search.flaws.resolvers.TemporalSeparation;
import fape.core.planning.states.State;
import fape.core.planning.timelines.Timeline;

import java.util.LinkedList;
import java.util.List;

public class Threat extends Flaw {

    public final Timeline db1;
    public final Timeline db2;

    public Threat(Timeline db1, Timeline db2) {
        this.db1 = db1;
        this.db2 = db2;
    }

    @Override
    public List<Resolver> getResolvers(State st, APlanner planner) {
        if(resolvers != null)
            return resolvers;

        resolvers = new LinkedList<>();

        // db1 before db2
        if(st.canBeStrictlyBefore(db1.getLastTimePoints().getFirst(), db2.getFirstTimePoints().getFirst()))
            resolvers.add(new TemporalSeparation(db1, db2));

        // db2 before db1
        if(st.canBeStrictlyBefore(db2.getLastTimePoints().getFirst(), db1.getFirstTimePoints().getFirst()))
            resolvers.add(new TemporalSeparation(db2, db1));

        // make any argument of the state variables different
        for (int i = 0; i < db1.stateVariable.args().length; i++) {
            if(st.separable(db1.stateVariable.arg(i), db2.stateVariable.arg(i)))
                resolvers.add(new BindingSeparation(
                        db1.stateVariable.arg(i),
                        db2.stateVariable.arg(i)));
        }

        return resolvers;
    }
}
