package abcdefghppp.actors

import akka.actor.Actor
import akka.event.Logging

/**
  * Created by Gaplo917 on 3/4/2016.
  */
class CountActor extends Actor {
  val log = Logging(context.system, this)

  var count = 0
  var solutions = List[List[Int]]()

  def receive = {

    case saveSolution: CountActor.SaveSolution =>
      // count the solution
      count += 1

      if(solutions.size < 50){
        solutions :+= saveSolution.xs
      }

    case CountActor.AskSolutions =>
      sender() ! CountActor.Solutions(count,solutions)

    case _ => log.info("received unknown message")
  }
}
object CountActor {
  case object AskSolutions
  case class SaveSolution(xs: List[Int])
  case class Solutions(count: Int, results: List[List[Int]])
}