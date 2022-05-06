sealed class Token
data class SymbolToken(val symbol: String): Token() {
    override fun toString(): String = symbol
}
data class SequenceToken(val children: List<Token>): Token() {
    override fun toString(): String {
        val s = children.fold("") { acc, node -> "$acc $node" }.substring(1)
        return "($s)"
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

sealed class Expression {
    abstract fun closedValues(depth: Int): Set<Int>
    abstract fun compile(prototypes: MutableList<String>, definitions: MutableList<String>, cvsToIndex: Map<Int, Int>, depth: Int)
}
data class Abstraction(val body: Expression): Expression() {
    override fun toString(): String = "(\\ $body)"
    override fun closedValues(depth: Int): Set<Int> = body.closedValues(depth + 1) - depth
    override fun compile(prototypes: MutableList<String>, definitions: MutableList<String>, cvsToIndex: Map<Int, Int>, depth: Int) {
        when(body) {
            is Abstraction -> {
                val cvs = body.closedValues(depth + 1)
                val newCvsToIndex =
                    if(cvs.contains(depth)) cvsToIndex + Pair(depth, cvsToIndex.size)
                    else cvsToIndex

                val buffer = StringBuffer();

                if(newCvsToIndex.size > 0) {
                    buffer.append("function** cv = (function**)malloc(sizeof(function*));\n")
                    for(i in 0 until depth) {
                        val index = cvsToIndex[i]
                        if(index != null)
                            buffer.append("cv[$index] = closedValues[$index];\n");
                    }

                    if(newCvsToIndex.contains(depth))
                        buffer.append("cv[${cvsToIndex.size}] = f;\n");
                }

                println(buffer.toString())
                body.compile(prototypes, definitions, newCvsToIndex, depth + 1)
            }
        }
    }
}
data class Application(val function: Expression, val parameter: Expression): Expression() {
    override fun toString(): String = "($function $parameter)"
    override fun closedValues(depth: Int): Set<Int> = function.closedValues(depth) + parameter.closedValues(depth)
    override fun compile(prototypes: MutableList<String>, definitions: MutableList<String>, cvsToIndex: Map<Int, Int>, depth: Int) {

    }
}
data class Name(val index: Int): Expression() {
    override fun toString(): String = "$index"
    override fun closedValues(depth: Int): Set<Int> = setOf(index)
    override fun compile(prototypes: MutableList<String>, definitions: MutableList<String>, cvsToIndex: Map<Int, Int>, depth: Int) {

    }
}

fun parse(tokens: List<Token>): List<Expression> {
    fun inner(token: Token, indexLookup: Map<String, Int>, depth: Int): Expression {
        return when(token) {
            is SequenceToken -> {
                val first = token.children[0]
                if(first is SymbolToken && first.symbol == "fn") {
                    val second = token.children[1]
                    if(second is SymbolToken) {
                        val newLookup = indexLookup + Pair(second.symbol, depth)
                        Abstraction(inner(token.children[2], newLookup, depth + 1))
                    }
                    else throw Exception("You didnt use a name after fn")
                }
                else Application(inner(first, indexLookup, depth), inner(token.children[1], indexLookup, depth))
            }
            is SymbolToken -> Name(indexLookup[token.symbol] ?: throw Exception("couldnt find ${token.symbol}"))
        }
    }

    return tokens.map { inner(it, mapOf(), 0) }
}

fun main() {
    val p = parse(tokenize("(fn x (fn y (fn z ((x y) z))))"))
    println(p)
    p[0].compile(mutableListOf(), mutableListOf(), mapOf(), 0)
}