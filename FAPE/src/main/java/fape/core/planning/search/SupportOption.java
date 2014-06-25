/*
 * Author:  Filip Dvořák <filip.dvorak@runbox.com>
 *
 * Copyright (c) 2013 Filip Dvořák <filip.dvorak@runbox.com>, all rights reserved
 *
 * Publishing, providing further or using this program is prohibited
 * without previous written permission of the author. Publishing or providing
 * further the contents of this file is prohibited without previous written
 * permission of the author.
 */
package fape.core.planning.search;

import fape.core.planning.constraints.TemporalConstraint;
import fape.core.planning.temporaldatabases.TemporalDatabase;
import planstack.anml.model.abs.AbstractAction;
import planstack.anml.model.concrete.Action;
import planstack.anml.model.concrete.VarRef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * non-null values represent the option
 *
 * @author FD
 */
public class SupportOption {

    public boolean representsCausalLinkAddition() {
        return this.temporalDatabase != -1;
    }

    public TemporalConstraint tCon = null;

    public ActionWithBindings actionWithBindings = null;

    /**
     *
     */
    public int temporalDatabase = -1;
    //public TemporalDatabase tdb;

    /**
     *
     */
    public int precedingChainComponent = -1;
    //public TemporalDatabase.ChainComponent precedingComponent;

    /**
     *
     */
    public AbstractAction supportingAction;

    /**
     *
     */
    public Action actionToDecompose = null;

    /**
     *
     */
    public int decompositionID = -1;

    @Override
    public String toString() {
        //return "" + tdb + " " + precedingComponent + " " + supportingAction + " " + actionToDecompose;
        if (temporalDatabase != -1 && precedingChainComponent != -1) {
            // this is database merge of one persistence into another
            return "{merge of two databases, tdb=" + temporalDatabase + ", preceding=" + precedingChainComponent;
        } else if (temporalDatabase != -1) {
            //this is a database concatenation
            return "{DB Concatenation w/ " + temporalDatabase + "}";
        } else if (supportingAction != null) {
            //this is a simple applciation of an action
            return "{ActionApplication " + supportingAction + "}";
        } else if (actionToDecompose != null) {
            // this is a task decomposition
            return "{ActionDecomposition " + actionToDecompose + ", dec=" + decompositionID + "}";
        } else if (tCon != null) {
            // this is a temporal constraint            
            return "{TemporalConstraint " + tCon.first + " -> " + tCon.second + ":" + "[" + tCon.min + "," + tCon.max + "]";
        } else if (this instanceof StateVariableBinding) {
            StateVariableBinding svb = (StateVariableBinding) this;
            // this is a temporal constraint            
            return "{SVBinding: " + svb.one + " + " + svb.two;
        } else {
            return "Unknown option.";
        }
    }
}
