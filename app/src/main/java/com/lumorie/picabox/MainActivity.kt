
package com.lumorie.picabox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var pendingCameraRequest: PermissionRequest? = null
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val trusted = request.origin.scheme == "https" &&
                        request.origin.host == "appassets.androidplatform.net"
                    val wantsCamera = request.resources.contains(
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    )

                    if (!trusted || !wantsCamera) {
                        request.deny()
                    } else if (checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        pendingCameraRequest = request
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.CAMERA),
                            42
                        )
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingCameraRequest == request) pendingCameraRequest = null
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ) = assetLoader.shouldInterceptRequest(request.url)
        }

        web.addJavascriptInterface(PrintBridge(this), "Lumorie")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        requestBtPermissionIfNeeded()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 42) {
            val request = pendingCameraRequest
            pendingCameraRequest = null

            if (request != null) {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    request.deny()
                    toast("กรุณาอนุญาตใช้กล้องเพื่อถ่ายภาพ")
                }
            }
        }
    }

    private fun requestBtPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                41
            )
        }
    }

    inner class PrintBridge(private val ctx: Context) {
        @JavascriptInterface
        fun platform(): String = "Android"

        @JavascriptInterface
        fun printBluetooth(base64: String) {
            if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) {
                runOnUiThread {
                    requestBtPermissionIfNeeded()
                    toast("กรุณาอนุญาต Bluetooth แล้วลองอีกครั้ง")
                }
                return
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                toast("อุปกรณ์นี้ไม่มี Bluetooth")
                return
            }

            val paired = try {
                adapter.bondedDevices.toList()
            } catch (_: SecurityException) {
                emptyList()
            }

            if (paired.isEmpty()) {
                toast("กรุณาจับคู่ Xprinter ในการตั้งค่า Android ก่อน")
                return
            }

            runOnUiThread {
                val choices = paired.map {
                    "${it.name ?: "Bluetooth"} · ${it.address}"
                }.toTypedArray()

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("เลือกเครื่องพิมพ์ Bluetooth")
                    .setItems(choices) { _, which ->
                        thread {
                            try {
                                val device = paired[which]
                                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                                socket.connect()
                                socket.outputStream.use { out ->
                                    out.write(escPosImage(decode(base64)))
                                    out.write(byteArrayOf(0x1d, 0x56, 0x00))
                                    out.flush()
                                }
                                socket.close()
                                toast("ส่งงานพิมพ์ไปยัง ${device.name ?: "เครื่องพิมพ์"} แล้ว")
                            } catch (e: Exception) {
                                toast("พิมพ์ Bluetooth ไม่สำเร็จ: ${e.message}")
                            }
                        }
                    }
                    .setNegativeButton("ยกเลิก", null)
                    .show()
            }
        }

        @JavascriptInterface
        fun printUsb(base64: String) {
            thread {
                try {
                    val manager = getSystemService(Context.USB_SERVICE) as UsbManager
                    val candidates = manager.deviceList.values.toList()
                    if (candidates.isEmpty()) {
                        toast("ไม่พบเครื่องพิมพ์ USB")
                        return@thread
                    }

                    val device = candidates.first()
                    if (!manager.hasPermission(device)) {
                        toast("กรุณาอนุญาต USB จากหน้าต่าง Android แล้วลองอีกครั้ง")
                        return@thread
                    }

                    val pair = findBulkOut(device)
                        ?: throw IllegalStateException("ไม่พบ USB Bulk OUT endpoint")
                    val connection = manager.openDevice(device)
                        ?: throw IllegalStateException("เปิด USB ไม่สำเร็จ")

                    try {
                        connection.claimInterface(pair.first, true)
                        val bytes = escPosImage(decode(base64))
                        var offset = 0

                        while (offset < bytes.size) {
                            val end = minOf(offset + 16 * 1024, bytes.size)
                            val chunk = bytes.copyOfRange(offset, end)
                            val sent = connection.bulkTransfer(
                                pair.second, chunk, chunk.size, 10000
                            )
                            if (sent < 0) {
                                throw IllegalStateException("ส่งข้อมูล USB ไม่สำเร็จ")
                            }
                            offset += sent
                        }
                    } finally {
                        connection.close()
                    }
                    toast("ส่งงานพิมพ์ USB แล้ว")
                } catch (e: Exception) {
                    toast("พิมพ์ USB ไม่สำเร็จ: ${e.message}")
                }
            }
        }
    }

    private fun findBulkOut(
        device: UsbDevice
    ): Pair<android.hardware.usb.UsbInterface, android.hardware.usb.UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT) {
                    return intf to ep
                }
            }
        }
        return null
    }

    private fun decode(data: String): Bitmap {
        val raw = android.util.Base64.decode(
            data.substringAfter(","),
            android.util.Base64.DEFAULT
        )
        return BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("อ่านภาพใบเสร็จไม่ได้")
    }

    private fun escPosImage(source: Bitmap): ByteArray {
        val width = minOf(576, source.width).let { it - (it % 8) }
        val height = (source.height.toFloat() * width / source.width)
            .toInt().coerceAtLeast(1)
        val bmp = Bitmap.createScaledBitmap(source, width, height, true)
        val bytesPerRow = width / 8
        val out = ByteArrayOutputStream()

        out.write(byteArrayOf(0x1b, 0x40))
        var top = 0

        while (top < height) {
            val rows = minOf(256, height - top)
            out.write(
                byteArrayOf(
                    0x1d, 0x76, 0x30, 0x00,
                    (bytesPerRow and 0xff).toByte(),
                    (bytesPerRow shr 8).toByte(),
                    (rows and 0xff).toByte(),
                    (rows shr 8).toByte()
                )
            )

            for (y in top until top + rows) {
                for (bx in 0 until bytesPerRow) {
                    var value = 0
                    for (bit in 0..7) {
                        val pixel = bmp.getPixel(bx * 8 + bit, y)
                        val gray = (
                            android.graphics.Color.red(pixel) * 299 +
                            android.graphics.Color.green(pixel) * 587 +
                            android.graphics.Color.blue(pixel) * 114
                        ) / 1000

                        if (gray < 175) value = value or (1 shl (7 - bit))
                    }
                    out.write(value)
                }
            }
            top += rows
        }

        out.write(byteArrayOf(0x0a, 0x0a, 0x0a))
        return out.toByteArray()
    }

    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
