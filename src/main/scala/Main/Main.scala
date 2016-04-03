package Main

import akka.pattern.ask
import akka.actor.{Props, Actor}
import akka.event.Logging
import akka._
import akka.actor.ActorSystem
import akka.util.Timeout
import scala.collection.generic.SeqFactory
import scala.concurrent.duration._
import scala.collection.parallel.ParSeq

import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by Gaplo917 on 2/4/2016.
  */

class CountActor extends Actor {
  val log = Logging(context.system, this)

  var count = 0
  var solutions = List[List[Int]]()

  def receive = {
    case c: Int => count += c
    case saveSolution: CountActor.SaveSolution => if(solutions.size < 50) solutions = solutions :+ saveSolution.xs
    case CountActor.AskSolutions => sender() ! CountActor.Solutions(count,solutions)
    case _      => log.info("received unknown message")
  }
}
object CountActor {
  case object AskSolutions
  case class SaveSolution(xs: List[Int])
  case class Solutions(count: Int, results: List[List[Int]])
}

object Main {

  val BASE = 27

  val WIDTH = 4
  //  val WIDTH = 4
  val system = ActorSystem("mySystem")
  val countActor = system.actorOf(Props[CountActor], "countActor")
  system.scheduler.schedule(0 second, 10 seconds, new Runnable {
    override def run(): Unit = {
      println(s"Total memory used ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)}MB")
    }
  })

  val baseAnd1 = BASE + 1
  val possibleToTry = (0 until BASE).filter(x => x != 1).toList

  def intToChar(x:Int) = if( x > 9) (x - 10 + 'a'.toInt).toChar else (x - 1 + '1'.toInt).toChar

  object BranchState extends Enumeration {
    type BranchState = Value
    val Initial,State_1, State_11,State_0,State_10 = Value
  }

  import BranchState._

  def leadingDigitPossibleSolution(possibleToTry: List[Int], result:Int, lastLending:Int) = {
    possiblePermuatationN(possibleToTry,3)
      .withFilter {
        case xs@List(a,c,g) =>
          val na = a - lastLending
          val e = result - g
          na > 1 && na > c && c > 1 && g > 1 && e != 1 && e != a && e != c && e != g && na - c + g == result
      }
      .map{
        case xs@List(a,c,g) =>
          List(a, c, result - g, g)
      }.toStream.par
  }

  def innerDigitPossibleSolution(possibleToTry: List[Int], result:Int, lending:Int, lastLending:Int) = {
    possiblePermuatationN(possibleToTry,3)
      .withFilter {
        case xs@List(b,d,h) =>
          val f = result - h
          f != 1 && f >= 0  && f < BASE && f != b && f != d && f != h && (b + lending * BASE) - lastLending - d + h == result
      }
      .map{
        case xs@List(b,d,h) =>
          List(b, d, result - h, h)
      }.toStream.par
  }


  val InnerDigitPossibleSolutionMap  =
    Map(
      (State_11,1,1) -> innerDigitPossibleSolution(possibleToTry,baseAnd1,1,1),
      (State_11,1,0) -> innerDigitPossibleSolution(possibleToTry,baseAnd1,1,0),
      (State_11,0,1) -> innerDigitPossibleSolution(possibleToTry,baseAnd1,0,1),
      (State_11,0,0) -> innerDigitPossibleSolution(possibleToTry,baseAnd1,0,0),
      (State_10,1,1) -> innerDigitPossibleSolution(possibleToTry,BASE,1,1),
      (State_10,1,0) -> innerDigitPossibleSolution(possibleToTry,BASE,1,0),
      (State_10,0,1) -> innerDigitPossibleSolution(possibleToTry,BASE,0,1),
      (State_10,0,0) -> innerDigitPossibleSolution(possibleToTry,BASE,0,0)
    )


  val leadingDigitPossibleSolutionMap =
    Map(
      (State_11,1) -> leadingDigitPossibleSolution(possibleToTry,baseAnd1,1),
      (State_11,0) -> leadingDigitPossibleSolution(possibleToTry,baseAnd1,0),
      (State_10,1) -> leadingDigitPossibleSolution(possibleToTry,BASE,1),
      (State_10,0) -> leadingDigitPossibleSolution(possibleToTry,BASE,0)
    )

  def mainRecur() = {
    func(
      branchState = State_11,
      lending = 1
    )
    func(
      branchState = State_11,
      lending = 0
    )
  }

  def func(branchState: BranchState = Initial, pos: Int = 1, lending:Int = 0, lastLending: Int = 0, possibleSolutions: ParSeq[List[Int]] = ParSeq()):  Unit= {

    if(pos == WIDTH) {
      leadingDigitPossibleSolutionMap(branchState,lastLending).foreach { case ps@List(ps0,ps1,ps2,ps3) =>
        possibleSolutions.withFilter(xs => !xs.contains(ps0) && !xs.contains(ps1) && !xs.contains(ps2) && !xs.contains(ps3)).foreach { xs =>
          countActor ! 1
          countActor ! CountActor.SaveSolution(ps ++ xs)
        }
      }

    } else {

      val innerDigitPossibleSolutions = InnerDigitPossibleSolutionMap(branchState,lending,lastLending)

      val newPossibleSolutions = if(pos > 1) InnerDigitPossibleSolutionMap(branchState,lending,lastLending).flatMap {
        case ps@List(ps0,ps1,ps2,ps3) => possibleSolutions.withFilter(xs => !xs.contains(ps0) && !xs.contains(ps1) && !xs.contains(ps2) && !xs.contains(ps3)).map(ps ++ _)
      } else innerDigitPossibleSolutions

      branchState match {
        case State_11 if pos + 1 == WIDTH =>
          // lending is not possible if the next position is the leading digit
          func(
            branchState = State_10,
            lending = 0,
            lastLending = lending,
            pos = pos + 1,
            possibleSolutions = newPossibleSolutions
          )

        case State_11 =>
          func(
            branchState = State_10,
            lending = 0,
            lastLending = lending,
            pos = pos + 1,
            possibleSolutions = newPossibleSolutions
          )
            func(
              branchState = State_10,
              lending = 1,
              lastLending = lending,
              pos = pos + 1,
              possibleSolutions = newPossibleSolutions
            )

        case State_10 if pos + 1 == WIDTH  =>
            // lending is not possible if the next position is the leading digit
            func(
              branchState = State_10,
              lending = 0,
              lastLending = lending,
              pos = pos + 1,
              possibleSolutions = newPossibleSolutions
            )
        case State_10  =>
          func(
            branchState = State_10,
            lending = 0,
            lastLending = lending,
            pos = pos + 1,
            possibleSolutions = newPossibleSolutions
          )
            func(
              branchState = State_10,
              lending = 1,
              lastLending = lending,
              pos = pos + 1,
              possibleSolutions = newPossibleSolutions
            )


      }

    }

  }

  def possiblePermuatationN(xs: List[Int], n: Int) = {
    xs.combinations(n)
      .flatMap(_.permutations)
  }

  def main(args: Array[String]): Unit = {

    val start = System.currentTimeMillis()
    println("Start Calculation")

    val solutions = mainRecur()

    implicit val timeout: Timeout = 10 seconds

    (countActor ? CountActor.AskSolutions).mapTo[CountActor.Solutions].foreach { solutions =>
      println(s"Total number of solutions = ${solutions.count}")
      solutions.results.foreach(printEqu)
    }


    println(s"Total Time used to solve (Base $BASE, Width $WIDTH): ${System.currentTimeMillis() - start}ms")
  }

  def printEqu(xs: List[Int]) = {
    val listWithIndex = xs.map(intToChar).zipWithIndex
    // abcdef - ghijkl =  mnopqr,  mnopqr  + stuvwx = 111111
    val abcdef = listWithIndex.collect {
      case (x,i) if i % 4 == 0 => x
    }.mkString("")
    val ghijkl = listWithIndex.collect {
      case (x,i) if i % 4 == 1 => x
    }.mkString("")
    val mnopqr = listWithIndex.collect {
      case (x,i) if i % 4 == 2 => x
    }.mkString("")
    val stuvwx = listWithIndex.collect {
      case (x,i) if i % 4 == 3 => x
    }.mkString("")

    // don't learn this, too lazy to use for-loop only lol
    val yyyyyyy = List(1,1,1,1,1,1,1,1,1,1).take(WIDTH + 1).mkString("")

    println(s"$abcdef - $ghijkl = $mnopqr,  $mnopqr + $stuvwx = $yyyyyyy")
  }
}
