package fr.laas.fape.constraints.stnu.structurals

import java.util

import fr.laas.fape.constraints.stnu.InconsistentTemporalNetwork


import scala.collection.mutable

object DistanceMatrix {
  val growthIncrement = 5
  val INF :Int = Integer.MAX_VALUE /2 -1

  /** addition that will never overflow given that both parameters are in [-INF,INF] */
  final def plus(a:Int, b: Int) = {
    if(a == INF || b == INF)
      INF
    else {
      val total = a+b
      assert(a+b < INF)
      assert(a > -INF)
      assert(b > -INF)
      a + b
    }
  }
}

trait DistanceMatrixListener {
  def distanceUpdated(a: Int, b: Int)
}

import DistanceMatrix._

class DistanceMatrix(
                      var dists: Array[Array[Int]],
                      val emptySpots: mutable.Set[Int],
                      val defaultValue: Int
                    ) {

  def this() = this(new Array[Array[Int]](0), mutable.Set(), INF)

  val listeners = mutable.ArrayBuffer[DistanceMatrixListener]()

  private final def isActive(tp: Int) = {
    assert(tp < dists.size)
    !emptySpots.contains(tp)
  }

  def createNewNode(): Int = {
    if(emptySpots.isEmpty) {
      // grow matrix
      val prevLength = dists.length
      val newLength = prevLength + growthIncrement
      val newDists = util.Arrays.copyOf(dists, newLength)
      for(i <- 0 until prevLength) {
        newDists(i) = util.Arrays.copyOf(dists(i), newLength)
        util.Arrays.fill(newDists(i), prevLength, newLength, defaultValue)
      }
      for(i <- prevLength until newLength) {
        newDists(i) = new Array[Int](newLength)
        util.Arrays.fill(newDists(i), defaultValue)
        emptySpots += i
      }
      dists = newDists
    }
    val newNode = emptySpots.head
    emptySpots -= newNode
    dists(newNode)(newNode) = 0
    newNode
  }

  /**
    * Removes a node from the network. Note that all constraints previously inferred will stay in the matrix
    */
  private def eraseNode(n: Int): Unit = {
    util.Arrays.fill(dists(n), defaultValue)
    for(i <- dists.indices)
      dists(i)(n) = defaultValue
    emptySpots += n
  }

  def enforceDist(a: Int, b: Int, d: Int): Unit = {
    if(plus(d, dists(b)(a)) < 0)
      throw new InconsistentTemporalNetwork
    if(d >= dists(a)(b))
      return // constraint is dominated

    dists(a)(b) = d
    updated(a,b)

    val I = mutable.ArrayBuffer[Int]()
    val J = mutable.ArrayBuffer[Int]()
    for(k <- dists.indices if !emptySpots.contains(k) && !emptySpots.contains(a) && !emptySpots.contains(b) && k != a && k!= b) {
      if(dists(k)(b) > plus(dists(k)(a), d)) {
        dists(k)(b) = plus(dists(k)(a), d)
        updated(k,b)
        I += k
      }
      if(dists(a)(k) > plus(d, dists(b)(k))) {
        dists(a)(k) = plus(d, dists(b)(k))
        updated(a,k)
        J += k
      }
    }
    for(i <- I ; j <- J if i != j && !emptySpots.contains(i) && !emptySpots.contains(j)) {
      if(dists(i)(j) > plus(dists(i)(a), dists(a)(j))) {
        dists(i)(j) = plus(dists(i)(a), dists(a)(j))
        updated(i,j)
      }

    }
  }

  def compileAwayRigid(anchoredTimepoint: Int, anchor: Int): Unit = {
    assert(anchoredTimepoint != anchor)
    assert(dists(anchor)(anchoredTimepoint) == -dists(anchoredTimepoint)(anchor), "Trying to compile a non rigid relation")
    for(i <- dists.indices) {
      if(isActive(i) && dists(i)(anchor) > plus(dists(i)(anchoredTimepoint), dists(anchoredTimepoint)(anchor))) {
        dists(i)(anchor) = plus(dists(i)(anchoredTimepoint), dists(anchoredTimepoint)(anchor))
        updated(i, anchor)
      }
      if(isActive(i) && dists(anchor)(i) > plus(dists(anchor)(anchoredTimepoint), dists(anchoredTimepoint)(i))) {
        dists(anchor)(i) = plus(dists(anchor)(anchoredTimepoint), dists(anchoredTimepoint)(i))
        updated(anchor, i)
      }
    }
    eraseNode(anchoredTimepoint)
  }

  def getDistance(a: Int, b: Int): Int = {
    assert(!emptySpots.contains(a) && !emptySpots.contains(b))
    dists(a)(b)
  }

  private final def updated(a: Int, b: Int): Unit = {
    assert(dists(a)(b) < INF)
    for(list <- listeners)
      list.distanceUpdated(a, b)
  }
}
