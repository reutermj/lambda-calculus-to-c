package lambda

import java.io.File

fun loadTemplate(template: String)  = File(template).readText()

val mainDefinition = loadTemplate(".\\resources\\Main.c.template")

val abstractionDefinition = loadTemplate(".\\resources\\AbstractionDefinition.c.template")
val abstractionCall = loadTemplate(".\\resources\\AbstractionCall.c.template")
val abstractionPrototype = "function* [[name]](function*, function**);\n"

val applicationDefinition = "    function* [[name]] = [[nameL]]->ptr([[nameR]], [[nameL]]->closedValues);\n"

val closureFromClosedValue = "    [[name]]->closedValues[[[index]]] = c[[[index]]];\n"
val closureFromArgument = "    [[name]]->closedValues[[[index]]] = f;\n"

val valueFromClosedValue = "    function* [[name]] = c[[[index]]];\n"
val valueFromArgument = "    function* [[name]] = f;\n"