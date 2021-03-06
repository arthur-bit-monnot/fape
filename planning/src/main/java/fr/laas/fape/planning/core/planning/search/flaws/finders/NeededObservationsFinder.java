package fr.laas.fape.planning.core.planning.search.flaws.finders;

import fr.laas.fape.anml.model.concrete.*;
import fr.laas.fape.constraints.stnu.morris.PartialObservability;
import fr.laas.fape.planning.core.planning.planner.Planner;
import fr.laas.fape.planning.core.planning.search.flaws.flaws.Flaw;
import fr.laas.fape.planning.core.planning.search.flaws.resolvers.Resolver;
import fr.laas.fape.planning.core.planning.states.PartialPlan;
import fr.laas.fape.planning.core.planning.states.StateExtension;
import fr.laas.fape.planning.exceptions.FAPEException;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * This flaw finder and the associated resolvers are experimental and will right now only work in the rabbits domain.
 */
public class NeededObservationsFinder implements FlawFinder {


    @Override
    public List<Flaw> getFlaws(PartialPlan plan, Planner planner) {
        // contingent timepoints are all those with an incoming contingent link
        // when executing, some contingents might have been executed (and their incoming links removed)
        Stream<TPRef> contingents = plan.csp.stn().getOriginalConstraints().stream()
                .filter(c -> c instanceof ContingentConstraint)
                .map(ctg -> ctg.dst());

        if(!plan.hasExtension(PartialObservabilityExt.class))
            plan.addExtension(new PartialObservabilityExt(new HashSet<>(), new HashMap<>()));

        PartialObservabilityExt obs = plan.getExtension(PartialObservabilityExt.class);
        Set<TPRef> observable = contingents
                .filter(tp -> !obs.observed.contains(tp) && obs.observationConditions.containsKey(tp))
                .collect(Collectors.toSet());

        Optional<PartialObservability.NeededObservations> opt = PartialObservability.getResolvers(plan.csp.stn().getConstraintsWithoutStructurals().stream().collect
                (Collectors.toList()), obs.observed, observable);

        if(opt.isPresent())
            return Collections.singletonList(new NeededObservationFlaw(opt.get(), plan));
        else
            return Collections.emptyList();
    }


    @Value private static final class PartialObservabilityExt implements StateExtension {
        public final Set<TPRef> observed;
        public final Map<TPRef,Chronicle> observationConditions;

        @Override
        public StateExtension clone(PartialPlan st) {
            return new PartialObservabilityExt(new HashSet<>(observed), new HashMap<>(observationConditions));
        }

        @Override
        public void chronicleMerged(Chronicle c) {
            for(ChronicleAnnotation annot : c.annotations()) {
                if(annot instanceof ObservationConditionsAnnotation) {
                    ObservationConditionsAnnotation obsCond = (ObservationConditionsAnnotation) annot;
                    observationConditions.put(obsCond.tp(), obsCond.conditions());
                    if(obsCond.conditions().isEmpty())
                        observed.add(obsCond.tp());
                }
            }
        }
    }

    public static final class NeededObservationFlaw extends Flaw {
        final Collection<Set<TPRef>> possibleObservationsSets;

        public NeededObservationFlaw(PartialObservability.NeededObservations no, PartialPlan st) {
            possibleObservationsSets = no.resolvingObs();
        }

        @Override
        public List<Resolver> getResolvers(PartialPlan plan, Planner planner) {
            return possibleObservationsSets.stream().map(NeededObsResolver::new).collect(Collectors.toList());
        }

        @Override
        public int compareTo(Flaw o) {
            throw new FAPEException("There should not be two needed observations flaws on the same state.");
        }

        @AllArgsConstructor final class NeededObsResolver implements Resolver {
            public final Set<TPRef> toObserve;

            @Override
            public int compareWithSameClass(Resolver e) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }
}
