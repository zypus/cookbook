package com.zypus

import com.amazonaws.services.s3.model.CannedAccessControlList
import io.ktor.util.escapeHTML
import java.io.File

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-09
 */
object ImageManager {

    enum class ImageSize {
        SMALL,
        MEDIUM,
        LARGE
    }

    val smallImageCache: MutableMap<String, String> = hashMapOf()
    val mediumImageCache: MutableMap<String, String> = hashMapOf()
    val largeImageCache: MutableMap<String, String> = hashMapOf()

    fun getImageUrlFor(term: String, size: ImageSize = ImageSize.MEDIUM, lang: String = "de"): String? {

        println("Requested: $term in $size")

        val imageCache = when (size) {
            ImageSize.SMALL -> smallImageCache
            ImageSize.MEDIUM -> mediumImageCache
            ImageSize.LARGE -> largeImageCache
        }

        if (term in imageCache) {
            println(imageCache[term])
            return imageCache[term]!!
        }

        val imageFromS3 = getImageFromS3(term, size)
        println(imageFromS3)
        if (imageFromS3 != null) {
            imageCache[term] = imageFromS3
            return imageFromS3
        }

        val pixabayResult = getImageFromPixabay(term, lang)

        if (pixabayResult != null) {
            println(pixabayResult)

            val s3Urls = hashMapOf(
                ImageSize.SMALL to pixabayResult.smallUrl,
                ImageSize.MEDIUM to pixabayResult.mediumUrl,
                ImageSize.LARGE to pixabayResult.largeUrl
            ).filter {
                getImageFromS3(term, it.key).isNullOrBlank()
            }.map { (size, url) ->
                val tempFile = createTempFile()

                for (chunk in khttp.get(url, stream = true).contentIterator(2048)) {
                    tempFile.appendBytes(chunk)
                }

                val s3Url = s3 {
                    val key = "placeholder/$term/${File(url).name}"
                    tempFile.upload(
                        key,
                        acl = CannedAccessControlList.PublicRead
                    )
                    url(key).toExternalForm()
                }

                when (size) {
                    ImageSize.SMALL -> smallImageCache[term] = s3Url
                    ImageSize.MEDIUM -> mediumImageCache[term] = s3Url
                    ImageSize.LARGE -> largeImageCache[term] = s3Url
                }

                size to s3Url
            }.toMap()

            return s3Urls[size]
        } else {
            return null
        }


    }

    fun getImageFromS3(term: String, size: ImageSize): String? {
        val url = s3 {
            val key = objects.objectSummaries.asSequence()
                .map {
                    it.key
                }
                .filter {
                    it.startsWith("placeholder/${term.escapeHTML()}/") && when (size) {
                        ImageSize.SMALL -> it.contains("_150.")
                        ImageSize.MEDIUM -> it.contains("_640.")
                        ImageSize.LARGE -> it.contains("_1280.")
                    }
                }.firstOrNull()
            key?.let { url(it) }
        }
        return url?.toExternalForm()
    }

    data class PixabayResult(val smallUrl: String, val mediumUrl: String, val largeUrl: String)

    fun getImageFromPixabay(term: String, lang: String = "de"): PixabayResult? {
        val pixabayKey = System.getenv("PIXABAY_KEY")

        val result = khttp.get(
            "https://pixabay.com/api/",
            params = mapOf(
                "key" to pixabayKey,
                "q" to term.replace("[^a-zA-zäöü]".toRegex(), "+"),
                "lang" to lang,
                "image_type" to "photo",
                "safesearch" to "true",
                "per_page" to "3"
            )
        )

        val jsonArray = result.jsonObject
            .getJSONArray("hits")
        return if (jsonArray.length() > 0) {
            val jsonObject = jsonArray
                .getJSONObject(0)
            PixabayResult(
                smallUrl = jsonObject.getString("previewURL"),
                mediumUrl = jsonObject.getString("webformatURL"),
                largeUrl = jsonObject.getString("largeImageURL")
            )
        } else {
            null
        }

    }

}