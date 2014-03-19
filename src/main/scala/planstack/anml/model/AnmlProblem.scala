package planstack.anml.model

import collection.JavaConversions._
import planstack.anml.{ANMLException, parser}

import planstack.graph.core.impl.SimpleUnlabeledDirectedAdjacencyList
import planstack.anml.parser.{ParseResult, FuncExpr, VarExpr}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import planstack.anml.model.concrete.{Action, StateModifier, BaseStateModifier, TemporalInterval}
import planstack.anml.model.concrete.statements.TemporalStatement
import planstack.anml.model.abs.{AbstractTemporalStatement, AbstractActionRef, AbstractAction}


class AnmlProblem extends TemporalInterval {

  val instances = new InstanceManager
  val functions = new FunctionManager
  val context = new Context(None)

  val abstractActions = ListBuffer[AbstractAction]()
  def jAbstractActions = seqAsJavaList(abstractActions)

  val modifiers = ArrayBuffer[StateModifier]()
  def jModifiers = seqAsJavaList(modifiers)

  private var nextGlobalVarID = 0
  private var nextActionID = 0

  // create an initial modifier containing the predifined instances (true and false)
  modifiers += new BaseStateModifier(this, Nil, Nil, Nil, instances.instances.map(_._1))

  /** Returns a new, unused, identifier for a global variable */
  def newGlobalVar : VarRef = new VarRef("globVar_" + {nextGlobalVarID+=1; nextGlobalVarID-1})

  /** Returns a new, unused, identifier for an action */
  def newActionID = "action_" + {nextActionID+=1; nextActionID-1}


  protected def expressionToValue(expr:parser.Expr) : String = {
    expr match {
      case v:VarExpr => {
        if(instances.containsInstance(v.variable))
          v.variable
        else
          throw new ANMLException("Unknown instance: "+v.variable)
      }
      case x => throw new ANMLException("Cannot find value for expression "+expr)
    }
  }

  def getAction(name:String) = abstractActions.find(_.name == name) match {
    case Some(act) => act
    case None => throw new ANMLException("No action named "+name)
  }

  def addAnml(anml:ParseResult) = addAnmlBlocks(anml.blocks)

  def addAnmlBlocks(blocks:Seq[parser.AnmlBlock]) {

    var modifier = new BaseStateModifier(this, Nil, Nil, Nil, Nil)

    blocks.filter(_.isInstanceOf[parser.Type]).map(_.asInstanceOf[parser.Type]) foreach(typeDecl => {
      instances.addType(typeDecl.name, typeDecl.parent)
    })

    blocks.filter(_.isInstanceOf[parser.Instance]).map(_.asInstanceOf[parser.Instance]) foreach(instanceDecl => {
      instances.addInstance(instanceDecl.name, instanceDecl.tipe)
      modifier = modifier.withInstances(instanceDecl.name)
    })

    for((name, tipe) <- instances.instances) {
      context.addVar(new LVarRef(name), tipe, new VarRef(name))
    }

    blocks.filter(_.isInstanceOf[parser.Function]).map(_.asInstanceOf[parser.Function]) foreach(funcDecl => {
      functions.addFunction(funcDecl)
    })

    blocks.filter(_.isInstanceOf[parser.Type]).map(_.asInstanceOf[parser.Type]) foreach(typeDecl => {
      typeDecl.content.filter(_.isInstanceOf[parser.Function]).map(_.asInstanceOf[parser.Function]).foreach(scopedFunction => {
        functions.addScopedFunction(typeDecl.name, scopedFunction)
        instances.addMethodToType(typeDecl.name, scopedFunction.name)
      })
    })

    blocks.filter(_.isInstanceOf[parser.TemporalStatement]).map(_.asInstanceOf[parser.TemporalStatement]) foreach(tempStatement => {
      val ts = AbstractTemporalStatement(this, this.context, tempStatement)
      modifier = modifier.withStatements(TemporalStatement(context, ts))
    })

    blocks.filter(_.isInstanceOf[parser.Action]).map(_.asInstanceOf[parser.Action]) foreach(actionDecl => {
      val abs = AbstractAction(actionDecl, this)
      abstractActions += abs

      if(abs.name == "Seed" || abs.name == "seed") {
        val id = newActionID
        context.addActionID(new LActRef(id), new ActRef(id))
        val act = Action(this, new AbstractActionRef(abs.name, null, new LActRef(id)),null)
        modifier = modifier.withActions(act)
      }
    })

    modifiers += modifier
  }

}
