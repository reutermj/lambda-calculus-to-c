package lambda

import java.io.File

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

data class CompilerStuff(val body: String, val prototypes: String, val program: String, val deletes: String)

sealed class Expression {
    abstract val closedValue: Set<Int>
    abstract fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): CompilerStuff
}
data class Abstraction(val body: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "(\\ $body)"

    private fun compileImpl(pathName: String, dbToArray: Map<Int, Int>, depth: Int, callTemplate: String, umm: String): CompilerStuff {
        val fnName = pathName + "_f"
        val retName = fnName + "_ret"
        val numClosures = closedValue.size

        //generate the definition of the abstraction
        val (body, prototypes, program, deletes) = body.compile(retName, dbToArray + Pair(depth + 1, numClosures), depth + 1)
        val definition =
            abstractionDefinition
                .replace("[[name]]", fnName)
                .replace("[[body]]", body)
                .replace("[[return]]", retName)
                .replace("[[deletes]]", deletes)

        //capture values closed over by the function
        val closures =
            closedValue.fold("") { acc, i ->
                val closure =
                    if(i == depth) closureFromArgument
                    else closureFromClosedValue

                val updated =
                    closure
                        .replace("[[name]]", pathName)
                        .replace("[[umm]]", umm)
                        .replace("[[index]]", "${dbToArray[i]}")

                acc + updated
            }

        //generate the abstraction representation
        val call =
            callTemplate
                .replace("[[name]]", pathName)
                .replace("[[fnName]]", fnName)
                .replace("[[size]]", "$numClosures")
                .replace("[[closures]]", closures)

        val protos = prototypes + abstractionPrototype.replace("[[name]]", fnName)

        return CompilerStuff(call, protos, program + definition, "    del($pathName);\n")
    }

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int) =
        compileImpl(pathName, dbToArray, depth, abstractionCall, "->closedValues")

    fun directCall(pathName: String, dbToArray: Map<Int, Int>, depth: Int) =
        compileImpl(pathName, dbToArray, depth, directAbstractionCall, "ClosedValues")
}
data class Application(val l: Expression, val r: Expression, override val closedValue: Set<Int>): Expression() {
    override fun toString(): String = "($l $r)"

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): CompilerStuff {
        val nameR = pathName + "_r"
        val nameL = pathName + "_l"

        val (bodyR, prototypesR, programR, deletesR) = r.compile(nameR, dbToArray, depth)

        val application: String

        val (bodyL, prototypesL, programL, deletesL) =
            if(l is Abstraction && false) {
                application =
                    directApplicationDefinition
                        .replace("[[name]]", pathName)
                        .replace("[[nameR]]", nameR)
                        .replace("[[nameL]]", nameL)

                l.directCall(nameL, dbToArray, depth)
            }
            else {
                application =
                    applicationDefinition
                        .replace("[[name]]", pathName)
                        .replace("[[nameR]]", nameR)
                        .replace("[[nameL]]", nameL)

                l.compile(nameL, dbToArray, depth)
            }

        return CompilerStuff(bodyR + bodyL + application, prototypesR + prototypesL, programR + programL, deletesR + deletesL)
    }
}
data class Name(val index: Int): Expression() {
    override val closedValue = setOf(index)
    override fun toString(): String = "$index"

    override fun compile(pathName: String, dbToArray: Map<Int, Int>, depth: Int): CompilerStuff {
        val (value, dup) =
            if(index == depth) Pair(valueFromArgument, dupFromArgument)
            else Pair(valueFromClosedValue, dupFromClosedValue)

        val updated =
            value
                .replace("[[name]]", pathName)
                .replace("[[index]]", "${dbToArray[index]}") +
                    (if(pathName.endsWith("ret")) dup.replace("[[index]]", "${dbToArray[index]}") else "")

        return CompilerStuff(updated, "", "", "")
    }
}

fun main() {
    val p = tokenize("((fn x ((fn y x) (fn z z))) (fn w w))").map { it.parse() }

    val (body, prototypes, functions, deletes) = p[0].compile("f", mapOf(), -1)
    val program =
        mainDefinition
            .replace("[[body]]", body)
            .replace("[[functions]]", functions)
            .replace("[[prototypes]]", prototypes)
            .replace("[[deletes]]", deletes)

    println(program)
}