package checkers.inference
package pbssolver

import scala.collection.mutable.ListBuffer

/*
object Formula {
		def this(in: Elem) = {
			AndOfOrs(List(OrOfElems(List(in))))
	}

}*/

// TODO: add a way to add comments in the formula

object AndOfOrs {
  def apply(newors: OrOfElems*): AndOfOrs = {
    new AndOfOrs(newors: _*)
  }

  def apply(newor: Elem): AndOfOrs = {
    new AndOfOrs(newor)
  }
}

object OrOfElems {
  def apply(newels: Elem*): OrOfElems = {
    new OrOfElems(newels: _*)
  }
}

/*
 * A list of or-ed elements that are all and-ed.
 */
class AndOfOrs {

  val ors = new ListBuffer[OrOfElems]()

  def this(newors: OrOfElems*) {
    this()
    ors.append(newors: _*)
  }

  def this(in: Elem) = {
    this(new OrOfElems(in))
  }

  def or(in: AndOfOrs): (AndOfOrs, BVar) = {
    val bvar = BVar()
    val res = this.orEach(bvar).and(in.orEach(Not(bvar)))

    (res, bvar)
  }

  private def orEach(el: Elem): AndOfOrs = {
    ors.map((x: OrOfElems) => x.or(el))
    this
  }

  def and(in: AndOfOrs): AndOfOrs = {
    ors.append(in.ors: _*)
    this
  }

  override def toString: String = {
    ors.mkString(" /\\\n")
  }
}

class OrOfElems {

  val elems = new ListBuffer[Elem]()

  def this(newelems: Elem*) {
    this()
    elems.append(newelems: _*)
  }

  def or(els: Elem*): OrOfElems = {
    elems.append(els: _*)
    this
  }

  override def toString: String = {
    elems.mkString("(", " \\/ ", ")")
  }
}

sealed abstract trait Elem {
  def implies(el: Elem): OrOfElems = {
    new OrOfElems(Not(this), el)
  }

  def or(el: Elem): OrOfElems = {
    new OrOfElems(this, el)
  }
}

object BVar {
  var nextid: Int = 0

  def apply(): BVar = {
    // Should the first index be 0 or 1? Make sure this is consistent with e.g.
    // GUTConstraintSolver.bvarBase
    val res = new BVar(nextid)
    nextid += 1
    res
  }
}

case class BVar(val id: Int) extends Elem {
  override def toString: String = {
    "BVar #" + id
  }
}

case class EqConst(val v: AbstractVariable, val c: Constant) extends Elem {
  override def toString: String = {
    v.toString + "==" + c.toString
  }
}

case class Not(val el: Elem) extends Elem {
  override def toString: String = {
    "!(" + el.toString + ")"
  }
}

// I removed EqVar for equality between two variables, because it could not
// be represented as a simple Or, it needs an Or for each constant, anded together.

object FormulaTest {
  def main(args: Array[String]) {
    val v1 = new Variable(null, 1)
    val v2 = new Variable(null, 2)
    val v3 = new Variable(null, 3)

    // val o1 = OrOfElems(List(EqVar(v1, v2), Not(EqVar(v2, v3))))
    // println("O1: " + o1)
  }
}
