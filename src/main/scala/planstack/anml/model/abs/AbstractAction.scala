package planstack.anml.model.abs

import planstack.anml.{ANMLException, parser}
import scala.collection.mutable
import planstack.anml.parser._
import planstack.anml.parser.TemporalStatement
import planstack.anml.parser.ActionRef
import planstack.anml.model.{LVarRef, PartialContext, AnmlProblem}

import collection.JavaConversions._

class AbstractAction(val name:String, val args:List[LVarRef], val context:PartialContext)  {

  val decompositions = mutable.ArrayBuffer[AbstractDecomposition]()
  def jDecompositions = seqAsJavaList(decompositions)

  val temporalStatements = mutable.ArrayBuffer[AbstractTemporalStatement]()
  def jTemporalStatements = seqAsJavaList(temporalStatements)

}

object AbstractAction {

  /** Factory method to build an abstract action
    *
    * @param act Action from the parser to be converted
    * @param pb Problem in which the action is defined
    * @return
    */
  def apply(act:parser.Action, pb:AnmlProblem) : AbstractAction = {
    val action = new AbstractAction(act.name, act.args.map(a => new LVarRef(a.name)), new PartialContext(Some(pb.context)))

    act.args foreach(arg => {
      action.context.addUndefinedVar(new LVarRef(arg.name), arg.tipe)
    })

    act.content foreach( _ match {
      case ts:parser.TemporalStatement => {
        action.temporalStatements += AbstractTemporalStatement(pb, action.context, ts)
      }
      case dec:parser.Decomposition => {
        action.decompositions += AbstractDecomposition(pb, action.context, dec)
      }
    })

    action
  }
}