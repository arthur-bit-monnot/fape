package planstack.constraints.stnu

import net.openhft.koloboke.collect.map.hash.{HashIntIntMap, HashIntObjMap}
import planstack.UniquelyIdentified
import planstack.constraints.Kolokobe
import planstack.constraints.stn.ISTN
import planstack.graph.core.LabeledEdge
import planstack.graph.printers.NodeEdgePrinter
import Controllability._
import planstack.structures.IList
import ElemStatus._
import planstack.structures.Converters._

protected class TConstraint[TPRef,ID](val u:TPRef, val v:TPRef, val min:Int, val max:Int, val optID:Option[ID])

class PseudoSTNUManager[TPRef <: UniquelyIdentified,ID](val stn : FullSTN[ID],
                                  _tps : HashIntObjMap[TimePoint[TPRef]],
                                  _ids : HashIntIntMap,
                                  _rawConstraints : List[Constraint[TPRef,ID]],
                                  _start : Option[TPRef],
                                  _end : Option[TPRef])
  extends GenSTNUManager[TPRef,ID](_tps, _ids, _rawConstraints, _start, _end)
{
  def this() = this(new FullSTN[ID](), Kolokobe.getIntObjMap[TimePoint[TPRef]], Kolokobe.getIntIntMap, List(), None, None)
  def this(toCopy:PseudoSTNUManager[TPRef,ID]) =
    this(toCopy.stn.cc(), Kolokobe.clone(toCopy.tps), Kolokobe.clone(toCopy.id), toCopy.rawConstraints, toCopy.start, toCopy.end)

  override def controllability = PSEUDO_CONTROLLABILITY

  def contingents = rawConstraints.filter(c => c.tipe == CONTINGENT)

  override def deepCopy(): PseudoSTNUManager[TPRef, ID] = new PseudoSTNUManager(this)

  override def isConsistent: Boolean = {
    stn.consistent && contingents.forall(c => stn.isMinDelayPossible(id.get(c.u.id), id.get(c.v.id), c.d))
  }

  override def exportToDotFile(filename: String, printer: NodeEdgePrinter[Object, Object, LabeledEdge[Object, Object]]): Unit =
    println("Warning: this STNUManager can not be exported to a dot file")

  override protected def isConstraintPossible(u: Int, v: Int, w: Int): Boolean = stn.isConstraintPossible(u, v, w)

  /** If there is a contingent constraint [min, max] between those two timepoints, it returns
    * Some((min, max).
    * Otherwise, None is returned.
    */
  override def contingentDelay(from: TPRef, to: TPRef): Option[(Integer, Integer)] = {
    val min = contingents.find(c => c.u == to && c.v == from).map(c => -c.d)
    val max = contingents.find(c => c.u == from && c.v == to).map(c => c.d)

    if(min.nonEmpty && max.nonEmpty)
      Some((min.get :Integer, max.get :Integer))
    else
      None
  }



  override protected def commitContingent(u: Int, v: Int, d: Int, optID: Option[ID]): Unit =
    // simple commit a controllable constraint, the contingency will be checked in isConsistent
    commitConstraint(u, v, d, optID)

  override protected def commitConstraint(u: Int, v: Int, w: Int, optID: Option[ID]): Unit =
    optID match {
      case Some(id) => stn.addConstraintWithID(u, v, w, id)
      case None => stn.addConstraint(u, v, w)
    }

  /** Returns the latest time for the time point with id u */
  override protected def latestStart(u: Int): Int = stn.latestStart(u)

  /** should remove a constraint from the underlying STNU */
  override protected def performRemoveConstraintWithID(id: ID): Boolean =
    stn.removeConstraintsWithID(id)

  /** Returns the earliest time for the time point with id u */
  override protected def earliestStart(u: Int): Int = stn.earliestStart(u)

  override def getMinDelay(u: TPRef, v: TPRef): Int = stn.minDelay(id.get(u.id), id.get(v.id))

  override def getMaxDelay(u: TPRef, v: TPRef): Int = stn.maxDelay(id.get(u.id), id.get(v.id))
}
