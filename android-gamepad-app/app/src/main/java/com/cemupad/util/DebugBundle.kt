package com.cemupad.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.FileProvider
import com.cemupad.config.DisplaySettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Assembles the remote-debugging bundle: app log file(s), a window screenshot,
 * and a device/codec report — zipped and sent through the system share sheet.
 */
data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val device: String,
    val board: String,
    val socModel: String,
    val sdkInt: Int,
    val androidRelease: String,
    val screenPx: String,
    val refreshHz: Float,
    val appVersion: String,
    val settingsSummary: String,
    val avcDecoders: List<String>
)

object DebugBundle {
    private const val TAG = "DebugBundle"

    fun collectDeviceReport(
        context: Context,
        settings: DisplaySettings
    ): DeviceReport {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        var version = "unknown"
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            version = "${info.versionName} ($code)"
        } catch (_: Exception) {
        }
        val soc = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL ?: "unknown"
            } else {
                Build.BOARD ?: "unknown"
            }
        } catch (_: Exception) {
            "unknown"
        }
        val refresh = try {
            context.display?.refreshRate ?: 0f
        } catch (_: Exception) {
            0f
        }
        return DeviceReport(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            device = Build.DEVICE ?: "unknown",
            board = Build.BOARD ?: "unknown",
            socModel = soc,
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            screenPx = "${metrics.widthPixels}x${metrics.heightPixels} @${metrics.densityDpi}dpi",
            refreshHz = refresh,
            appVersion = version,
            settingsSummary = "fit=${settings.fitMode} res=${settings.resolutionPreset} " +
                "bitrate=${settings.videoBitrateMbps}Mbps audio=${settings.audioEnabled} " +
                "mic=${settings.micEnabled}",
            avcDecoders = listAvcDecoders()
        )
    }

    fun listAvcDecoders(): List<String> {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    !info.isEncoder && info.supportedTypes.any {
                        it.equals("video/avc", ignoreCase = true)
                    }
                }
                .map { info ->
                    val hw = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (info.isHardwareAccelerated) "hw" else "sw"
                        } else {
                            "?"
                        }
                    } catch (_: Exception) {
                        "?"
                    }
                    "${info.name} [$hw]"
                }
        } catch (e: Exception) {
            listOf("decoder query failed: ${e.message}")
        }
    }

    fun formatReport(report: DeviceReport): String {
        return buildString {
            appendLine("CemuPad debug report")
            appendLine("manufacturer=${report.manufacturer}")
            appendLine("model=${report.model}")
            appendLine("device=${report.device}")
            appendLine("board=${report.board}")
            appendLine("soc=${report.socModel}")
            appendLine("sdk=${report.sdkInt} release=${report.androidRelease}")
            appendLine("screen=${report.screenPx} refresh=${report.refreshHz}Hz")
            appendLine("app=${report.appVersion}")
            appendLine("settings: ${report.settingsSummary}")
            appendLine("avc_decoders:")
            for (decoder in report.avcDecoders) {
                appendLine("  - $decoder")
            }
        }
    }

    fun screenshotPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun buildZip(
        output: File,
        reportText: String,
        logText: String,
        screenshotPng: ByteArray?
    ) {
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("device-info.txt"))
            zip.write(reportText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("app-log.txt"))
            zip.write(logText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (screenshotPng != null) {
                zip.putNextEntry(ZipEntry("screenshot.png"))
                zip.write(screenshotPng)
                zip.closeEntry()
            }
        }
    }

    fun shareZip(context: Context, zip: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                zip
            )
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_SUBJECT, "CemuPad debug bundle $stamp")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share debug bundle"))
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to share debug bundle: ${e.message}")
            throw e
        }
    }
}
