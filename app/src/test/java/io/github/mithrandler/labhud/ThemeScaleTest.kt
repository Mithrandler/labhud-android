package io.github.mithrandler.labhud

import java.io.File
import org.junit.Test
import org.junit.Assert.assertTrue

/** The look lives in design/tokens.json (Tokens.kt is generated): a screen takes its type and gaps from LabScale. */
class ThemeScaleTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" && it.name != "Tokens.kt" }.toList()

    private fun count(re: Regex) = sources.sumOf { f -> re.findAll(f.readText()).count() }

    /** What the scale has no token for, on purpose: a zero, letter spacing, a text-fit step, a widget's sizes. */
    private val allowed = Regex("""(?<![\w.])0\.dp|letterSpacing = [0-9.]+\.sp|stepSize = [0-9.]+\.sp|DpSize\([^)]*\)|LocalSize\.current\.height >= [0-9.]+\.dp""")

    @Test fun fontSizesComeFromTheScale() {
        val n = count(Regex("""fontSize = [0-9.]+\.sp"""))
        assertTrue("$n fontSize literals: use LabScale.text*, or add a token to design/tokens.json", n <= 0)
    }

    @Test fun looseDpAndSpDoNotGrow() {
        val n = sources.sumOf { f -> Regex("""(?<![\w.])[0-9.]+\.(dp|sp)\b""").findAll(allowed.replace(f.readText(), "")).count() }
        assertTrue("$n dp/sp literals: use LabScale, or add a token to design/tokens.json", n <= 0)
    }
}
