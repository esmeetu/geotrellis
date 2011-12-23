package trellis.operation

import trellis.data.ColorBreaks
import trellis.process.Server
import trellis.stat._

/**
  * Generate quantile class breaks with assigned colors.
  */
case class FindColorBreaks(h:Operation[Histogram], n:Int,
                           colors:Array[Int]) extends Operation[ColorBreaks] 
                                              with SimpleOperation[ColorBreaks] {
  def childOperations = { List(h) }
  def _value(server:Server) = {
    val histogram = server.run(h)
    val breaks = histogram.getQuantileBreaks(n)
    ColorBreaks(breaks.zip(colors))
  }
}
