package com.mazesys.zerobite.testing

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.Strings
import com.mazesys.zerobite.ui.text.UiText
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The English string resources (res/values/strings.xml) for plain JVM tests, so view model
 * tests can check the exact English copy without starting Robolectric.
 */
object EnglishStrings : Strings {
    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/values/strings.xml"))
        .documentElement

    private val strings: Map<String, String> = document.elements("string").associate { it.getAttribute("name") to unescape(it.textContent) }

    private val plurals: Map<String, Map<String, String>> = document.elements("plurals").associate { plural ->
        plural.getAttribute("name") to plural.elements("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
    }

    override fun get(@StringRes id: Int): String = strings.getValue(name(R.string::class.java, id))

    fun format(@StringRes id: Int, vararg args: Any): String = String.format(Locale.UK, get(id), *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val forms = plurals.getValue(name(R.plurals::class.java, id))
        return String.format(Locale.UK, forms.getValue(if (count == 1) "one" else "other"), *args)
    }

    private fun name(type: Class<*>, id: Int): String = type.fields.first { it.getInt(null) == id }.name

    private fun Element.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun unescape(raw: String): String = raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
}

/** The English text of a view model's [UiText]. */
fun UiText.english(): String = when (this) {
    is UiText.Res -> EnglishStrings.format(id, *args.toTypedArray())
    is UiText.Plural -> EnglishStrings.plural(id, count, *args.toTypedArray())
}

/** The English text of a string resource. */
fun english(@StringRes id: Int, vararg args: Any): String = EnglishStrings.format(id, *args)
