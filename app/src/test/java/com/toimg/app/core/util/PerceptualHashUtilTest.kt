package com.toimg.app.core.util

import com.toimg.app.domain.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashUtilTest {

    @Test
    fun testDHashCalculationIdentical() {
        val lum1 = Array(8) { row ->
            IntArray(9) { col -> row * 10 + col * 2 }
        }
        val lum2 = Array(8) { row ->
            IntArray(9) { col -> row * 10 + col * 2 }
        }

        val hash1 = PerceptualHashUtil.calculateDHashFromLuminance(lum1)
        val hash2 = PerceptualHashUtil.calculateDHashFromLuminance(lum2)

        assertEquals("Identical luminance matrices must produce identical dHash", hash1, hash2)
        assertEquals(0, PerceptualHashUtil.hammingDistance(hash1, hash2))
        assertTrue(PerceptualHashUtil.isSimilar(hash1, hash2, 0))
    }

    @Test
    fun testHammingDistanceDifference() {
        // All zeros vs all ones
        val hashZero = "0000000000000000"
        val hashOne = "ffffffffffffffff"
        assertEquals(64, PerceptualHashUtil.hammingDistance(hashZero, hashOne))

        // 1 bit flip
        val hashDiff1 = "0000000000000001"
        assertEquals(1, PerceptualHashUtil.hammingDistance(hashZero, hashDiff1))
        assertTrue(PerceptualHashUtil.isSimilar(hashZero, hashDiff1, 10))

        // 5 bit difference
        val hashDiff5 = "000000000000001f"
        assertEquals(5, PerceptualHashUtil.hammingDistance(hashZero, hashDiff5))
        assertTrue(PerceptualHashUtil.isSimilar(hashZero, hashDiff5, 10))

        // 12 bit difference (above 10 threshold)
        val hashDiff12 = "0000000000000fff"
        assertEquals(12, PerceptualHashUtil.hammingDistance(hashZero, hashDiff12))
        assertFalse(PerceptualHashUtil.isSimilar(hashZero, hashDiff12, 10))
    }

    @Test
    fun testGroupDuplicatesBySha256() {
        val img1 = ImageItem(
            id = "img1",
            uri = android.net.TestUri("content://media/1"),
            name = "pic1.jpg",
            path = "/sdcard/pic1.jpg",
            size = 2000L,
            dateModified = 1000L,
            mimeType = "image/jpeg",
            folderName = "DCIM",
            sha256 = "hash_abc_123"
        )
        val img2 = ImageItem(
            id = "img2",
            uri = android.net.TestUri("content://media/2"),
            name = "pic1_copy.jpg",
            path = "/sdcard/pic1_copy.jpg",
            size = 2000L,
            dateModified = 2000L,
            mimeType = "image/jpeg",
            folderName = "Download",
            sha256 = "hash_abc_123"
        )
        val img3 = ImageItem(
            id = "img3",
            uri = android.net.TestUri("content://media/3"),
            name = "other.jpg",
            path = "/sdcard/other.jpg",
            size = 1500L,
            dateModified = 3000L,
            mimeType = "image/jpeg",
            folderName = "DCIM",
            sha256 = "hash_xyz_789"
        )

        val groups = PerceptualHashUtil.groupDuplicatesAndSimilar(listOf(img1, img2, img3))
        assertEquals(1, groups.size)
        assertEquals(DuplicateType.EXACT_SHA256, groups[0].type)
        assertEquals("img2", groups[0].primaryImage.id) // Newest date modified is primary
        assertEquals(1, groups[0].duplicateImages.size)
        assertEquals("img1", groups[0].duplicateImages[0].id)
        assertEquals(2000L, groups[0].potentialSavingsBytes)
    }

    @Test
    fun testGroupVisualSimilarityByDHash() {
        val imgA = ImageItem(
            id = "imgA",
            uri = android.net.TestUri("content://media/A"),
            name = "burst_1.jpg",
            path = "/sdcard/burst_1.jpg",
            size = 5000L,
            dateModified = 1000L,
            mimeType = "image/jpeg",
            folderName = "DCIM",
            sha256 = "unique_sha_A"
        )
        val imgB = ImageItem(
            id = "imgB",
            uri = android.net.TestUri("content://media/B"),
            name = "burst_2.jpg",
            path = "/sdcard/burst_2.jpg",
            size = 4800L,
            dateModified = 1001L,
            mimeType = "image/jpeg",
            folderName = "DCIM",
            sha256 = "unique_sha_B"
        )

        // dHashes differ by 2 bits (dist = 2 <= 10)
        val dHashes = mapOf(
            "imgA" to "1111222233334440",
            "imgB" to "1111222233334443"
        )

        val groups = PerceptualHashUtil.groupDuplicatesAndSimilar(
            images = listOf(imgA, imgB),
            dHashes = dHashes,
            similarityThreshold = 10
        )

        assertEquals(1, groups.size)
        assertEquals(DuplicateType.VISUALLY_SIMILAR, groups[0].type)
        assertEquals("imgA", groups[0].primaryImage.id) // Larger size
        assertEquals("imgB", groups[0].duplicateImages[0].id)
        assertEquals(4800L, groups[0].potentialSavingsBytes)
    }
}
