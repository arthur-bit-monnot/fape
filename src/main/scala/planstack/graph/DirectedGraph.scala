package planstack.graph




trait DirectedGraph[V, E <: Edge[V]] extends Graph[V,E] {

  def inEdges(v:V) : Seq[E]

  def outEdges(u:V) : Seq[E]

  def inDegree(v:V) = inEdges(v).length

  def outDegree(v:V) = outEdges(v).length
}
