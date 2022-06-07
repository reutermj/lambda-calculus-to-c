package lambda

import java.io.File

val baseDirectory = File(".").absoluteFile.parent

fun loadTemplate(template: String) = File("$baseDirectory${File.separator}resources${File.separator}$template").readText()

val mainDefinition = loadTemplate("Main.c.template")

val abstractionDefinition = loadTemplate("AbstractionDefinition.c.template")
val abstractionCall = loadTemplate("AbstractionCall.c.template")
val directAbstractionCall = loadTemplate("DirectAbstractionCall.c.template")
val abstractionPrototype = "function* [[name]](function*, function**);\n"

val applicationDefinition = "    function* [[name]] = [[nameL]]->ptr([[nameR]], [[nameL]]->closedValues);\n"
val directApplicationDefinition = "    function* [[name]] = [[nameL]]_f([[nameR]], [[nameL]]ClosedValues);\n"

val closureFromClosedValue = "    [[name]]->closedValues[[[index]]] = c[[[index]]];\n"
val closureFromArgument = "    [[name]]->closedValues[[[index]]] = f;\n"

val valueFromClosedValue = "    function* [[name]] = c[[[index]]];\n"
val valueFromArgument = "    function* [[name]] = f;\n"