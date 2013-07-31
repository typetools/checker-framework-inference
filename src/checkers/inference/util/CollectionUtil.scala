package checkers.inference.util

import scala.collection.mutable.ListBuffer

/**
 * A set of methods useful for scala collections of different types.
 * without
 */
object CollectionUtil {

  /**
   * Creates a traversable of triplets that contains the entries from each of the tuples in the
   * traversable plus the value corresponding to the same index in third.  If the lengths of
   * the traversables differ the method does one of two things:
   * 1.  If lengthMustBeEqual == true:
   *     throw an assertion error
   * 2.  else
   *     extra values are ignored
   * @param seq  A traversable of tuples
   * @param third A traversable of single values
   * @param lengthMustBeEqual If true asserts list.size == third.size
   * @tparam X The type of the first tuple member
   * @tparam Y The type of the second tuple member
   * @tparam Z The type of the values in third
   * @return A list of triplets of type ( X,Y,Z )
   */
  //TODO: I don't think this works for TraversableOnces
  def zip3[X,Y,Z]( seq : Seq[(X,Y)], third : Seq[Z], lengthMustBeEqual : Boolean = false ) : List[(X,Y,Z)] = {
    if( lengthMustBeEqual ) {
      assert ( seq.size == third.size )
    }

    val lb = new ListBuffer[(X,Y,Z)]()

    if( seq.size < third.size ) {
      for(i <- 0 until seq.size ) {
        val li = seq(i)
        lb += Tuple3[X,Y,Z]( li._1, li._2, third(i) )
      }
    } else {
      for(i <- 0 until third.size) {
        val li = seq(i)
        lb += Tuple3[X,Y,Z]( li._1, li._2, third(i) )
      }
    }

    return lb.toList

  }

  /**
   * Calls zip3 on traversable with a third argument being the index numbers for all elements of traversable
   *
   * @param seq The traversable list of tuples we would like to add an index to
   * @tparam X The type of the first tuple member
   * @tparam Y The type of the second tuple member
   * @return a List with the original tuples transformed to triplets with an index in the third position
   */
  def zip3WithIndex[X,Y]( seq : Seq[(X,Y)] ) : List[(X,Y,Int)] = {
    return zip3( seq, 0 until seq.size, true )
  }

  /**
   * Given a List of (X,X) create a flat list of X values by traversing the list and pairwise placing
   * the first and second elements of each tuple in the list.  Whether the first element or second element of each
   * tuple appear first is determined by the parameter num2First.
   *
   * e.g.
   *   interlace( List((1,2), (3,4)), num2First=false ) = List( 1,2,3,4 )
   *   interlace( List((1,2), (3,4)), num2First=true )  = List( 2,1,4,3 )
   *
   * @param seq A sequence of tuples to interlace
   * @param num2First Whether or not the second member of the tuple should appear before the first member
   * @tparam X The type of BOTH values in the tuple
   * @return A list of X
   */
  def interlace[X]( seq : Seq[(X,X)], num2First : Boolean = false ) : List[X] = {
    val interlaced = new ListBuffer[X]
    seq.foreach( elem => {
      if( !num2First ) {
        interlaced += elem._1
        interlaced += elem._2
      } else {
        interlaced += elem._2
        interlaced += elem._1
      }
    })
    interlaced.toList
  }
}
