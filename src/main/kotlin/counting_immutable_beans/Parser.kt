package counting_immutable_beans

sealed class Expression

data class ConstAppFull(val fnRef: FnName, var args: Array<Variable>) : Expression() {

}

data class ConstAppPart(val fnRef: FnName, var args: Array<Variable>) : Expression() {

}

data class VarPapFull(val fnRef: Variable, val args: Array<Variable>) : Expression() {

}

data class VarPapPart(var fnRef: Variable, val args: Array<Variable>) : Expression() {

}

data class Ctor(var ctorVariable: CtorName, var args: Array<Expression>) : Expression() {

}

data class Proj(var ctorVariable: CtorName, var index: Int, var variable: Variable) : Expression() {

}

sealed class FnBody()

data class Ret(var retVal: Variable) : FnBody() {

}

data class Let(var variable: Variable, var expression: Expression, var body: FnBody) : FnBody() {

}

data class Case(var variable: Variable, var bodies: Array<Pair<CtorName, FnBody>>) : FnBody() {

}

data class Variable(var name: String)
data class FnName(var name: String)
data class CtorName(var name: String)
data class Location(var loc: Int)

sealed class HeapValue

data class PapValue(var fnRef: FnName, var heapLoc: Int) : HeapValue()
data class CtorValue(var ctorName: CtorName, var values: Array<Location>) : HeapValue()

sealed class Token {
    abstract fun parse(indexLookup: Map<String, Int>, depth: Int): Expression

    fun parse(): Expression = parse(mapOf(), 0)
}
data class SymbolToken(val symbol: String): Token() {
    override fun toString(): String = symbol

    override fun parse(indexLookup: Map<String, Int>, depth: Int): Expression =
        Variable(indexLookup[symbol] ?: throw Exception("couldnt find $symbol"))
}
data class SequenceToken(val children: List<Token>): Token() {
    override fun toString(): String {
        val s = children.fold("") { acc, node -> "$acc $node" }.substring(1)
        return "($s)"
    }

    override fun parse(indexLookup: Map<String, Int>, depth: Int): Expression {
        val first = children[0]
        return if(first is SymbolToken && first.symbol == "fn") {
            val second = children[1]
            if(second is SymbolToken) {
                val newLookup = indexLookup + Pair(second.symbol, depth)
                val body = children[2].parse(newLookup, depth + 1)
                Abstraction(body, body.closedValue - depth)
            }
            else throw Exception("You didnt use a name after fn")
        }
        else {
            val function = first.parse(indexLookup, depth)
            val argument = children[1].parse(indexLookup, depth)
            Application(function, argument, function.closedValue + argument.closedValue)
        }
    }
}

val whitespace = setOf(' ', '\n', '\t')
val nameChars = ('a'..'z').toSet()

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