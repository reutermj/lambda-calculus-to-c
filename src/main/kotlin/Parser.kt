sealed class Token {
    abstract fun parse(indexLookup: Map<String, Int>, depth: Int): Expression

    fun parse(): Expression = parse(mapOf(), 0)
}
data class SymbolToken(val symbol: String): Token() {
    override fun toString(): String = symbol

    override fun parse(indexLookup: Map<String, Int>, depth: Int): Expression =
        Name(indexLookup[symbol] ?: throw Exception("couldnt find $symbol"))
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

sealed class Expression {
    abstract val closedValue: Set<Int>
    abstract fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int)
}
data class Abstraction(val body: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "(\\ $body)"
    override fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int) {
        val p2 = pathName + "_f"
        println("function* $p2(function* f, function** c) {")
        body.compile(p2 + "_ret", cvsToIndex + Pair(depth, depth), depth)
        println("return ${p2}_ret;")
        println("}")

        println("function* $pathName = (function*)malloc(sizeof(function));")
        println("function** ${pathName}_c = (function**)malloc(sizeof(function*) * ${cvsToIndex.size});")
        println("$pathName->ptr = &$p2;")
        println("$pathName->closedValues = ${pathName}_c;")
    }
}
data class Application(val l: Expression, val r: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "($l $r)"
    override fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int) {
        r.compile(pathName + "_r", cvsToIndex, depth);
        l.compile(pathName + "_l", cvsToIndex, depth);
        println("function* $pathName = ${pathName}_l->ptr(${pathName}_r, ${pathName}_l->closedValues);")
    }
}
data class Name(val index: Int): Expression() {
    override val closedValue = setOf(index)
    override fun toString(): String = "$index"
    override fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int) {
        if(index == depth) { println("function* $pathName = f;") }
        else println("function* $pathName = c[${cvsToIndex[index]}];")
    }
}

fun main() {
    val p = tokenize("(fn x ((fn y (x y)) (x x)))").map { it.parse() }
    println(p)
    p[0].compile("f", mapOf(), 0)
}