package com.pansare.sadan.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** PDF sharing helpers. Files are copied into the FileProvider-approved receipts cache first. */
object ShareUtils {

    fun sharePdf(context: Context, file: File, title: String = "Rent Receipt") {
        val uri = getShareableUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt"))
    }

    /** Opens WhatsApp/WhatsApp Business directly when installed, otherwise falls back to Sharesheet. */
    fun sharePdfToWhatsApp(
        context: Context,
        file: File,
        title: String = "Rent Receipt",
        mobileNumber: String = ""
    ) {
        val uri = getShareableUri(context, file)
        val digits = mobileNumber.filter(Char::isDigit).let {
            if (it.length == 10) "91$it" else it
        }

        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (packageName in packages) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                setPackage(packageName)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                if (digits.isNotBlank()) putExtra("jid", "$digits@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next WhatsApp package, then use the normal Android Sharesheet.
            }
        }
        sharePdf(context, file, title)
    }

    fun shareText(context: Context, text: String, subject: String = "") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun getShareableUri(context: Context, source: File): Uri {
        require(source.exists()) { "Receipt file was not created." }
        val receiptDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val safeFile = if (source.parentFile?.canonicalFile == receiptDir.canonicalFile) {
            source
        } else {
            File(receiptDir, source.name).also { target -> source.copyTo(target, overwrite = true) }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            safeFile
        )
    }
}
