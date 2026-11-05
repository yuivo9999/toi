package com.toimg.app.core.util

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

object Sha256Util {
    fun calculateSha256(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val hashBytes = digest.digest()
                val hexString = StringBuilder()
                for (b in hashBytes) {
                    val hex = Integer.toHexString(0xff and b.toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }
                hexString.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
