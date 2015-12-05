package planstack.anml.model

import planstack.anml.model.concrete.{EmptyVarRef, RefCounter, VarRef}


class PartialContext(val parentContext:Option[AbstractContext]) extends AbstractContext {

  def addUndefinedVar(name:LVarRef, typeName:String, refCounter: RefCounter = null) {
    assert(!variables.contains(name), "Local variable already defined: "+name)
    variables.put(name, new EmptyVarRef(typeName))
  }

  def addUndefinedAction(localID:LActRef) {
    assert(!actions.contains(localID))
    actions.put(localID, null)
  }

  /**
   * Creates a new local var with type tipe. Returns the name of the created variable.
   * @param tipe Type of the variable to create
   * @return Name of the new local variable
   */
  def getNewLocalVar(tipe:String) : LVarRef = {
    var i = 0
    var lVarRef = new LVarRef("locVar_"+i)
    while(contains(lVarRef)) {
      i += 1
      lVarRef = new LVarRef("locVar_"+i)
    }
    addUndefinedVar(lVarRef, tipe)
    lVarRef
  }

  /**
   * Builds a new concrete context (i.e. all local vars map to a global var) by
   *  - adding all (local, global) variable pairs to the new context.
   *  - creating the missing global variables using `factory`
   * @param parent Concrete context to be added as the parent of the built context
   * @param newVars map of (localVar -> globalVar) to be added to the context)
   * @return
   */
  def buildContext(pb:AnmlProblem, parent:Option[Context], refCounter: RefCounter, newVars:Map[LVarRef, VarRef] = Map()) = {
    val context = new Context(parent)

    for((local, global) <- variables) {
      if(global.isEmpty && newVars.contains(local)) {
        context.addVar(local, newVars(local))
      } else if(global.isEmpty) {
        val globalVar = new VarRef(global.typ, refCounter)
        context.addVar(local, globalVar)
        context.addVarToCreate(globalVar)
      } else {
        context.addVar(local, global)
      }
    }

    for((localActionID, action) <- actions) {
      context.addAction(localActionID, action)
    }
    context
  }

}