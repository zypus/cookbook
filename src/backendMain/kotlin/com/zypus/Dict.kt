package com.zypus

import com.beust.klaxon.internal.firstNotNullResult
import kotlinx.io.streams.readPacketExact
import kotlinx.io.streams.readerUTF8
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-22
 */

val b64List = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

fun String.b64Decode(): Long {
    if (this.isEmpty()) {
        return 0
    }
    val (retval, _) = this.asSequence().toList().foldRight(0L to 0) { c, (retval, shiftval) ->
        (retval or (b64List.indexOf(c).toLong() shl shiftval)) to shiftval + 6
    }

    return retval
}

object Dict {

    val indexEntries: MutableMap<String, MutableList<Pair<Long, Long>>> = hashMapOf()

    val indexFile = File("deu-eng.index")

    init {
        indexFile.useLines { lines ->
            lines.forEach {
                line ->
                val (key, start, size) = line.split("\t")
                if (key !in indexEntries) {
                    indexEntries[key] = arrayListOf()
                }
                indexEntries[key]!!.add(start.b64Decode() to size.b64Decode())
            }
        }
    }

    fun definitions(word: String): List<String> {
        return indexEntries[word.toLowerCase()]?.map {
                (start, size) ->
            val dictStream = GZIPInputStream(File("deu-eng.dict.dz").inputStream())
            dictStream.skip(start)
            val readPacket = dictStream.readPacketExact(size)
            readPacket.readerUTF8().readText().lines()[1]
        } ?: arrayListOf()
    }
}

fun robustDefintion(word: String): String? {
    return (word.length downTo 3).firstNotNullResult {
        prefixLength ->
        Dict.definitions(word.take(prefixLength)).firstOrNull()
    }
}

fun main(args: Array<String>) {
    println(Dict.definitions("essen"))
}