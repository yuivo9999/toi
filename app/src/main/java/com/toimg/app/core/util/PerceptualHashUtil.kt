package com.toimg.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.toimg.app.domain.model.ImageItem
import java.io.InputStream

enum class DuplicateType {
    EXACT_SHA256,       // 完全重复 (SHA-256 哈希值相同)
    VISUALLY_SIMILAR    // 视觉相似 (dHash 感知哈希汉明距离 <= 10)
}

data class DuplicateGroup(
    val id: String,
    val type: DuplicateType,
    val primaryImage: ImageItem,
    val duplicateImages: List<ImageItem>,
    val similarityDescription: String,
    val potentialSavingsBytes: Long
)

object PerceptualHashUtil {

    const val SIMILARITY_THRESHOLD = 10 // 汉明距离 <= 10 判定为相似图片

    /**
     * 根据 9x8 灰度亮度矩阵计算 64 位感知哈希值 (dHash - Difference Hash)
     * 每一行比较相邻像素 (col vs col + 1)，大于为 1，否则为 0。
     * 共 8 行 * 8 次比较 = 64 位，格式化为 16 位 16 进制字符串。
     */
    fun calculateDHashFromLuminance(luminance: Array<IntArray>): String {
        require(luminance.size >= 8 && luminance.all { it.size >= 9 }) {
            "Luminance matrix must be at least 8 rows by 9 columns"
        }

        var hash: Long = 0L
        var bitIndex = 0

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val left = luminance[row][col]
                val right = luminance[row][col + 1]
                if (left > right) {
                    hash = hash or (1L shl (63 - bitIndex))
                }
                bitIndex++
            }
        }

        return String.format("%016x", hash)
    }

    /**
     * 计算 Android Bitmap 的 dHash
     */
    fun calculateDHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val luminance = Array(8) { IntArray(9) }

        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // 经典亮度加权公式: 0.299*R + 0.587*G + 0.114*B
                luminance[y][x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        return calculateDHashFromLuminance(luminance)
    }

    /**
     * 从 Content Uri 解码小缩略图并计算 dHash
     */
    fun calculateDHashFromUri(context: Context, uri: Uri): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outWidth > 64 || options.outHeight > 64) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= 32 && (halfWidth / inSampleSize) >= 32) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            bitmap?.let {
                val hash = calculateDHash(it)
                it.recycle()
                hash
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 计算两个 16 进制 dHash 字符串之间的汉明距离 (Hamming Distance)
     * 范围 0..64，越小说明两张图片在视觉上越接近。
     */
    fun hammingDistance(hash1: String, hash2: String): Int {
        if (hash1.length != 16 || hash2.length != 16) {
            return 64
        }
        return try {
            val h1 = java.lang.Long.parseUnsignedLong(hash1, 16)
            val h2 = java.lang.Long.parseUnsignedLong(hash2, 16)
            java.lang.Long.bitCount(h1 xor h2)
        } catch (e: Exception) {
            64
        }
    }

    /**
     * 是否判定为视觉相似
     */
    fun isSimilar(hash1: String, hash2: String, threshold: Int = SIMILARITY_THRESHOLD): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }

    /**
     * 聚类分析图片列表，输出完全重复组 (SHA-256) 与视觉相似组 (dHash)
     */
    fun groupDuplicatesAndSimilar(
        images: List<ImageItem>,
        dHashes: Map<String, String> = emptyMap(),
        similarityThreshold: Int = SIMILARITY_THRESHOLD
    ): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val processedImageIds = mutableSetOf<String>()

        // 1. 基于 SHA-256 进行完全重复分组
        val sha256Groups = images
            .filter { !it.sha256.isNullOrBlank() }
            .groupBy { it.sha256!! }
            .filter { it.value.size > 1 }

        for ((sha256, dupList) in sha256Groups) {
            // 排序选出主图片 (优先保留最新修改、或体积最大的)
            val sorted = dupList.sortedWith(
                compareByDescending<ImageItem> { it.size }.thenByDescending { it.dateModified }
            )
            val primary = sorted.first()
            val duplicates = sorted.drop(1)
            val savings = duplicates.sumOf { it.size }

            groups.add(
                DuplicateGroup(
                    id = "sha256_${sha256.take(12)}",
                    type = DuplicateType.EXACT_SHA256,
                    primaryImage = primary,
                    duplicateImages = duplicates,
                    similarityDescription = "SHA-256 完全一致 (100% 精确副本)",
                    potentialSavingsBytes = savings
                )
            )
            dupList.forEach { processedImageIds.add(it.id) }
        }

        // 2. 基于 dHash 感知哈希进行视觉相似分组 (排除已完全重复的图片)
        val remainingImages = images.filter { !processedImageIds.contains(it.id) && dHashes.containsKey(it.id) }
        val visitedSimilarIds = mutableSetOf<String>()

        for (i in remainingImages.indices) {
            val imgA = remainingImages[i]
            if (visitedSimilarIds.contains(imgA.id)) continue

            val hashA = dHashes[imgA.id] ?: continue
            val similarCluster = mutableListOf<ImageItem>()

            for (j in (i + 1) until remainingImages.size) {
                val imgB = remainingImages[j]
                if (visitedSimilarIds.contains(imgB.id)) continue

                val hashB = dHashes[imgB.id] ?: continue
                val dist = hammingDistance(hashA, hashB)
                if (dist <= similarityThreshold) {
                    similarCluster.add(imgB)
                }
            }

            if (similarCluster.isNotEmpty()) {
                val allInCluster = listOf(imgA) + similarCluster
                allInCluster.forEach { visitedSimilarIds.add(it.id) }

                // 排序保留最优
                val sorted = allInCluster.sortedWith(
                    compareByDescending<ImageItem> { it.size }.thenByDescending { it.dateModified }
                )
                val primary = sorted.first()
                val duplicates = sorted.drop(1)
                val savings = duplicates.sumOf { it.size }

                val avgDist = duplicates.map { hammingDistance(hashA, dHashes[it.id] ?: "") }.average()

                groups.add(
                    DuplicateGroup(
                        id = "dhash_${primary.id}",
                        type = DuplicateType.VISUALLY_SIMILAR,
                        primaryImage = primary,
                        duplicateImages = duplicates,
                        similarityDescription = String.format("视觉相似 (平均汉明距离: %.1f)", avgDist),
                        potentialSavingsBytes = savings
                    )
                )
            }
        }

        return groups.sortedWith(
            compareBy<DuplicateGroup> { it.type.ordinal }.thenByDescending { it.potentialSavingsBytes }
        )
    }
}
