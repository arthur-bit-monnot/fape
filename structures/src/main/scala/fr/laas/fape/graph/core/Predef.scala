package fr.laas.fape.graph.core

import fr.laas.fape.graph.core.impl.{MultiLabeledDirectedAdjacencyList, MultiUnlabeledDirectedAdjacencyList, SimpleLabeledDirectedAdjacencyList, SimpleUnlabeledDirectedAdjacencyList}


/** A directed graph with labeled edges */
trait LabeledDigraph[V,EL] extends Graph[V,EL, LabeledEdge[V,EL]] with LabeledGraph[V,EL] with DirectedGraph[V, EL, LabeledEdge[V,EL]] {
  def cc : LabeledDigraph[V,EL]
}

object LabeledDigraph {
  def apply[V,EL]() : LabeledDigraph[V,EL] = new MultiLabeledDirectedAdjacencyList[V,EL]()
}

/** A directed graph with no label on its edges */
trait UnlabeledDigraph[V] extends Graph[V, Nothing, Edge[V]] with UnlabeledGraph[V] with DirectedGraph[V,Nothing,Edge[V]] {
  def cc : UnlabeledDigraph[V]
}

object UnlabeledDigraph {
  def apply[V]() : UnlabeledDigraph[V] = new MultiUnlabeledDirectedAdjacencyList[V]()
}

/** A simple directed graph with labeled edges */
trait SimpleLabeledDigraph[V,EL] extends LabeledDigraph[V,EL] with SimpleGraph[V, EL, LabeledEdge[V,EL]] {
  def cc: SimpleLabeledDigraph[V,EL]
}

object SimpleLabeledDigraph {
  def apply[V,EL]() : SimpleLabeledDigraph[V,EL] = new SimpleLabeledDirectedAdjacencyList[V,EL]()
}

/** A directed multi-graph with labeled edges */
trait MultiLabeledDigraph[V,EL] extends LabeledDigraph[V,EL] with MultiGraph[V, EL, LabeledEdge[V,EL]] {
  def cc : MultiLabeledDigraph[V,EL]
}

object MultiLabeledDigraph {
  def apply[V,EL]() : MultiLabeledDigraph[V,EL] = new MultiLabeledDirectedAdjacencyList[V,EL]()
}

/** A simple directed graph with no label on its edges */
trait SimpleUnlabeledDigraph[V] extends UnlabeledDigraph[V] with SimpleGraph[V, Nothing, Edge[V]] {
  def cc : SimpleUnlabeledDigraph[V]
}

object SimpleUnlabeledDigraph {
  def apply[V]() : SimpleUnlabeledDigraph[V] = new SimpleUnlabeledDirectedAdjacencyList[V]()
}

/** A directed multi graph with labeled edges */
trait MultiUnlabeledDigraph[V] extends UnlabeledDigraph[V] with MultiGraph[V, Nothing, Edge[V]] {
  def cc : MultiUnlabeledDigraph[V]
}

object MultiUnlabeledDigraph {
  def apply[V]() : MultiUnlabeledDigraph[V] = new MultiUnlabeledDirectedAdjacencyList[V]()
}