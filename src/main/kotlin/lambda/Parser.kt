package lambda

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
    abstract fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int, function: StringBuilder, prototypes: StringBuilder, code: StringBuilder)

    abstract fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): Triple<String, String, String>
}
data class Abstraction(val body: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "(\\ $body)"
    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int, function: StringBuilder, prototypes: StringBuilder, code: StringBuilder) {
        val fnName = pathName + "_f"

        val builder = StringBuilder()
        //create a prototype for this function
        prototypes.append("function* $fnName(function*, function**);\n")

        //create the definition of this function and compile the body into it
        builder.append("function* $fnName(function* f, function** c) {\n")
        builder.append("    printf(\"$fnName called\\n\");\n")
        builder.append("    fflush(stdout);\n")
        body.compile(fnName + "_ret", dbToArray + Pair(depth + 1, closedValue.size), depth + 1, builder, prototypes, code)
        builder.append("    return ${fnName}_ret;\n")
        builder.append("}\n")
        code.append(builder.toString() + "\n")

        //construct the function call for this function to be inserted into the code of the calling function
        function.append("    function* $pathName = (function*)malloc(sizeof(function));\n")
        function.append("    function** ${pathName}_c = (function**)malloc(sizeof(function*) * ${dbToArray.size});\n")
        for(i in closedValue) {
            if(i == depth) function.append("    ${pathName}_c[${dbToArray[i]}] = f;\n")
            else function.append("    ${pathName}_c[${dbToArray[i]}] = c[${dbToArray[i]}];\n")
        }
        function.append("    $pathName->ptr = &$fnName;\n")
        function.append("    $pathName->closedValues = ${pathName}_c;\n")
    }

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): Triple<String, String, String> {
        val fnName = pathName + "_f"
        val retName = fnName + "_ret"
        val numClosures = closedValue.size

        val (body, prototypes, program) = body.compile(retName, dbToArray + Pair(depth + 1, numClosures), depth + 1)
        val definition =
            abstractionDefinition
                .replace("[[name]]", fnName)
                .replace("[[body]]", body)
                .replace("[[return]]", retName)

        val closures =
            closedValue.fold("") { acc, i ->
                val closure =
                    if(i == depth) closureFromArgument
                    else closureFromClosedValue

                val updated =
                    closure
                        .replace("[[name]]", pathName)
                        .replace("[[index]]", "${dbToArray[i]}")

                acc + updated
            }

        val call =
            abstractionCall
                .replace("[[name]]", pathName)
                .replace("[[fnName]]", fnName)
                .replace("[[size]]", "$numClosures")
                .replace("[[closures]]", closures)

        val protos = prototypes + abstractionPrototype.replace("[[name]]", fnName)

        return Triple(call, protos, program + definition)
    }
}
data class Application(val l: Expression, val r: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "($l $r)"
    override fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int, function: StringBuilder, prototypes: StringBuilder, code: StringBuilder) {
        r.compile(pathName + "_r", cvsToIndex, depth, function, prototypes, code);
        l.compile(pathName + "_l", cvsToIndex, depth, function, prototypes, code);
        function.append("    function* $pathName = ${pathName}_l->ptr(${pathName}_r, ${pathName}_l->closedValues);\n")
    }

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): Triple<String, String, String> {
        val nameR = pathName + "_r"
        val nameL = pathName + "_l"

        val (bodyR, prototypesR, programR) = r.compile(nameR, dbToArray, depth)
        val (bodyL, prototypesL, programL) = l.compile(nameL, dbToArray, depth)

        val application =
            applicationDefinition
                .replace("[[name]]", pathName)
                .replace("[[nameR]]", nameR)
                .replace("[[nameL]]", nameL)

        return Triple(bodyR + bodyL + application, prototypesR + prototypesL, programR + programL)
    }
}
data class Name(val index: Int): Expression() {
    override val closedValue = setOf(index)
    override fun toString(): String = "$index"
    override fun compile(pathName: String, cvsToIndex: Map<Int, Int>, depth: Int, function: StringBuilder, prototypes: StringBuilder, code: StringBuilder) {
        if(index == depth) { function.append("    function* $pathName = f;\n") }
        else function.append("    function* $pathName = c[${cvsToIndex[index]}];\n")
    }

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): Triple<String, String, String> {
        val value =
            if(index == depth) valueFromArgument
            else valueFromClosedValue

        val updated =
            value
                .replace("[[name]]", pathName)
                .replace("[[index]]", "${dbToArray[index]}")

        return Triple(updated, "", "")
    }
}

fun m1() {
    val p = tokenize("((fn x x) (fn y y))").map { it.parse() }

    val builder = StringBuilder()
    val prototypes = StringBuilder()
    val code = StringBuilder()
    p[0].compile("f", mapOf(), -1, builder, prototypes, code)

    println("#include <stdio.h>")
    println("#include <stdlib.h>")
    println("typedef struct function {")
    println("    struct function* (*ptr)(struct function*, struct function**);")
    println("    struct function** closedValues;")
    println("} function;")
    println()

    println(prototypes.toString())
    println(code.toString())

    println("int main() {")
    println(builder.toString())
    println("    return 0;")
    println("}")

}

fun main() {
    val p = tokenize("((((fn x (fn y (fn z (x (y z))))) (fn c c)) (fn b b)) (fn a a))").map { it.parse() }

    val (body, prototypes, functions) = p[0].compile("f", mapOf(), -1)
    val program =
        mainDefinition
            .replace("[[body]]", body)
            .replace("[[functions]]", functions)
            .replace("[[prototypes]]", prototypes)

    println(program)
}