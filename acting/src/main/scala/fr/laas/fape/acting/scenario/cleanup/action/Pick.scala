package fr.laas.fape.acting.scenario.cleanup.action

import akka.actor.FSM
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{ExecutionRequest, Failed, TimepointExecuted}
import fr.laas.fape.acting.{Clock, Utils}
import fr.laas.fape.ros.action.{GoToPick, MoveBaseClient, MoveBlind}
import fr.laas.fape.ros.database.Database
import fr.laas.fape.ros.exception.ActionFailure
import fr.laas.fape.ros.request.{GTPPick, GTPUpdate}

class Pick(robot: String)  extends FSM[String,Option[ExecutionRequest]] with MessageLogger {
  startWith("idle", None)

  when("idle") {
    case Event(req:ExecutionRequest, _) =>
      assert(req.name == "Pick")
      val agent :: target :: table :: Nil = req.parameters

      if(!Database.getBool("engaged", false)) {
        log.error("Robot is not engaged, aborting")
        req.caller ! Failed(req)
      }
      Utils.Future {
        try {
          fr.laas.fape.ros.action.LootAt.lookAt(target)
          GTPUpdate.update()
          val pick = new GTPPick(agent, target)
          pick.plan()
          pick.execute()
          self ! "success"
        } catch {
          case e:ActionFailure =>
            log.error("Action failure")
            e.printStackTrace()
            self ! "failure"
        }
      }
      goto("active") using Some(req)
  }

  when("active") {
    case Event("success", Some(exe)) =>
      exe.caller ! TimepointExecuted(exe.action.end, Clock.time())
      goto("idle") using None

    case Event("failure", Some(exe)) =>
      exe.caller ! Failed(exe)
      goto("idle") using None

    case Event(req:ExecutionRequest, Some(previous)) =>
      MoveBlind.cancelAllGoals()
      MoveBaseClient.cancelAllGoals()
      self ! req // reconsider when in idle state
      goto("idle") using None
  }


}
