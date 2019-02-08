package com.eden.edenbarcode.qr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

typealias IBarcodeRecognizerCallback = ((barcode: String?) -> Unit)

interface IBarcodeRecognizer {
    fun recognizeBarCode(
        data: ByteArray,
        pictureSize: Point,
        recognizeRect: Rect,
        callback: IBarcodeRecognizerCallback
    )

    fun generateBarCode(data: String, width: Int, height: Int): Bitmap?
    fun release()
    fun prepare()
}

/**
 * ----
 * Created by Лукащук Олег(oleg) on 15.01.19.
 */
class BarcodeRecognizer @Inject constructor() : IBarcodeRecognizer {
    override fun recognizeBarCode(
        data: ByteArray,
        pictureSize: Point,
        recognizeRect: Rect,
        callback: IBarcodeRecognizerCallback
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            var barCode: String? = null
            try {
                val source = PlanarYUVLuminanceSource(
                    data, pictureSize.x, pictureSize.y,
                    recognizeRect.left,
                    recognizeRect.top,
                    (recognizeRect.right - recognizeRect.left),
                    (recognizeRect.bottom - recognizeRect.top),
                    false
                )

                val bitmap = BinaryBitmap(HybridBinarizer(source))
                barCode = reader.decodeWithState(bitmap)?.text
            } catch (e: Exception) {
                Log.e(TAG, "recognize error: ", e)
            }
            withContext(Dispatchers.Main) {
                callback(barCode)
            }
        }
    }

    override fun generateBarCode(data: String, width: Int, height: Int): Bitmap? {
        return BarcodeEncoder().encodeBitmap(data, BarcodeFormat.QR_CODE, width, height)
    }

    override fun release() {
//        readerInstance = null
    }

    override fun prepare() {
        reader
    }

    companion object {

        private val TAG = BarcodeRecognizer::class.java.simpleName
        // Подготовка ридера довольно медленная операция, поэтому его кешируем
        private var readerInstance: MultiFormatReader? = null
        private val reader: MultiFormatReader
            get() {
                return readerInstance ?: let {
                    readerInstance = prepareReader()
                    readerInstance!!
                }
            }

        private fun prepareReader(): MultiFormatReader {
            val decodeFormats = listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODABAR
            )
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to decodeFormats)
            return MultiFormatReader().apply { setHints(hints) }
        }
    }
}

