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
package fape.model.anml;

import fape.model.compact.ANMLBlock;
import fape.model.compact.Action;
import fape.model.compact.ActionRef;
import fape.model.compact.Function;
import fape.model.compact.Instance;
import fape.model.compact.Reference;
import fape.model.compact.TemporalConstraint;
import fape.model.compact.TemporalInterval;
import fape.model.compact.statements.Assign;
import fape.model.compact.statements.AssignFunctional;
import fape.model.compact.statements.Equality;
import fape.model.compact.statements.Statement;
import fape.model.compact.types.Type;
import fape.util.Pair;
import java.util.LinkedList;
import java.util.List;
import org.antlr.runtime.tree.Tree;

/**
 *
 * @author FD
 */
public class ANMLFactory {

    public static ANMLBlock Parse(Tree t) {
        ANMLBlock b = new ANMLBlock();

        //types
        for (int i = 0; i < t.getChild(0).getChildCount(); i++) {
            b.types.add(parseType(t.getChild(0).getChild(i)));
        }

        //instances
        for (int i = 0; i < t.getChild(2).getChildCount(); i++) {
            b.instances.add(parseInstance(t.getChild(2).getChild(i)));
        }

        //actions
        for (int i = 0; i < t.getChild(4).getChildCount(); i++) {
            b.actions.add(parseAction(t.getChild(4).getChild(i)));
        }

        //statements
        for (int i = 0; i < t.getChild(5).getChildCount(); i++) {
            b.statements.addAll(parseStatements(t.getChild(5).getChild(i)));
        }

        return b;
    }

    private static Type parseType(Tree child) {
        if (!child.getText().equals("Type")) {
            throw new UnsupportedOperationException("Typecheck failed.");
        }
        Type tp = new Type();
        tp.name = child.getChild(0).getText();
        tp.parent = child.getChild(2).getText();
        if (child.getChild(3).getChildCount() > 0) {
            Tree fluents = child.getChild(3).getChild(0).getChild(2);
            for (int i = 0; i < fluents.getChildCount(); i++) {
                tp.instances.add(parseInstance(fluents.getChild(i)));
            }
        }
        return tp;
    }

    private static Instance parseInstance(Tree child) {
        Instance in = new Instance();
        in.name = child.getChild(0).getText();
        in.type = child.getChild(1).getChild(0).getText();
        return in;
    }

    private static Action parseAction(Tree child) {
        Action a = new Action();
        //name
        a.name = child.getChild(0).getText();
        //parameters
        for (int i = 0; i < child.getChild(1).getChildCount(); i++) {
            a.params.add(parseInstance(child.getChild(1).getChild(i)));
        }
        //parse statements
        for (int i = 0; i < child.getChild(7).getChildCount(); i++) {
            a.statements.addAll(parseStatements(child.getChild(7).getChild(i)));
        }
        //parse decompositions
        for (int i = 0; i < child.getChild(8).getChildCount(); i++) {
            a.strongDecompositions.add(parseDecomposition(child.getChild(8).getChild(i)));
        }
        //what more to parse? TODO
        // - duration
        // - hard refinement
        // - soft refinement
        // - weak decompositions  
        return a;
    }

    private static List<Statement> parseStatements(Tree child) {
        List<Statement> ret = new LinkedList<>();
        //3 variants
        switch (child.getText()) {
            case "Skip": {
                //do nothing
                break;
            }
            case "Chain": {
                Assign rt = new Assign();
                rt.interval = parseInterval(child.getChild(0));
                rt.leftRef = parseReference(child.getChild(1));
                rt.from = parseReference(child.getChild(2).getChild(0));
                if(child.getChild(2).getText().equals("==")){
                    rt.operator = "==";
                    rt.to = parseReference(child.getChild(4).getChild(0));
                }else{
                    rt.operator = ":=";
                }
                ret.add(rt);
                break;
            }
            case "==": {
                Equality rt = new Equality();
                rt.leftRef = parseReference(child.getChild(0));
                rt.rightRef = parseReference(child.getChild(1));
                ret.add(rt);
                break;
            }
            case "TimedStmt": {
                TemporalInterval interval = parseInterval(child.getChild(0));
                List<Statement> statements = new LinkedList<>();
                for (int i = 0; i < child.getChild(1).getChild(4).getChildCount(); i++) {
                    statements.addAll(parseStatements(child.getChild(1).getChild(4).getChild(i)));
                }
                for (Statement s : statements) {
                    s.interval = interval;
                }
                ret.addAll(statements);
                break;
            }
            case ":=": {
                //another assign
                AssignFunctional af = new AssignFunctional();
                af.label = child.getChild(0).getChild(1).getText();
                af.func = parseFunction(child.getChild(1));
                ret.add(af);
                break;
            }
            default:
                throw new UnsupportedOperationException();
        }
        return ret;
    }

    private static Reference parseReference(Tree child) {
        Reference r = new Reference();
        while (child.getText().equals("AccessField")) {
            r.refs.addFirst(child.getChild(1).getChild(0).getText()); //->ref->name
            child = child.getChild(0);
        }
        if (child.getChild(0) != null) {
            r.refs.addFirst(child.getChild(0).getText());
        } else {
            r.refs.addFirst(child.getText());
        }
        return r;
    }

    private static TemporalInterval parseInterval(Tree child) {
        TemporalInterval in = new TemporalInterval();
        if (child.getChildCount() == 1) {
            in.e = child.getChild(0).getText();
            in.s = child.getChild(0).getText();
        } else {
            in.e = child.getChild(1).getText();
            in.s = child.getChild(3).getText();
        }
        return in;
    }

    private static Pair<List<ActionRef>, List<TemporalConstraint>> parseDecomposition(Tree child) {
        Pair<List<ActionRef>, List<TemporalConstraint>> ret = new Pair<>();
        ret.value1 = new LinkedList<>();
        ret.value2 = new LinkedList<>();
        Tree decs = child.getChild(4).getChild(0).getChild(4);
        for (int i = 0; i < decs.getChildCount(); i++) {
            ActionRef ar = new ActionRef();
            ar.name = decs.getChild(i).getChild(0).getChild(0).getText();
            for (int j = 0; j < decs.getChild(i).getChild(1).getChildCount(); j++) {
                ar.args.add(parseReference(decs.getChild(i).getChild(1).getChild(j)));
            }
            ret.value1.add(ar);
        }
        return ret;
    }

    private static Function parseFunction(Tree child) {
        Function f = new Function();
        switch (child.getText()) {
            case "/": {
                f.mOperator = Function.EOperator.DIVIDE;
                break;
            }
            case "*": {
                f.mOperator = Function.EOperator.MULTIPLY;
                break;
            }
            case "+": {
                f.mOperator = Function.EOperator.PLUS;
                break;
            }
            case "-": {
                f.mOperator = Function.EOperator.MINUS;
                break;
            }
        }
        f.left = parseReference(child.getChild(0));
        f.right = parseReference(child.getChild(1));
        return f;
    }
}
