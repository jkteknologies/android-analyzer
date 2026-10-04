package com.jkteknologies.androidanalyzer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Device-free JVM unit tests (FR-004, data-model S-4).
 *
 * No Android framework classes are touched (the JVM `android.*` stubs throw by
 * design), so UI rendering is not asserted here. What IS verifiable on the JVM
 * is the resource contract behind the placeholder screen: the exact user-visible
 * text (U-1) comes from the sole string resource `app_name` (FR-002), which this
 * test pins by parsing the resource XML directly from the source tree.
 */
class PlaceholderScreenTest {

    @Test
    fun appNameResourceContainsExactPlaceholderText() {
        assertEquals(
            "app_name must hold the exact placeholder text (FR-002, U-1)",
            "Android Analyzer",
            stringResources()["app_name"],
        )
    }

    @Test
    fun appNameIsTheSoleStringResource() {
        assertEquals(
            "The skeleton defines exactly one string resource (T008)",
            setOf("app_name"),
            stringResources().keys,
        )
    }

    /**
     * Parses `src/main/res/values/strings.xml`. Walks up from the test working
     * directory so the test works regardless of the launcher's CWD.
     */
    private fun stringResources(): Map<String, String> {
        val stringsXml = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "src/main/res/values/strings.xml") }
            .firstOrNull { it.isFile }
            ?: error("src/main/res/values/strings.xml not found under any parent of the working directory")

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXml)
        val nodes = doc.getElementsByTagName("string")
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = requireNotNull(node.attributes.getNamedItem("name")?.nodeValue) {
                    "string resource at index $i has no name attribute"
                }
                put(name, node.textContent.trim())
            }
        }
    }
}
