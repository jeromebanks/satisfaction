package satisfaction


import org.specs2.mutable._
import org.junit.runner.RunWith
import Temporal._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SequenceOptionSpec extends Specification {

   def curry[A,B,C](f: (A, B) => C): A => (B => C) = 
     ( a : A ) => {
         ( b: B)  => { f(a,b) }
   }
     
     
     /**
   def variance(xs: Seq[Double]): Double =  {
     xs.foldLeft(0)({ (l:Double,r: Double) => {
        (l + r ).toDouble
     }
     })
     
   }
   * **/
 def sequence[A](a: List[Option[A]]): Option[List[A]]  = {
    val l : List[A] = a.foldRight( List[A]() ) ( (l : Option[A], r : List[A]) => {
         l :: r
       }
    )
    Some(l)
}
  
  "sequenceOption" should {
      "sequence" in {
         
        val list1 : List[Option[String]] = List( 
             Some(" A"),
             Some(" B"),
             Some(" C"),
             Some(" D")
        )
        val seqList = sequence( list1)
        println(seqList)
      }
      "sequence2" in {
        val list1 : List[Option[String]] = List( 
             None,
             Some(" A"),
             Some(" B"),
             Some(" C"),
             Some(" D")
        )
        val seqList = sequence( list1)
        println(seqList)
      }
     
  } 
     
     
}