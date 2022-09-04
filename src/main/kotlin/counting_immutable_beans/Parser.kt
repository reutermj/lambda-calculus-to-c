package counting_immutable_beans

import java.io.File

sealed class Expression {
    abstract fun evaluate(context: Context): Location
}

data class ConstAppFull(val fnRef: FnName, var args: List<Variable>) : Expression() {
    override fun evaluate(context: Context): Location {
        val (fnBody, parameters) = FnDefs[fnRef]
        assert(args.size == parameters.size)
        val locs = args.map { context[it] }
        return fnBody.evaluate(Context(parameters.zip(locs)))
    }
}

data class ConstAppPart(val fnRef: FnName, var args: List<Variable>) : Expression() {
    override fun evaluate(context: Context): Location {
        val (fnBody, paramters) = FnDefs[fnRef]
        assert(args.size < paramters.size)
        val locations = args.map { context[it] }
        val loc = getNextLocation()
        Heap[loc] = PapValue(fnBody, paramters, locations)
        return loc
    }
}

data class VarPapFull(val fnRef: Variable, val args: List<Variable>) : Expression() {
    override fun evaluate(context: Context): Location {
        val papValue = Heap[context[fnRef]] as PapValue
        val locs = papValue.heapLocs + args.map { context[it] }
        assert(locs.size == papValue.parameters.size)
        return papValue.fnRef.evaluate(Context(papValue.parameters.zip(locs)))
    }
}

data class VarPapPart(var fnRef: Variable, val args: List<Variable>) : Expression() {
    override fun evaluate(context: Context): Location {
        val papValue = Heap[context[fnRef]] as PapValue
        val locs = papValue.heapLocs + args.map { context[it] }
        assert(locs.size < papValue.parameters.size)
        val loc = getNextLocation()
        Heap[loc] = PapValue(papValue.fnRef, papValue.parameters, locs)
        return loc
    }
}

data class Ctor(val name: CtorName, val values: List<Variable>) : Expression() {
    override fun evaluate(context: Context): Location {
        val loc = getNextLocation()
        val locs = values.map { context[it] }
        val names = TypeDefs[name]
        Heap[loc] = CtorValue(name, names.zip(locs).toMap())
        return loc
    }
}

data class Proj(val index: Variable, val variable: Variable) : Expression() {
    override fun evaluate(context: Context): Location {
        val ctor = Heap[context[variable]] as CtorValue
        return ctor[index]
    }
}

sealed class FnBody() {
    abstract fun evaluate(context: Context): Location
}

data class Ret(val retVal: Variable) : FnBody() {
    override fun evaluate(context: Context) = context[retVal]
}

data class Let(val variable: Variable, val expression: Expression, val body: FnBody) : FnBody() {
    override fun evaluate(context: Context): Location {
        val loc = expression.evaluate(context)
        return body.evaluate(context + Pair(variable, loc))
    }
}

data class Case(val variable: Variable, val bodies: Map<CtorName, FnBody>) : FnBody() {
    override fun evaluate(context: Context): Location {
        val ctor = Heap[context[variable]] as CtorValue
        val body = bodies[ctor.ctorName]!!
        return body.evaluate(context)
    }
}

data class Variable(val name: String)
data class FnName(val name: String)
data class CtorName(val name: String)
data class Location(val loc: Int)

var nextLoc = 0
fun getNextLocation() = Location(nextLoc++)

class Context private constructor(private val context: Map<Variable, Location>) {
    constructor(): this(mapOf()) {}
    constructor(bindings: List<Pair<Variable, Location>>): this(bindings.toMap()) {}
    operator fun get(variable: Variable) = context[variable] ?: throw Exception("Could not find $variable in the context")
    operator fun plus(binding: Pair<Variable, Location>) = Context(context + binding)
}
object Heap {
    private val heap = mutableMapOf<Location, HeapValue>()
    operator fun get(location: Location) = heap[location] ?: throw Exception("Could not find $location on the heap")
    operator fun set(location: Location, value: HeapValue) { heap[location] = value }
}

object FnDefs {
    private val fnDefs = mutableMapOf<FnName, Pair<FnBody, List<Variable>>>()
    operator fun get(name: FnName) = fnDefs[name] ?: throw Exception("Could not find function $name")
    operator fun set(name: FnName, value: Pair<FnBody, List<Variable>>) { fnDefs[name] = value }
}

object TypeDefs {
    private val typeDefs = mutableMapOf<CtorName, List<Variable>>()

    operator fun get(name: CtorName) = typeDefs[name] ?: throw Exception("Could not find ctor $name")
    operator fun set(name: CtorName, value: List<Variable>) { typeDefs[name] = value }
}

sealed class HeapValue

data class PapValue(val fnRef: FnBody, val parameters: List<Variable>, val heapLocs: List<Location>) : HeapValue()
data class CtorValue(val ctorName: CtorName, val values: Map<Variable, Location>) : HeapValue() {
    operator fun get(name: Variable) = values[name] ?: throw Exception("Could not find variable with $name")
}

sealed class Token {
    abstract fun parseFnBody(boundVariables: Set<Variable>): FnBody
    abstract fun parseExpression(boundVariables: Set<Variable>): Expression
    abstract fun parseVariable(): Variable
    abstract fun parseCtorName(): CtorName
    abstract fun parseFnName(): FnName

    abstract fun parseProgram()
}
data class SymbolToken(val symbol: String): Token() {
    override fun toString(): String = symbol

    override fun parseVariable(): Variable = Variable(symbol)
    override fun parseCtorName(): CtorName = CtorName(symbol)
    override fun parseFnName(): FnName = FnName(symbol)

    override fun parseFnBody(boundVariables: Set<Variable>): FnBody {
        throw Exception("Expected FnBody; found variable")
    }

    override fun parseExpression(boundVariables: Set<Variable>): Expression {
        throw Exception("Expected expression; found variable")
    }

    override fun parseProgram() {
        throw Exception("Expected program definition; found variable")
    }
}
data class SequenceToken(val children: List<Token>): Token() {
    override fun toString(): String {
        val s = children.fold("") { acc, node -> "$acc $node" }.substring(1)
        return "($s)"
    }

    override fun parseVariable(): Variable {
        throw Exception("Expected variable; found sequence")
    }

    override fun parseCtorName(): CtorName {
        throw Exception("Expected ctor name; found sequence")
    }

    override fun parseFnName(): FnName {
        throw Exception("Expected function name; found sequence")
    }

    override fun parseFnBody(boundVariables: Set<Variable>): FnBody {
        val first = children[0] as SymbolToken
        return when(first.symbol) {
            "let" -> {
                assert(children.size == 3)
                val second = children[1] as SequenceToken
                assert(second.children.size % 2 == 0)
                assert(second.children.size >= 2)
                val variables = second.children.filterIndexed { i, _ -> i % 2 == 0 }
                val expressions = second.children.filterIndexed { i, _ -> i % 2 == 1 }

                fun inner(varExps: List<Pair<Token, Token>>, boundVariables: Set<Variable>): FnBody {
                    val variable = varExps[0].first.parseVariable()
                    val expression = varExps[0].second.parseExpression(boundVariables + variable)
                    return if(varExps.size == 1) {
                        val body = children[2].parseFnBody(boundVariables + variable)
                        Let(variable, expression, body)
                    } else {
                        val body = inner(varExps.drop(1), boundVariables + variable)
                        Let(variable, expression, body)
                    }
                }

                return inner(variables.zip(expressions), boundVariables)
            }
            "case" -> {
                assert(children.size >= 4)
                assert(children.size % 2 == 0)

                val variable = children[1].parseVariable()
                val blas = children.drop(2)
                val ctors = blas.filterIndexed { i, _ -> i % 2 == 0 }
                val bodies = blas.filterIndexed { i, _ -> i % 2 == 1 }
                val cases = ctors.zip(bodies) { ctor, body ->
                    Pair(ctor.parseCtorName(), body.parseFnBody(boundVariables))
                }.toMap()

                Case(variable, cases)
            }
            "ret" -> {
                assert(children.size == 2)
                Ret(children[1].parseVariable())
            }
            else -> throw Exception("Unrecognized fnbody")
        }
    }

    override fun parseExpression(boundVariables: Set<Variable>): Expression {
        val first = children[0] as SymbolToken
        return when(first.symbol) {
            "proj" -> {
                assert(children.size == 3)
                val index = children[1].parseVariable()
                val variable = children[2].parseVariable()
                Proj(index, variable)
            }
            "new" -> {
                assert(children.size >= 2)
                val ctorName = children[1].parseCtorName()
                val args = children.drop(2).map { it.parseVariable() }
                Ctor(ctorName, args)
            }
            "pap" -> {
                assert(children.size >= 2)
                val variable = children[1].parseVariable()
                val args = children.drop(2).map { it.parseVariable() }

                if (boundVariables.contains(variable)) VarPapPart(variable, args)
                else ConstAppPart(children[1].parseFnName(), args)
            }
            else -> {
                val variable = first.parseVariable()
                val args = children.drop(1).map { it.parseVariable() }

                if (boundVariables.contains(variable)) VarPapFull(variable, args)
                else ConstAppFull(first.parseFnName(), args)
            }
        }
    }

    override fun parseProgram() {
        val first = children[0] as SymbolToken
        when(first.symbol) {
            "defn" -> {
                assert(children.size == 4)
                val name = children[1].parseFnName()
                val parameters = children[2] as SequenceToken
                val paramterVariables = parameters.children.map { it.parseVariable() }
                val body = children[3].parseFnBody(paramterVariables.toSet())
                FnDefs[name] = Pair(body, paramterVariables)
            }
            "deftype" -> {
                assert(children.size >= 2)
                val ctorName = children[1].parseCtorName()
                val variableNames = children.drop(2).map { it.parseVariable() }
                TypeDefs[ctorName] = variableNames
            }
            else -> throw Exception("Unrecognized program")
        }
    }
}

fun main() {
    val basePath = File(".").absolutePath
    val file = File(basePath.substring(0, basePath.length - 1) + "resources" + File.separator + "test")
    val program = file.readText()
    val tokens = tokenize(program)
    for(token in tokens) token.parseProgram()
    val (body, vars) = FnDefs[FnName("main")]
    assert(vars.isEmpty())
    println(Heap[body.evaluate(Context())])
}

val whitespace = setOf(' ', '\n', '\t', '\r')
val nameChars = ('a'..'z').toSet() + ('0'..'9').toSet()

fun tokenize(s: String): List<Token> {
    var i = 0
    var nameStart = -1

    fun inner(depth: Int, sequence: MutableList<Token>) {
        while(i < s.length) {
            val currentIndex = i
            fun endName() {
                if(nameStart != -1) {
                    sequence.add(SymbolToken(s.substring(nameStart, currentIndex)))
                    nameStart = -1
                }
            }
            val c = s[i]
            i++

            when (c) {
                in whitespace -> endName()
                in nameChars -> if (nameStart == -1) nameStart = currentIndex
                '(' -> {
                    endName()

                    val seq = mutableListOf<Token>()
                    inner(depth + 1, seq)
                    sequence.add(SequenceToken(seq))
                }
                ')' -> {
                    endName()

                    if(depth == 0) throw Exception("Too Many ')'")
                    else return
                }
            }
        }

        if(depth > 0) throw Exception("EOF")
    }

    val tokens = mutableListOf<Token>()
    inner(0, tokens)
    return tokens
}