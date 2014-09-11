package planstack.anml.parser

import scala.util.parsing.combinator._


sealed trait AnmlBlock
sealed trait ActionContent
sealed trait DecompositionContent
sealed trait TypeContent




case class TemporalStatement(annotation:Option[TemporalAnnotation], statement:Statement) extends AnmlBlock with ActionContent with DecompositionContent

case class TemporalAnnotation(start:RelativeTimepoint, end:RelativeTimepoint, flag:String) {
  require(flag == "is" || flag == "contains")
}

case class RelativeTimepoint(tp:Option[TimepointRef], delta:Int) {
  def this(str:String) = this(Some(new TimepointRef(str)), 0)
  def this(abs:Int) = this(None, abs)
}


case class TimepointRef(extractor:String, id:String) {
  def this(extractor:String) = this(extractor, "")
}

case class Operator(op:String)

sealed abstract class Statement(id:String)

case class SingleTermStatement(val term : Expr, val id:String) extends Statement(id)
case class TwoTermsStatement(left:Expr, op:Operator, right:Expr, id:String) extends Statement(id)
case class ThreeTermsStatement(left:Expr, op1:Operator, middle:Expr, op2:Operator, right:Expr, id:String) extends Statement(id)




case class Action(name:String, args:List[Argument], content:List[ActionContent]) extends AnmlBlock

object Motivated extends ActionContent

sealed trait Duration extends ActionContent {
  if(minDur.isInstanceOf[NumExpr]) {
    val f = minDur.asInstanceOf[NumExpr].value
    assert((f - f.toInt.toFloat) == 0.0, "Duration is not an integer: "+minDur)
  }
  if(maxDur.isInstanceOf[NumExpr]) {
    val f = maxDur.asInstanceOf[NumExpr].value
    assert((f - f.toInt.toFloat) == 0.0, "Duration is not an integer: "+maxDur)
  }

  def minDur : Expr
  def maxDur : Expr
}

case class ExactDuration(dur : Expr) extends Duration {
  override def minDur = dur
  override def maxDur = dur
}

case class BoundedDuration(minDur : Expr, maxDur : Expr) extends Duration


case class Argument(tipe:String, name:String)

case class Decomposition(content:Seq[DecompositionContent]) extends ActionContent

sealed trait Expr {
  def functionName : String
}
case class VarExpr(variable:String) extends Expr {
  override def functionName = variable
}
case class FuncExpr(svExpr:List[String], args:List[VarExpr]) extends Expr {
  override def functionName = svExpr.mkString(".")
}

case class NumExpr(value : Float) extends Expr {
  override def functionName = value.toString
}

case class TemporalConstraint(tp1:TimepointRef, operator:String, tp2:TimepointRef, delta:Int)
  extends DecompositionContent with ActionContent with AnmlBlock {
  require(operator == "=" || operator == "<")
}

class Function(val name:String, val args:List[Argument], val tipe:String, val isConstant:Boolean) extends AnmlBlock with TypeContent

case class SymFunction(
    override val name:String,
    override val args:List[Argument],
    override val tipe:String,
    override val isConstant:Boolean)
  extends Function(name, args, tipe, isConstant)
{
  assert(tipe != "integer" && tipe != "float", "Symbolic function with a numeric type: "+this)
}

class NumFunction(
    override val name:String,
    override val args:List[Argument],
    override val tipe:String,
    override val isConstant:Boolean,
    val resourceType:Option[String])
  extends Function(name, args, tipe, isConstant)
{
  resourceType match {
    case None => // OK
    case Some(x) => assert(x=="consumable" | x=="producible" | x=="reusable" | x=="replenishable")
  }
}

case class IntFunction(
    override val name:String,
    override val args:List[Argument],
    override val tipe:String,
    override val isConstant:Boolean,
    minValue:Int,
    maxValue:Int,
    override val resourceType:Option[String])
  extends NumFunction(name, args, tipe, isConstant, resourceType)
{
  assert(tipe == "integer", "The type of this int function is not an integer: "+this)
  assert(minValue <= maxValue, "Error: min value greater than max value in integer function: "+this)
}

case class FloatFunction(
                          override val name:String,
                          override val args:List[Argument],
                          override val tipe:String,
                          override val isConstant:Boolean,
                          minValue:Float,
                          maxValue:Float,
                          override val resourceType:Option[String])
  extends NumFunction(name, args, tipe, isConstant, resourceType)
{
  assert(tipe == "float", "The type of this float function is not a float: "+this)
  assert(minValue <= maxValue, "Error: min value greater than max value in float function: "+this)
}

class Constant(override val name :String, override val tipe:String) extends Function(name, Nil, tipe, true) with DecompositionContent

case class Type(name:String, parent:String, content:List[TypeContent]) extends AnmlBlock

case class Instance(tipe:String, name:String) extends AnmlBlock

object AnmlParser extends JavaTokenParsers {

  def annotation : Parser[TemporalAnnotation] = (
    annotationBase<~"contains" ^^ { case TemporalAnnotation(start, end, _) => TemporalAnnotation(start, end, "contains")}
      | annotationBase
    )

  /** A temporal annotation like `[all]` `[start, end]`, `[10, end]`, ...
    * The flag of this temporal annotation is set to "is" (it doesn't look for the "contains" keyword
    * after the annotation
    */
  def annotationBase : Parser[TemporalAnnotation] = (
    "["~"all"~"]" ^^
      (x => TemporalAnnotation(new RelativeTimepoint("start"), new RelativeTimepoint("end"), "is"))
      | "["~> repsep(timepoint,",") <~"]" ^^ {
      case List(tp) => TemporalAnnotation(tp, tp, "is")
      case List(tp1, tp2) => TemporalAnnotation(tp1, tp2, "is")
    })

  def timepoint : Parser[RelativeTimepoint] = (
    opt(timepointRef)~("+"|"-")~decimalNumber ^^ {
      case tp~"+"~delta => RelativeTimepoint(tp, delta.toInt)
      case tp~"-"~delta => RelativeTimepoint(tp, - delta.toInt)
    }
      | decimalNumber ^^ { case x => RelativeTimepoint(None, x.toInt)}
      | timepointRef ^^ (x => RelativeTimepoint(Some(x), 0))
      | failure("illegal timepoint")
    )

  def timepointRef : Parser[TimepointRef] = (
    kwTempAnnot~"("~word<~")" ^^ {
      case kw~"("~id => TimepointRef(kw, id) }
      | kwTempAnnot ^^
      (kw => TimepointRef(kw, ""))
    )

  def statement : Parser[Statement] = (
      statementWithoutID
    | ident~":"~statementWithoutID ^^ {
        case id~":"~s => s match {
          case SingleTermStatement(e, "") => SingleTermStatement(e, id)
          case TwoTermsStatement(e1, o, e2, "") => TwoTermsStatement(e1, o, e2, id)
          case ThreeTermsStatement(e1, o1, e2, o2, e3, "") => ThreeTermsStatement(e1, o1, e2, o2, e3, id)
        }}
    | statementWithoutID
    )

  def statementWithoutID : Parser[Statement] = (
      expr<~";" ^^ (e => new SingleTermStatement(e, ""))
    | expr~op~expr<~";" ^^ { case e1~o~e2 => new TwoTermsStatement(e1, o, e2, "") }
    | expr~op~expr~op~expr<~";" ^^ { case e1~o1~e2~o2~e3 => new ThreeTermsStatement(e1, o1, e2, o2, e3, "") }
    )

  /** Temporal constraint between two time points. It is of the form:
    * `start(xx) < end + x`, `start = end -5`, ...
    */
  def tempConstraint : Parser[TemporalConstraint] =
    timepointRef~("<"|"=")~timepointRef~opt(constantAddition)<~";" ^^ {
      case tp1~op~tp2~None => TemporalConstraint(tp1, op, tp2, 0)
      case tp1~op~tp2~Some(delta) => TemporalConstraint(tp1, op, tp2, delta)
    }

  /** Any string of the form `+ 10`, `- 2`, ... Returns an integer*/
  def constantAddition[Int] = ("+"|"-")~decimalNumber ^^ {
    case "+"~num => num.toInt
    case "-"~num => - num.toInt
  }

  def expr : Parser[Expr] = (
      decimalNumber ^^ { x => NumExpr(x.toFloat) }
    | "-"~>decimalNumber ^^ { x => NumExpr(-x.toFloat) }
    | repsep(word,".")~opt(refArgs) ^^ {
        case List(variable) ~ None => VarExpr(variable)
        case svExpr~None => FuncExpr(svExpr, Nil)
        case svExpr~Some(args) => FuncExpr(svExpr, args)
      }
  )

  /** a var expr is a single word such as x, prettyLongName, ... */
  def varExpr : Parser[VarExpr] =
    word ^^ (x => VarExpr(x))

  /** List of variable expression such as (x, y, z) */
  def refArgs : Parser[List[VarExpr]] =
    "("~>repsep(varExpr, ",")<~")"

  def action : Parser[Action] =
    "action"~>word~"("~repsep(argument, ",")~")"~actionBody ^^
      { case name~"("~args~")"~body => new Action(name, args, body)}

  def actionBody : Parser[List[ActionContent]] = (
    "{"~>rep(actionContent)<~"}"~";" ^^ (_.flatten)
      | "{"~"}"~";" ^^ (x => List())
    )

  def actionContent : Parser[List[ActionContent]] = (
    temporalStatements
      | decomposition ^^ (x => List(x))
      | tempConstraint ^^ (x => List(x))
      | "motivated"~";" ^^^ List(Motivated)
      | "duration"~":="~>expr<~";" ^^ (x => List(ExactDuration(x)))
      | "duration"~":in"~"["~>expr~","~expr<~"]"~";" ^^ {
        case min~","~max => List(BoundedDuration(min, max))
      }
    )

  def decomposition : Parser[Decomposition] =
    ":decomposition"~"{"~>rep(decompositionContent)<~"}"~";" ^^ {
      case content => Decomposition(content.flatten)
    }

  def decompositionContent : Parser[List[DecompositionContent]] = (
    tempConstraint ^^ (x => List(x))
      | temporalStatements
      | "constant"~>(word|kwType)~word<~";" ^^ {
      case tipe~name => List(new Constant(name, tipe))
    })


  def argument : Parser[Argument] = (
    tipe~word ^^ { case tipe~name => new Argument(tipe, name)}
      | failure("Argument malformed.")
    )

  def temporalStatements : Parser[List[TemporalStatement]] = (
      annotation~statements ^^ { case annot~statements => statements.map(new TemporalStatement(Some(annot), _))}
    | statement ^^ { case s:Statement => List(new TemporalStatement(None, s))}
  )

  def statements : Parser[List[Statement]] = (
    "{"~>rep(statement)<~"}"<~";"
      | statement ^^ (x => List(x)))

  def block : Parser[List[AnmlBlock]] = (
    action ^^ (a => List(a))
      | temporalStatements
      | tempConstraint ^^ (x => List(x))
      | functionDecl ^^ (func => List(func))
      | typeDecl ^^ (t => List(t))
      | instanceDecl
    )

  def anml : Parser[List[AnmlBlock]] = rep(block) ^^ (blockLists => blockLists.flatten)

  def argList : Parser[List[Argument]] =
    "("~>repsep(argument,",")<~")"

  def functionDecl : Parser[Function] = numFunctionDecl | symFunctionDecl

  def anySymType : Parser[String] = symType | word

  def symFunctionDecl : Parser[SymFunction] = (
    "constant"~>anySymType~word~opt(argList)<~";" ^^ {
      case t~name~Some(args) => SymFunction(name, args, t, isConstant=true)
      case t~name~None => SymFunction(name, Nil, t, isConstant=true)
    }
      | "variable"~>anySymType~word<~";" ^^ {case t~name => SymFunction(name, List(), t, isConstant=false)}
      | "function"~>anySymType~word~argList<~";" ^^ {case t~name~args => SymFunction(name, args, t, isConstant=false)}
      | "predicate"~>anySymType~argList<~";" ^^ {case name~args => SymFunction(name, args, "boolean", isConstant=false)}
    )

  def numFunctionDecl : Parser[NumFunction] = integerFunctionDecl | floatFunctionDecl

  def numFunctionType : Parser[String] = "constant" | "variable" | "function" | "consumable" | "producible" | "reusable" | "replenishable"

  def floatInterval : Parser[(Float, Float)] = (
    "["~>floatingPointNumber~","~floatingPointNumber<~"]" ^^ { case min~","~max => (min.toFloat, max.toFloat) }
      | "["~>floatingPointNumber~","~"infinity"<~"]" ^^ { case min~","~_ => (min.toFloat, Float.MaxValue) })

  def intInterval : Parser[(Int, Int)] = (
    "["~>floatingPointNumber~","~floatingPointNumber<~"]" ^^ { case min~","~max => (min.toInt, max.toInt) }
      | "["~>floatingPointNumber~","~"infinity"<~"]" ^^ { case min~","~_ => (min.toInt, Int.MaxValue) })

  def floatFunctionDecl : Parser[FloatFunction] =
    numFunctionType~"float"~opt(floatInterval)~word~opt(argList)<~";" ^^ {
      case fType~"float"~interval~name~argsOpt => {
        val resourceType =
          if(fType == "constant" || fType == "variable" || fType == "function") None
          else Some(fType)
        val constraints = interval match {
          case None => (Float.MinValue, Float.MaxValue)
          case Some(anInterval) => anInterval
        }
        val args = argsOpt match {
          case None => List()
          case Some(arguments) => arguments
        }
        new FloatFunction(name, args, "float", fType == "constant", constraints._1, constraints._2, resourceType)
      }
    }

  def integerFunctionDecl : Parser[IntFunction] =
    numFunctionType~"integer"~opt(intInterval)~word~opt(argList)<~";" ^^ {
      case fType~"integer"~interval~name~argsOpt => {
        val resourceType =
          if(fType == "constant" || fType == "variable" || fType == "function") None
          else Some(fType)
        val constraints = interval match {
          case None => (Int.MinValue, Int.MaxValue)
          case Some(anInterval) => anInterval
        }
        val args = argsOpt match {
          case None => List()
          case Some(arguments) => arguments
        }
        new IntFunction(name, args, "integer", fType == "constant", constraints._1, constraints._2, resourceType)
      }
    }


  def tipe : Parser[String] =
    kwType | word | failure("Unable to parse type")

  def typeDecl : Parser[Type] = (
    "type"~>tipe~"<"~tipe~"with"~typeBody<~";" ^^ {case name~"<"~parent~"with"~content => Type(name, parent, content)}
      | "type"~>tipe~"with"~typeBody<~";" ^^ {case name~"with"~content => Type(name, "", content)}
      | "type"~>tipe~"<"~tipe<~";" ^^ {case name~"<"~parent => Type(name, parent, List())}
      | "type"~>tipe<~";" ^^ (name => Type(name, "", List()))
      | failure("Not a valid type declaration")
    )

  def typeBody : Parser[List[TypeContent]] = "{"~>rep(functionDecl)<~"}"

  def instanceDecl : Parser[List[Instance]] =
    "instance"~>tipe~repsep(word,",")<~";" ^^ {
      case tipe~names => names.map(Instance(tipe, _))
    }

  /** all predefined types: boolean, float, integer, object */
  def kwType : Parser[String] = numType | symType

  /** predefined symbolic types: boolean, object */
  def symType : Parser[String] = "boolean" | "object"

  /** Predefined numeric types: float and integer */
  def numType : Parser[String] = "float" | "integer"

  def kwTempAnnot : Parser[String] = "start" | "end"

  def keywords = kwType | kwTempAnnot | "motivated" | "duration"

  def word = not(keywords) ~> ident


  def op : Parser[Operator] = opString ^^ { case op:String => Operator(op) }
  private def opString : Parser[String] =
    "==" | ":=" | ":->" | ":produce" | ":consume" | ":use" | "<" | "<=" | ">=" | ">" | "!="

}



object Test extends App {

  import java.io.FileReader

import planstack.anml.parser.AnmlParser._


  val arg = ""
  println("input : "+ arg)
//  println(parseAll(temporalStatements, "[all] x.v\n == g :-> q;"))

  //val block = "action Move(Robot r, Loc a, Loc b){[end] location(r) == a;}; [all] x.v == g;"
//  val block = ":decomposition{ a(), b(i) };"
//  parseAll(decomposition, block) match {
//    case Success(res, _) => println(res)
//    case x => println("Failure: "+x)
//  }



  parseAll(anml, new FileReader("resources/test.anml")) match {
    case Success(res, _) => {
      println(res.mkString("\n"))
    }
    case x => {
      println(x)
    }
  }
}