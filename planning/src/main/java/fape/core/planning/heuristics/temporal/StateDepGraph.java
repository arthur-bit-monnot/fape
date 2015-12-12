package fape.core.planning.heuristics.temporal;

import fape.core.planning.grounding.Fluent;
import fape.core.planning.grounding.GAction;
import fape.util.IteratorConcat;
import fr.laas.fape.structures.IR2IntMap;
import fr.laas.fape.structures.IRSet;

import java.util.*;
import java.util.stream.Collectors;

public class StateDepGraph implements DependencyGraph {

    static final int dbgLvl = 0;
    static final int dbgLvlDij = 0;

    /** Edges and nodes common to all graphs **/
    public DepGraphCore core;

    /** Timed initial litterals **/
    public final FactAction facts;

    /** Timed initial literals as a list of edges **/
    List<MinEdge> initMinEdges = new ArrayList<>();
    /** The previous edges indexed by their target fluent. **/
    Map<TempFluent.DGFluent, List<MinEdge>> initFluents = new HashMap<>();

    /** maximum delay of all edges of the graph (i.e. both egdes in the core and those from timed initial literals) **/
    int dmax;

    /** Associates all possible nodes to their earliest appearance (populated after propagation) **/
    IR2IntMap<Node> earliestAppearances = null;

    /** All GActions that be added to the plan (populated after propagation) **/
    IRSet<GAction> addableActs = null;

    /** Earliest appearance of all achievable fluents (populated after propagation) **/
    IR2IntMap<Fluent> fluentsEAs = null;

    /** Associates any possible node to its predecessor (i.e. the possible that gave it its current earliest appearance time).
     * It is currently only used and set by the dijkstra propagator. **/
    private IR2IntMap<Node> predecessors = null;

    public StateDepGraph(DepGraphCore core, List<TempFluent> initFacts) {
        this.core = core;

        this.facts = core.store.getFactAction(initFacts);
        dmax = core.dmax;

        for(TempFluent tf : facts.getEffects()) {
            MinEdge e = new MinEdge(facts, tf.fluent, tf.getTime());
            initMinEdges.add(e);
            initFluents.putIfAbsent(tf.fluent, new ArrayList<>());
            initFluents.get(tf.fluent).add(e);
            dmax = Math.max(dmax, e.delay);
        }
    }

    /** Debug method that prints all possible GAction with their earliest appearance time **/
    private void printActions() {
        // complete action: show start
        System.out.println("\nactions: " +
                earliestAppearances.entrySet().stream()
                        .sorted((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
                        .filter(n -> n.getKey() instanceof RAct)
                        .filter(n -> ((RAct) n.getKey()).tp.toString().equals("ContainerStart"))
                        .map(a -> "\n  [" + a.getValue() + "] " + ((RAct) a.getKey()).act)
                        .collect(Collectors.toList()));
    }

    @Override
    public Iterator<MaxEdge> inEdgesIt(ActionNode n) {
        if(n != facts)
            return core.inEdgesIt(n);
        else
            return Collections.emptyIterator(); // fact action has no incoming edges
    }

    @Override
    public Iterator<MinEdge> outEdgesIt(ActionNode n) {
        if(n != facts)
            return core.outEdgesIt(n);
        else
            return initMinEdges.iterator();

    }

    @Override
    public Iterator<MinEdge> inEdgesIt(TempFluent.DGFluent f) {
        if(initFluents.containsKey(f))
            return new IteratorConcat<>(core.inEdgesIt(f), initFluents.get(f).iterator());
        else
            return core.inEdgesIt(f);
    }

    @Override
    public Iterator<MaxEdge> outEdgesIt(TempFluent.DGFluent f) {
        return core.outEdgesIt(f); // no such edge can be added by a fact action
    }


    public IR2IntMap<Node> propagate(Optional<StateDepGraph> ancestorGraph) {

        if(dbgLvlDij > 3) {
            // used when debugging to make sure bellman ford and dijkstra yield the same results
            Propagator p = new BellmanFord(ancestorGraph);
            IR2IntMap<Node> easBF = p.getEarliestAppearances();

            Dijkstra dij = new Dijkstra(ancestorGraph);
            IR2IntMap<Node> easDij = dij.getEarliestAppearances();
            earliestAppearances = easDij;
            predecessors = dij.labelsPred;

            for(Node n : easBF.keys()) {
                assert easDij.containsKey(n);
            }
            for(Node n : easDij.keys()) {
                if(!easBF.containsKey(n))
                    dij.display(n);
                assert easBF.containsKey(n);
                if(!easBF.get(n).equals(easDij.get(n))) {
                    System.out.println(easBF.get(n) + "   " + easDij.get(n));
                    this.displayRec(n,1,1);
                }
                assert easBF.get(n).equals(easDij.get(n));
            }

        } else {
            // propagate and retrieve the results
            Dijkstra dij = new Dijkstra(ancestorGraph);
            earliestAppearances = dij.getEarliestAppearances();
            predecessors = dij.labelsPred;
        }

        // extract results as more general ground object (the representation here is slightly different)
        addableActs = new IRSet<>(core.store.getIntRep(GAction.class));
        fluentsEAs = new IR2IntMap<>(core.store.getIntRep(Fluent.class));
        for(Node n : earliestAppearances.keys()) {
            if (n instanceof RAct) {
                addableActs.add(((RAct) n).getAct());
            } else if(n instanceof TempFluent.SVFluent) {
                fluentsEAs.put(((TempFluent.SVFluent) n).fluent, earliestAppearances.get(n));
            }
        }

        if(dbgLvlDij >= 2 && ancestorGraph.isPresent()) {
            for(Node n : earliestAppearances.keys()) {
                if(!(n instanceof FactAction))
                    assert ancestorGraph.get().earliestAppearances.containsKey(n);
            }
        }

        // if this was the first propagation, we recreate a core graph containing only possible nodes
        if(!ancestorGraph.isPresent()) {
            List<RAct> feasibles = earliestAppearances.keySet().stream()
                    .filter(n -> n instanceof RAct)
                    .map(n -> (RAct) n)
                    .collect(Collectors.toList());
            DepGraphCore prevCore = core;
            core = new DepGraphCore(feasibles, core.store);
            if(dbgLvl >= 1) System.out.println("Shrank core graph to: "+core.getDefaultEarliestApprearances().size()
                    +" nodes. (Initially: "+prevCore.getDefaultEarliestApprearances().size()+")");
        }

        if(dbgLvl >= 2) printActions();

        return earliestAppearances;
    }

    /** Recursively display a nodes and its incoming edges. **/
    private void displayRec(Node n, int depth, int maxDepth) {
        if(depth > maxDepth)
            return;
        String baseSpace = depth == 1 ? " " : (depth == 2 ? "  " : (depth==3 ? "    " : (depth==4 ? "     " :"  to deep")));
        System.out.print(baseSpace+(earliestAppearances.containsKey(n) ? "["+earliestAppearances.get(n)+"] " : "[--] "));
        System.out.println(baseSpace+n);
        if(n instanceof TempFluent.DGFluent) {
            for(MinEdge e : inEdges((TempFluent.DGFluent) n)) {
                System.out.println(baseSpace + "  " + earliestAppearances.containsKey(e.act) + " " + e +
                        (earliestAppearances.containsKey(e.act) ? " src:["+earliestAppearances.get(e.act)+"] " : " src[--] "));
                displayRec(e.act, depth+1, maxDepth);
            }
        } else {
            for(MaxEdge e : inEdges((ActionNode) n)) {
                System.out.println(baseSpace + "  " + earliestAppearances.containsKey(e.fluent) + "  " + e +
                        (earliestAppearances.containsKey(e.fluent) ? " src:["+earliestAppearances.get(e.fluent)+"] " : " src[--] "));
                displayRec(e.fluent, depth+1, maxDepth);
            }
        }
    }


    interface Propagator {
        IR2IntMap<Node> getEarliestAppearances();
    }

    /**
     * Class that propagates the earliest appearance labels in a dependency graph.
     * The implementation is straightforward:
     *  - we go through all the nodes and see if any needs to be pushed back in time.
     *  - we check if we have some late nodes that can be safely deleted. If so we remove them all
     *    those depending on them
     *  - we start again until no node was update or deleted
     *
     *  This implementation is quite slow but serves as a reference implementation on which the results
     *  of more complex implementations can be verified.
     **/
    class BellmanFord implements Propagator {
        final IRSet<Node> possible;
        final IR2IntMap<Node> eas;

        private int ea(Node n) { return ea(n.getID()); }
        private int ea(int nid) { return eas.get(nid); }
        private void setEa(Node n, int t) { setEa(n.getID(), t); }
        private void setEa(int nid, int t) { assert ea(nid) <= t; eas.put(nid, t); }
        private boolean possible(Node n) { return possible(n.getID()); }
        private boolean possible(int nid) { return possible.contains(nid); }
        private void setImpossible(int nid) { possible.remove(nid); eas.remove(nid); }
        private void setImpossible(Node n) { setImpossible(n.getID()); }

        public BellmanFord(Optional<StateDepGraph> ancestorGraph) {
            if(ancestorGraph.isPresent()) {
                eas = ancestorGraph.get().earliestAppearances.clone();
                eas.remove(ancestorGraph.get().facts);
                eas.put(facts, 0);
            } else {
                eas = core.getDefaultEarliestApprearances();
                eas.put(facts, 0);
                for(TempFluent.DGFluent f : initFluents.keySet())
                    eas.putIfAbsent(f, 0);
            }
            possible = new IRSet<Node>(core.store.getIntRep(Node.class));
            earliestAppearances = this.eas;

            PrimitiveIterator.OfInt it = eas.keysIterator();
            while(it.hasNext())
                possible.add(it.nextInt());

            int numIter = 0; int numCut = 0;
            boolean updated = true;
            while(updated) {
                if(dbgLvl >= 3) printActions();
                numIter ++;
                updated = false;
                final PrimitiveIterator.OfInt nodesIt = possible.primitiveIterator();
                // try to update all the nodes
                while (nodesIt.hasNext()) {
                    updated |= update(nodesIt.nextInt());
                }

                if(dbgLvl >= 3) printActions();

                // delete any late node.
                // first determine the cut_threshold after which all nodes can be removed
                List<Integer> easOrdered = new ArrayList<Integer>(this.eas.values());
                Collections.sort(easOrdered);
                int prevValue = 0;
                int cut_threshold = Integer.MAX_VALUE;
                for(int val : easOrdered) {
                    if(val - prevValue > dmax) {
                        cut_threshold = val;
                        break;
                    }
                    prevValue = val;
                }
                // set any node after the cut threshold as impossible as impossible
                if(cut_threshold != Integer.MAX_VALUE) {
                    PrimitiveIterator.OfInt nodes = possible.primitiveIterator();
                    while(nodes.hasNext()) {
                        int nid = nodes.nextInt();
                        if(ea(nid) > cut_threshold) {
                            setImpossible(nid);
                            numCut++;
                        }
                    }
                }
                if(dbgLvl >= 3) printActions();
            }
            if(dbgLvl >= 1) System.out.println(String.format("Num iterations: %d\tNum removed: %d", numIter, numCut));
        }

        /** Update the EA of the node if necessary. Returns true if node was updated */
        private boolean update(int nid) {
            Node n = (Node) core.store.get(Node.class, nid);
            if(n instanceof TempFluent.DGFluent)
                return updateFluent((TempFluent.DGFluent) n);
            else
                return updateAction((ActionNode) n);
        }

        private boolean updateFluent(TempFluent.DGFluent f) {
            if(!possible(f)) return false;
            assert eas.containsKey(f.getID()) : "Possible fluent with no earliest appearance time.";

            int min = Integer.MAX_VALUE;
            for(MinEdge e : inEdges(f)) {
                if(possible(e.act) && ea(e.act) + e.delay < min) {
                    min = ea(e.act) + e.delay;
                }
            }
            if(min == Integer.MAX_VALUE) {
                setImpossible(f);
                return true;
            } else if(min > ea(f)) {
                setEa(f, min);
                return true;
            } else {
                return false;
            }
        }

        private boolean updateAction(ActionNode a) {
            int max = Integer.MIN_VALUE;
            for(MaxEdge e : inEdges(a)) {
                if(!possible(e.fluent))
                    max = Integer.MAX_VALUE;
                else
                    max = Math.max(max, ea(e.fluent) + e.delay);
            }
            if(max == Integer.MAX_VALUE) {
                setImpossible(a);
                return true;
            } else if(max > ea(a)) {
                setEa(a, max);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public IR2IntMap<Node> getEarliestAppearances() {
            return eas;
        }
    }

    /**
     * More involved implementation of a propagator based on the Dijkstra algorithm.
     */
    public class Dijkstra implements Propagator {

        IR2IntMap<Node> pendingForActivation;
        final IR2IntMap<Node> optimisticValues;

        IR2IntMap<Node> labelsCost = new IR2IntMap<>(core.store.getIntRep(Node.class));
        IR2IntMap<Node> labelsPred = new IR2IntMap<>(core.store.getIntRep(Node.class));

        PriorityQueue<Node> queue = new PriorityQueue<>((Node n1, Node n2) -> cost(n1) - cost(n2));
        boolean firstPropagationFinished = false;

        private int cost(Node n) { return labelsCost.get(n.getID()); }
        private Node pred(Node n) { return (Node) core.store.get(Node.class, labelsPred.get(n)); }
        private void setPred(Node n, Node pred) {
            assert possible(pred) || pred == n;
            labelsPred.put(n, pred.getID());
        }
        private boolean possible(Node n) { return labelsPred.containsKey(n); }
        private boolean optimisticallyPossible(Node n) { return optimisticValues.containsKey(n); }

        private boolean shouldIgnore(MaxEdge e) { return e.delay < 0 || core.getFluentsWithIncomingNegEdge().contains(e.fluent);  }
        private Iterable<MaxEdge> ignoredEdges() { return core.getEdgesToIgnoreInDijkstra(); }

        IRSet<Node> queueContent = new IRSet<Node>(core.store.getIntRep(Node.class));

        private void enqueue(Node n, int cost, Node pred) {
            if(!optimisticValues.containsKey(n))
                return; // ignore all non-possible node

            if(queueContent.contains(n)) {
                if(dbgLvlDij >= 2) assert queue.contains(n); //  O(n) !!!!
                if(cost < cost(n) && cost(n) > optimisticValues.get(n)) { // "cost" is an improvement and an improvement is possible
                    queue.remove(n);
                    labelsCost.put(n, Math.max(cost, optimisticValues.get(n)));
                    setPred(n, pred);
                    queue.add(n);
                }
            } else if(!labelsPred.containsKey(n)) { // node has not been settled yet
                if(dbgLvlDij >= 2) assert !queue.contains(n); //  O(n) !!!!
                labelsCost.put(n, Math.max(cost, optimisticValues.get(n)));
                setPred(n, pred);
                queue.add(n);
                queueContent.add(n);
            } else {
                assert cost >= cost(n) || cost(n) == optimisticValues.get(n);
            }
        }

        public Dijkstra(Optional<StateDepGraph> ancestorGraph) {
            if(dbgLvlDij >= 2) System.out.println("\n------------------------------------\n");

            if(ancestorGraph.isPresent()) {
                // initialization from an existing graph: we will do everything incrementally

                // copy the earliest appearances and predecessors and replace the FactAction by our own
                optimisticValues = ancestorGraph.get().earliestAppearances.clone();
                optimisticValues.remove(ancestorGraph.get().facts);
                optimisticValues.put(facts, 0);
                labelsCost = optimisticValues.clone();
                labelsPred = ancestorGraph.get().predecessors.clone();
                labelsPred.remove(ancestorGraph.get().facts);
                setPred(facts, facts);

                // for all nodes, achieved by the ancestor's FactAction, check if we need to update it
                // (and if necessary put it in the queue)
                for(TempFluent tf : ancestorGraph.get().facts.getEffects()) {
                    if(possible(tf.fluent) && pred(tf.fluent) == ancestorGraph.get().facts) {
                        int bestCost = Integer.MAX_VALUE;
                        Node bestPred = null;
                        for(MinEdge e : inEdges(tf.fluent)) {
                            if(possible(e.act) && cost(e.act) + e.delay < bestCost) {
                                bestCost = cost(e.act) + e.delay;
                                bestPred = e.act;
                            }
                        }
                        if(bestPred == null) {
                            delete(tf.fluent); // no achiever left
                        } else {
                            delayEnqueue(tf.fluent, bestCost, bestPred);
                        }
                    }
                }
                // go directly to incremental propagation
                firstPropagationFinished = true;
            } else {
                optimisticValues = core.getDefaultEarliestApprearances();
                optimisticValues.put(facts, 0);
                for(TempFluent.DGFluent f : initFluents.keySet())
                    optimisticValues.putIfAbsent(f, 0);

                pendingForActivation = new IR2IntMap<Node>(core.store.getIntRep(Node.class));
                for(Node n : optimisticValues.keys()) {
                    if(n instanceof ActionNode) { // action node
                        ActionNode a = (ActionNode) n;
                        int numReq = 0;
                        for(MaxEdge e : inEdges(a)) {
                            if(!shouldIgnore(e))
                                numReq++;
                        }
                        pendingForActivation.put(n, numReq);
                        if (numReq == 0) {
                            enqueue(n, 0, n);
                        }
                    }
                }
            }


            while(!queue.isEmpty()) {
                // run a dijkstra algorithm to extract everything from the queue
                // this run is limited to positive edges.
                if (!firstPropagationFinished) {
                    originalDijkstra();
                    firstPropagationFinished = true;
                } else {
                    incrementalDijkstra();
                }

                // process ignored edges (those that can lead to a negative or null cycle) and enqueue modified nodes
                for (MaxEdge e : ignoredEdges()) {
                    if (possible(e.act)) {
                        if (!possible(e.fluent)) {
                            // the action is not possible
                            delete(e.act);
                            if(dbgLvlDij >2) System.out.println(String.format(">del %s", e.act));
                        } else {
                            if (cost(e.fluent) + e.delay > cost(e.act)) {
                                delayEnqueue(e.act, cost(e.fluent) + e.delay, e.fluent);
                                if(dbgLvlDij >2) System.out.println(String.format(">[%d] %s", cost(e.act), e.act));
                            }
                        }
                    }
                }

                // remove all late nides
                // first determine the cut threshold (all nodes later than that will be deleted)
                List<Integer> easOrdered = new ArrayList<Integer>(labelsCost.values());
                Collections.sort(easOrdered);
                int prevValue = 0; int cut_threshold = Integer.MAX_VALUE;
                for(int val : easOrdered) {
                    if(val - prevValue > dmax) {
                        cut_threshold = val;
                        break;
                    }
                    prevValue = val;
                }
                // delete all nodes after the cut threshold. Some nodes might be delayed due to these deletions,
                // in which case they are put back in the queue (done inside delete(.))
                if(cut_threshold != Integer.MAX_VALUE) {
                    if(dbgLvlDij > 2) System.out.println("Start cutting from: "+cut_threshold+", (dmax = "+dmax+")");
                    for(Node n : labelsCost.keys()) {
                        if(possible(n) && cost(n) > cut_threshold) {
                            delete(n);
                        }
                    }
                    if(dbgLvlDij > 2) System.out.println("End cutting from");
                }
            }
        }

        private void incrementalDijkstra() {
            while(!queue.isEmpty()) {
                Node n = queue.poll();
                queueContent.remove(n);
                assert possible(n);
                if(dbgLvlDij >2) System.out.println(String.format("+[%d] %s     <<--- %s", cost(n), n, pred(n)!=n ? pred(n) : " self"));
                if(n instanceof ActionNode) {
                    ActionNode a = (ActionNode) n;
                    for (MinEdge e : outEdges(a)) {
                        if(!possible(e.fluent)) {
                            display(e.fluent);
                            display(e.act);
                        }
                        assert possible(e.fluent);
                        if(pred(e.fluent) == a) {
                            int bestCost = Integer.MAX_VALUE;
                            Node bestPred = null;
                            for(MinEdge minE : inEdges(e.fluent)) {
                                if(possible(minE.act) && cost(minE.act) +minE.delay < bestCost) {
                                    bestCost = cost(minE.act) + minE.delay;
                                    bestPred = minE.act;
                                }
                            }
                            assert bestPred != null;
                            delayEnqueue(e.fluent, bestCost, bestPred);
                        }
                    }
                } else {
                    TempFluent.DGFluent f = (TempFluent.DGFluent) n;
                    for(MaxEdge e : outEdges(f)) {
                        if(possible(e.act) && !shouldIgnore(e)) {
                            delayEnqueue(e.act, cost(e.fluent) + e.delay, e.fluent);
                        }
                    }
                }
            }
        }

        private void originalDijkstra() {
            // main dijkstra loop
            while(!queue.isEmpty()) {
                Node n = queue.poll();
                queueContent.remove(n);
                if(dbgLvlDij >2) System.out.println(String.format(" [%d] %s     <<--- %s", cost(n), n, pred(n)!=n ? pred(n) : " self"));
                if(n instanceof ActionNode) {
                    ActionNode a = (ActionNode) n;
                    for (MinEdge e : outEdges(a)) {
                        enqueue(e.fluent, cost(a) + e.delay, a);
                    }
                } else {
                    TempFluent.DGFluent f = (TempFluent.DGFluent) n;
                    for(MaxEdge e : outEdges(f)) {
                        if(optimisticallyPossible(e.act) && !shouldIgnore(e)) {
                            int prevCost = labelsCost.getOrDefault(e.act, optimisticValues.get(n));
                            labelsCost.put(e.act, Math.max(cost(f) + e.delay, prevCost));
                            pendingForActivation.put(e.act, pendingForActivation.get(e.act) - 1);
                            if(dbgLvlDij >2) System.out.println(String.format(" [%d] %s     <<--- %s", cost(n), n, pred(n) != n ? pred(n) : " self"));
                            if(pendingForActivation.get(e.act) == 0) {
                                enqueue(e.act, labelsCost.get(e.act), f);
                            }
                        }
                    }
                }
            }
            // clean up label costs
            for(Node n : labelsCost.keys())
                if(!possible(n))
                    labelsCost.remove(n);
        }

        private void display(Node n) {
            System.out.println();
            System.out.print(possible(n) ? "["+cost(n)+"] " : "[--] ");
            System.out.print(n);
            System.out.println("  pred: "+(possible(n) ? pred(n) : "none")+"  predID: "+labelsPred.getOrDefault(n, -1));
            if(n instanceof TempFluent.DGFluent) {
                for(MinEdge e : inEdges((TempFluent.DGFluent) n))
                    System.out.println("  "+possible(e.act)+" "+e);
            } else {
                for(MaxEdge e : inEdges((ActionNode) n))
                    System.out.println("  "+possible(e.fluent)+"  "+e);
            }
        }

        private void delete(Node n) {
            if(!possible(n))
                return;

            if(dbgLvlDij > 2) System.out.println(" DEL    "+n);

            labelsCost.remove(n);
            labelsPred.remove(n);
            queue.remove(n);
            if(queueContent.contains(n))
                queueContent.remove(n);

            if(n instanceof TempFluent.DGFluent) {
                TempFluent.DGFluent f = (TempFluent.DGFluent) n;
                for(MaxEdge e : outEdges(f)) {
                    delete(e.act);
                }
            } else {
                ActionNode a = (ActionNode) n;
                for(MinEdge e : outEdges(a)) {
                    if(possible(e.fluent) && pred(e.fluent) == a) {
                        int bestCost = Integer.MAX_VALUE;
                        Node bestPred = null;
                        for(MinEdge minE : inEdges(e.fluent)) {
                            if(possible(minE.act) && cost(minE.act) +minE.delay < bestCost) {
                                bestCost = cost(minE.act) + minE.delay;
                                bestPred = minE.act;
                            }
                        }
                        if(bestPred == null) {
                            delete(e.fluent);
                            assert !possible(e.fluent);
                        } else {
                            delayEnqueue(e.fluent, bestCost, bestPred);
                            assert pred(e.fluent) != a;
                        }
                    }
                }
            }
        }

        private void delayEnqueue(Node n, int newCost, Node newPred) {
            assert possible(n);
            assert cost(n) >= optimisticValues.get(n);

            setPred(n, newPred); // always change the predecessor
            if(cost(n) < newCost) {
                if(queue.contains(n))
                    queue.remove(n);
                labelsCost.put(n, newCost);
                setPred(n, newPred);
                queue.add(n);
                queueContent.add(n);
            }

        }

        @Override
        public IR2IntMap<Node> getEarliestAppearances() {
            return labelsCost;
        }
    }
}