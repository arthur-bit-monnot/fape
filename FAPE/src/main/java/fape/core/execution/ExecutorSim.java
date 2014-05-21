package fape.core.execution;

import fape.core.execution.model.AtomicAction;

public class ExecutorSim extends Executor {

    @Override
    public void executeAtomicActions(AtomicAction acts) {
        System.out.println("Executing: "+acts+ " start: "+acts.mStartTime+", dur:"+acts.duration);
        mActor.ReportSuccess(acts.id, (int) acts.mStartTime+acts.duration);
    }
}
