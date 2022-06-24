import org.simpleframework.xml.Serializer
import org.simpleframework.xml.core.Persister
import xmlUnit.XMLResource
import xmlUnit.XMLString
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.StringWriter
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private const val ROLLBACK_FILE_TAIL = ".rollback"

fun main(args: Array<String>) {

    val serializer = Persister()

    while (true) {
        val defaultData = getStringsMap(serializer, "Please enter default strings path: ")
        val oldTargetData = getStringsMap(serializer, "Please enter old target strings path: ")
        val newTargetData = getStringsMap(serializer, "Please enter new target strings path: ")
        println("Dose the tool enable length checking? (Y/N)")
        val isLengthChecking = readLine()?.let { it.lowercase() == "y" } ?: false

        val resultMap = HashMap<String, Result>()

        listOf(defaultData, oldTargetData, newTargetData).forEach { data ->
            data.map.forEach { (key, strOb) ->
                if (strOb.isChecked) {
                    return@forEach
                }
                val defaultOb = defaultData.map[key]
                val oldTargetOb = oldTargetData.map[key]
                val newTargetOb = newTargetData.map[key]
                defaultOb?.isChecked = true
                oldTargetOb?.isChecked = true
                newTargetOb?.isChecked = true

                if (newTargetOb == null || defaultOb == null) {
                    return@forEach
                }

                if (!isLengthChecking && (newTargetOb.text != oldTargetOb?.text && newTargetOb.text != defaultOb?.text)) {
                    resultMap[key] = Result(key, defaultOb.text, oldTargetOb?.text, newTargetOb?.text)
                    return@forEach
                }

                if (oldTargetOb != null) {
                    if ((newTargetOb.textLength() > oldTargetOb.textLength()) && (newTargetOb.textLength() > defaultOb.textLength())) {
                        resultMap[key] = Result(key, defaultOb.text, oldTargetOb.text, newTargetOb.text)
                        return@forEach
                    }
                } else if (newTargetOb.textLength() > defaultOb.textLength()) {
                    resultMap[key] = Result(key, defaultOb.text, oldTargetOb?.text, newTargetOb.text)
                }
            }
        }
        val resultList = resultMap.values.toList().sortedBy { it.key }
        println()
        resultList.forEach {
            println("key: ${it.key}")
            println("default   : ${it.defaultString}")
            println("old string: ${it.oldString}")
            println("new string: ${it.newString}")
            println("---------------------------------")
        }
        println("\nEnd of Checking\nThere are ${resultList.size} changes\n")

        println("\nRollback these strings? (Y/N)")
        val isRollbackList = readLine()?.let { it.lowercase() == "y" } ?: false
        if (!isRollbackList) {
            continue
        }

        println("Please enter strings keys in these changes that you \"NOT\" want to rollback: (enter q to stop)")
        val notRollbackList = getNotRollbackList()

        try {
            val rollbackFile = File("${newTargetData.filePath}$ROLLBACK_FILE_TAIL")
            BufferedReader(FileReader(newTargetData.filePath)).use { bufferReader ->
                rollbackFile.bufferedWriter().use { bufferWriter ->
                    var line: String?
                    while (bufferReader.readLine().also { line = it } != null) {

                        var xmlString: XMLString? = null
                        try {
                            xmlString = serializer.read(XMLString::class.java, line)
                        } catch (e: Exception) {
                            bufferWriter.write(line)
                            bufferWriter.newLine()
                            continue
                        }
                        val result = resultMap[xmlString.id!!]

                        if (result == null || notRollbackList.contains(xmlString.id!!)) {
                            bufferWriter.write(line)
                            bufferWriter.newLine()
                            continue
                        }
                        val stringWriter = StringWriter()

                        if (result.oldString != null) {
                            serializer.write(XMLString(xmlString.id, result.oldString), stringWriter)
                        } else if (result.defaultString != null) {
                            continue
                        } else {
                            throw IllegalArgumentException()
                        }

                        bufferWriter.write("    ${stringWriter.buffer}")
                        bufferWriter.newLine()
                    }
                }
            }
        } catch (e: Exception) {
            println("write file error!\n\n")
            continue
        }
        println("write file success!\n\n")
    }
}

private fun getNotRollbackList(): ArrayList<String> {
    val list = ArrayList<String>()
    var line: String? = null
    while (readLine()?.also { line = it }?.lowercase() != "q") {
        line?.apply {
            list.add(this)
        }
    }
    return list
}

private fun getStringsMap(serializer: Serializer, title: String): XMLData {
    val map = HashMap<String, StringObject>()
    val pair = getXMLResource(serializer, title)
    pair.second.entriesList?.forEach { xmlString ->
        xmlString.id?.apply {
            map[this] = StringObject(xmlString.text ?: "")
        }
    }
    return XMLData(pair.first, map)
}

private fun getXMLResource(serializer: Serializer, title: String): Pair<String, XMLResource> {
    while (true) {
        println(title)
        val filePath = readLine()
        if (filePath.isNullOrBlank()) {
            println("ERROR! Incorrect file path!")
            continue
        }
        val file = File(filePath)
        if (!file.exists()) {
            println("ERROR! File not existed!")
            continue
        }
        try {
            return Pair(filePath, serializer.read(XMLResource::class.java, file.inputStream()))
        } catch (e: Exception) {
            println("ERROR! Read file failed! $e")
        }
    }
}

private fun findVarList(s: String?): List<Int> {
    val list = ArrayList<Int>()
    if (s == null)
        return list
    var index = 0
    while (index >= 0) {
        index = s.indexOf("%", index)
        if (index < 0 || index + 1 >= s.length) {
            return list
        }
        // %% just means %. skip it
        if (s[index + 1] == '%') {
            index += 2
            continue
        }
        val result = getVar(s, index + 1)
        result.first?.apply {
            if (!list.contains(this)) {
                list.add(this)
            }
        }
        index = result.second + 1
    }
    return list
}

private fun getVar(s: String, index: Int): Pair<Int?, Int> { // return var number and end index
    for (i in index until s.length) {
        val c = s[i]
        if (c.isDigit()) {
            continue
        }
        //end of num must be '$' and the sub string length must be more than 0
        if (c != '$' || index == i) {
            return Pair(null, i)
        }
        return Pair(s.substring(index, i).toInt(), i)
    }
    return Pair(null, s.length - 1)
}