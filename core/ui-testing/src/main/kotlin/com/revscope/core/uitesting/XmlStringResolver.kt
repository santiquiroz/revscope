package com.revscope.core.uitesting

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class XmlStringResolver(
    resDirs: List<File>,
    override val locale: Locale,
    rClasses: List<Class<*>> = emptyList(),
) : StringResolver {

    private val strings: Map<String, String>
    private val plurals: Map<String, Map<String, String>>
    private val stringIds: Map<Int, String>
    private val pluralIds: Map<Int, String>

    init {
        val stringMap = linkedMapOf<String, String>()
        val pluralMap = linkedMapOf<String, MutableMap<String, String>>()
        for (dir in resDirs) {
            loadDir(File(dir, "values"), stringMap, pluralMap)
        }
        if (locale.language != "en") {
            for (dir in resDirs) {
                loadDir(File(dir, "values-${locale.language}"), stringMap, pluralMap)
            }
        }
        strings = stringMap
        plurals = pluralMap
        stringIds = indexResourceIds(rClasses, "string")
        pluralIds = indexResourceIds(rClasses, "plurals")
    }

    fun get(name: String, vararg args: Any): String {
        val text = strings[name]
            ?: throw IllegalArgumentException("Unknown string name: $name")
        return format(text, args)
    }

    fun plural(name: String, count: Int, vararg args: Any): String {
        val forms = plurals[name]
            ?: throw IllegalArgumentException("Unknown plurals name: $name")
        val quantity = if (count == 1) "one" else "other"
        val text = forms[quantity] ?: forms["other"]
            ?: throw IllegalArgumentException("Unknown plurals name: $name")
        return format(text, args)
    }

    override fun get(@StringRes id: Int, vararg args: Any): String {
        val name = stringIds[id]
            ?: throw IllegalArgumentException("Unknown string id: $id")
        return get(name, *args)
    }

    override fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val name = pluralIds[id]
            ?: throw IllegalArgumentException("Unknown plurals id: $id")
        return plural(name, count, *args)
    }

    private fun format(text: String, args: Array<out Any>): String =
        if (args.isEmpty()) text else String.format(locale, text, *args)

    companion object {
        fun es(
            vararg rClasses: Class<*>,
            resDirs: List<String> = listOf("src/main/res"),
        ): XmlStringResolver =
            XmlStringResolver(resDirs.map(::File), Locale("es"), rClasses.toList())

        fun en(
            vararg rClasses: Class<*>,
            resDirs: List<String> = listOf("src/main/res"),
        ): XmlStringResolver =
            XmlStringResolver(resDirs.map(::File), Locale.US, rClasses.toList())
    }
}

private fun loadDir(
    valuesDir: File,
    strings: MutableMap<String, String>,
    plurals: MutableMap<String, MutableMap<String, String>>,
) {
    if (!valuesDir.isDirectory) return
    val files = valuesDir.listFiles { f ->
        f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml")
    } ?: return
    for (file in files.sortedBy { it.name }) {
        parseStringsXml(file, strings, plurals)
    }
}

private fun parseStringsXml(
    file: File,
    strings: MutableMap<String, String>,
    plurals: MutableMap<String, MutableMap<String, String>>,
) {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val root = doc.documentElement ?: return
    val children = root.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node !is Element) continue
        when (node.tagName) {
            "string" -> {
                val name = node.getAttribute("name")
                if (name.isNotEmpty()) strings[name] = unescapeAndroid(node.textContent.orEmpty())
            }
            "plurals" -> {
                val name = node.getAttribute("name")
                if (name.isEmpty()) continue
                val forms = plurals.getOrPut(name) { linkedMapOf() }
                val items = node.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val item = items.item(j) as Element
                    val quantity = item.getAttribute("quantity")
                    if (quantity == "one" || quantity == "other") {
                        forms[quantity] = unescapeAndroid(item.textContent.orEmpty())
                    }
                }
            }
        }
    }
}

private fun unescapeAndroid(raw: String): String {
    var text = raw
    if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
        text = text.substring(1, text.length - 1)
    }
    return text
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\@", "@")
        .replace("\\?", "?")
}

private fun indexResourceIds(rClasses: List<Class<*>>, nestedName: String): Map<Int, String> {
    val result = linkedMapOf<Int, String>()
    for (rClass in rClasses) {
        val nested = rClass.declaredClasses.firstOrNull { it.simpleName == nestedName } ?: continue
        for (field in nested.declaredFields) {
            if (field.type != Int::class.javaPrimitiveType) continue
            field.isAccessible = true
            result[field.getInt(null)] = field.name
        }
    }
    return result
}
