package planstack.graph.algorithms

import org.scalatest.FunSuite
import planstack.graph.core.DirectedGraph

class TopologicalSortingSuite extends FunSuite {

  val g = DirectedGraph[Int]()

  g.addVertex(1)
  g.addVertex(2)
  g.addVertex(3)
  g.addEdge(1, 2)

  test("Topo Sorting") {
    val order = Algos.topologicalSorting(g)
    assert(order.length === g.numVertices, "All vertices should be in the ordering")
    assert(order.head == 1 || order.head == 3)
  }

}
