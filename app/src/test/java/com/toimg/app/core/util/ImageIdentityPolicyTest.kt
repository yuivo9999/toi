package com.toimg.app.core.util

import com.toimg.app.data.local.entity.ImageEntity
import org.junit.Assert.*
import org.junit.Test

class ImageIdentityPolicyTest {
    private fun image(id: String, name: String = "photo.jpg", size: Long = 100,
                      modified: Long = 1234, mime: String = "image/jpeg") = ImageEntity(
        id = id, uriString = "content://test/$id", name = name, path = "", size = size,
        dateModified = modified, mimeType = mime, folderName = "test"
    )

    @Test fun onlyExactMetadataIsCandidateBeforeHashVerification() {
        val incoming = image("incoming")
        assertTrue(ImageIdentityPolicy.isMetadataCandidate(incoming, image("old")))
        assertFalse(ImageIdentityPolicy.isMetadataCandidate(incoming, image("old", modified = 1235)))
        assertFalse(ImageIdentityPolicy.isMetadataCandidate(incoming, image("old", size = 101)))
        assertFalse(ImageIdentityPolicy.isMetadataCandidate(incoming, image("old", name = "other.jpg")))
        assertFalse(ImageIdentityPolicy.isMetadataCandidate(incoming, image("old", mime = "image/png")))
    }

    @Test fun ambiguousCandidatesAreNeverAutoMerged() {
        val one = image("one")
        assertSame(one, ImageIdentityPolicy.uniqueCandidate(listOf(one)))
        assertNull(ImageIdentityPolicy.uniqueCandidate(emptyList()))
        assertNull(ImageIdentityPolicy.uniqueCandidate(listOf(one, image("two"))))
    }

    @Test fun moreThanTwentyMetadataMatchesRemainAmbiguous() {
        // 防止 DAO 通过 LIMIT 20 截断候选后，将实际存在的第 21 个候选隐藏。
        val candidates = (1..21).map { image("candidate-$it") }
        assertNull(ImageIdentityPolicy.uniqueCandidate(candidates))
    }
}
