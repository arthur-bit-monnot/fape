package fape.core.planning.resources;

import fape.core.planning.search.flaws.flaws.ResourceFlaw;
import fape.core.planning.states.State;
import planstack.anml.model.concrete.TPRef;

import java.util.List;

/**
 *
 * @author FD
 */
public class Consumable extends Resource {
    public float max;
    private float consumed = 0;
    

    
    @Override
    public List<ResourceFlaw> GatherFlaws(State st) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isConsistent(State st) {
        return consumed <= max;
    }

    @Override
    public void addConsumption(State st, TPRef start, TPRef end, float value) {
        consumed += value;
    }

    @Override
    public void addProduction(State st, TPRef start, TPRef end, float value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addUsage(State st, TPRef start, TPRef end, float value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addAssignement(State st, TPRef start, TPRef end, float value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addRequirement(State st, TPRef start, TPRef end, float value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addLending(State st, TPRef start, TPRef end, float value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void MergeInto(Resource b) {
        this.consumed += ((Consumable)b).consumed;
    }

    @Override
    public Resource DeepCopy() {
        Consumable c = new Consumable();
        c.consumed = this.consumed;
        c.continuous = this.continuous;
        c.max = this.max;
        c.mID = this.mID;
        //c.name = this.name;
        c.stateVariable = this.stateVariable;
        return c;
    }

}