/*
Copyright 2022 - 2023 Stɑrry Shivɑm

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package org.chicha.tbook.epub

import android.graphics.BitmapFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.File
import java.util.zip.ZipEntry

class EpubXMLFileParser(
    fileAbsolutePath: String,
    val data: ByteArray,
    private val zipFile: Map<String, Pair<ZipEntry, ByteArray>>
) {
    data class Output(val title: String?, val body: String)

    private val fileParentFolder: File = File(fileAbsolutePath).parentFile ?: File("")

    // Make all local references absolute to the root of the epub for consistent references
    private val absBasePath: String = File("").canonicalPath
    fun parse(): Output {
        val body = Jsoup.parse(data.inputStream(), "UTF-8", "").body()
        val title = body.selectFirst("h1, h2, h3, h4, h5, h6")?.text()
        body.selectFirst("h1, h2, h3, h4, h5, h6")?.remove()
        // TODO: Add support for images, for now just remove them.
        body.getElementsByTag("img").remove()
        return Output(
            title = title, body = getNodeStructuredText(body)
        )
    }

    // Rewrites the image node to xml for the next stage.
    private fun declareImgEntry(node: org.jsoup.nodes.Node): String {
        val relPathEncoded = (node as? org.jsoup.nodes.Element)?.attr("src") ?: return ""
        val absPath = File(
            fileParentFolder,
            relPathEncoded.decodedURL
        ).canonicalPath.removePrefix(absBasePath).replace("\\", "/").removePrefix("/")
        // Use run catching so it can be run locally without crash
        val bitmap = zipFile[absPath]?.second?.runCatching {
            BitmapFactory.decodeByteArray(this, 0, this.size)
        }?.getOrNull()
        val text = BookTextMapper.ImgEntry(path = absPath,
            yrel = bitmap?.let { it.height.toFloat() / it.width.toFloat() } ?: 1.45f).toXMLString()
        return "\n\n$text\n\n"
    }

    private fun getPTraverse(node: org.jsoup.nodes.Node): String {
        fun innerTraverse(node: org.jsoup.nodes.Node): String =
            node.childNodes().joinToString("") { child ->
                when {
                    child.nodeName() == "br" -> "\n"
                    child.nodeName() == "img" -> declareImgEntry(child)
                    child is TextNode -> child.text()
                    else -> innerTraverse(child)
                }
            }

        val paragraph = innerTraverse(node).trim()
        return if (paragraph.isEmpty()) "" else innerTraverse(node).trim() + "\n\n"
    }

    private fun getNodeTextTraverse(node: org.jsoup.nodes.Node): String {
        val children = node.childNodes()
        if (children.isEmpty()) return ""
        return children.joinToString("") { child ->
            when {
                child.nodeName() == "p" -> getPTraverse(child)
                child.nodeName() == "br" -> "\n"
                child.nodeName() == "hr" -> "\n\n"
                child.nodeName() == "img" -> declareImgEntry(child)
                child is TextNode -> {
                    val text = child.text().trim()
                    if (text.isEmpty()) "" else text + "\n\n"
                }
                else -> getNodeTextTraverse(child)
            }
        }
    }

    private fun getNodeStructuredText(node: org.jsoup.nodes.Node): String {
        val children = node.childNodes()
        if (children.isEmpty()) return ""
        return children.joinToString("") { child ->
            when {
                child.nodeName() == "p" -> getPTraverse(child)
                child.nodeName() == "br" -> "\n"
                child.nodeName() == "hr" -> "\n\n"
                child.nodeName() == "img" -> declareImgEntry(child)
                child is TextNode -> child.text().trim()
                else -> getNodeTextTraverse(child)
            }
        }
    }
}