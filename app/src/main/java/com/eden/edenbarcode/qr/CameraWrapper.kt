@file:Suppress("DEPRECATION")

package com.eden.edenbarcode.qr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.hardware.Camera
import android.os.Handler
import android.util.Log
import android.view.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * ----
 * Created by Лукащук Олег(oleg) on 14.01.19.
 * Класс обертка для реализации превью с камеры и работы с камерой
 */

// byte[] data содержит сырые данные с камеры (YuvImage)
typealias PreviewCameraCallback = (data: ByteArray, frameSize: Point) -> Unit

private const val TAG = "CameraWrapper"
private const val AUTO_FOCUS_INTERVAL: Long = 250
private const val AUTO_FOCUS_LONG_INTERVAL: Long = 2500
private const val MIN_PREVIEW_PIXELS = 480 * 320 // normal screen
private const val MAX_ASPECT_DISTORTION = 0.15

interface ICameraWrapper {
    fun open(display: Display, surfaceView: SurfaceView)
    // закрывает сессию для работы с камерой и освобождает ресурсы
    fun close()

    /*
Каллбек для получения сырой картинки с камеры в момент фокусировки (нужен для распознавания баркодов или иных действий с четкой картинкой)
срабатывает в среднем 1 раз в 2 секунды, при фокусировке камеры на объекте
для получения одиночной картинки, его можно назначить и после получения картинки скинуть в null
*/
    fun setPreviewCameraCallback(callback: PreviewCameraCallback?)
}

class CameraWrapper @Inject constructor() : ICameraWrapper {
    private var display: Display? = null
    private var surfaceView: SurfaceView? = null
    private var cameraResolution = Point()
    private val screenResolution = Point()
    private val handler = Handler()
    private var camera: Camera? = null
    private var active = false
    // Пользовательский калбек для получения сырого фрейма с камеры при автофокусировке
    private var previewCameraCallback: (PreviewCameraCallback)? = null
    // Временный калбек для получения картинки с камеры при автофокусировке
    private var takePictureCallback: PictureCameraCallback? = null
    private var cameraStarted = false
    private var resultOrientation: Int = 0
    private var cameraId = -1
    private var isFrontalCamera = false
    // Калбек internalPreviewCallback выставляется в момент наведения фокуса у камеры (событие onAutoFocus)
    // При снятии картинки (в onPreviewFrame) он скидывается в Null, чтобы событие onPreview срабатывало только один раз при фокусировке камеры
    private val internalPreviewCallback = Camera.PreviewCallback { data, camera ->
        if (isReady && data.isNotEmpty()) {
            // скидываем внутренний калбек, чтобы он не вызывался постоянно
            // он должен вызываться только один раз при автофокусе
            camera.setPreviewCallback(null)

            val pictureSize = camera.parameters.previewSize
            val preparedData: ByteArray
            preparedData = if (needRotateImage()) {
                //TODO: This is to use camera in landScape mode
                // В случае неправильной ориентации камеры переворачиваем картинку
                rotatePicture(data, pictureSize)
            } else {
                data.copyOf(data.size)
            }

            val size = Point(pictureSize.width, pictureSize.height)
            previewCameraCallback?.invoke(preparedData, size)
        }
    }
    private var focusModeAuto = false
    // Запускает процесс автофокуса на камере.
    // Каллбак на событии автофокуса запускает создание снимка экрана камеры и отправки его на распознавания
    private val autoFocusRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isReady) {
                return
            }

            handler.removeCallbacks(this)
            handler.postDelayed(this, AUTO_FOCUS_LONG_INTERVAL)
            safeAutoFocus(cameraAutoFocusCallback)
        }
    }
    private val cameraAutoFocusCallback = Camera.AutoFocusCallback { success, camera ->
        if (isReady) {
            val message = "onAutoFocus callback: " + if (success) "success" else "failed"
            Log.e(TAG, message)
            // Если камера сфокусировалась
            if (success) {
                previewCameraCallback?.apply {
                    // В момент автофокуса камеры навешиваем каллбак с Preview, снимаем в нем сырую картинку и там же обнуляем каллбек
                    camera.setPreviewCallback(internalPreviewCallback)
                }
                takePictureCallback?.apply {
                    camera.takePicture(null, null, { data, camera1 ->
                        if (isReady && data.isNotEmpty()) {
                            val size = camera1.parameters.previewSize
                            if (needRotateImage()) {
                                // В случае неправильной ориентации камеры переворачиваем картинку
                                val rotatedBitmap =
                                    rotateBitmap(BitmapFactory.decodeByteArray(data, 0, data.size), size, rotateAngle)
                                val os = ByteArrayOutputStream()
                                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                                rotatedBitmap.recycle()
                                onPicture(os.toByteArray(), Point(size.width, size.height))
                            } else {
                                onPicture(data, Point(size.width, size.height))
                            }
                        }
                        takePictureCallback = null
                    })
                }
            }

            if (!focusModeAuto) {
                handler.removeCallbacks(autoFocusRunnable)
                handler.postDelayed(autoFocusRunnable, AUTO_FOCUS_INTERVAL)
            }
        }
    }
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!cameraStarted) {
                cameraStart(holder)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (!cameraStarted) {
                cameraStart(holder)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (isReady) {
                close()
            }
        }
    }

    // Признак, что камера активирована и готова к работе
    private val isReady: Boolean
        get() = active && camera != null

    private val rotateAngle: Int
        get() = if (isFrontalCamera) {
            when (resultOrientation) {
                90 -> 270
                180 -> 90
                else -> 0
            }
        } else {
            when (resultOrientation) {
                180 -> 0
                0 -> 0
                else -> 90
            }
        }

    override fun open(display: Display, surfaceView: SurfaceView) {
        close()
        this.display = display
        this.surfaceView = surfaceView

        handler.post { this.prepareCamera() }
    }

    // закрывает сессию для работы с камерой и освобождает ресурсы
    override fun close() {
        active = false
        cameraStarted = false

        handler.removeCallbacks(autoFocusRunnable)

        camera?.apply {
            setPreviewCallback(null)
            stopPreview()
            release()
            camera = null
        }
    }

    // Каллбек для получения сырой картинки с камеры в момент фокусировки (нужен для распознавания баркодов или иных действий с четкой картинкой)
    // срабатывает в среднем 1 раз в 2 секунды, при фокусировке камеры на объекте
    // для получения одиночной картинки, его можно назначить и после получения картинки скинуть в null
    @Synchronized
    override fun setPreviewCameraCallback(callback: PreviewCameraCallback?) {
        previewCameraCallback = callback
        if (isReady && previewCameraCallback != null) {
            autoFocusRunnable.run()
        } else {
            handler.removeCallbacks(autoFocusRunnable)
        }
    }

    private fun prepareCamera() {
        try {
            if (cameraStarted) {
                camera!!.stopPreview()
                camera!!.release()
                camera = null

                cameraStarted = false
            }
            cameraId = -1
            camera = Camera.open(getCameraId())

            screenResolution.x = (surfaceView!!.parent as ViewGroup).width
            screenResolution.y = (surfaceView!!.parent as ViewGroup).height

            setCameraDisplayOrientation()
            setCameraParameters()
            createSurface()
        } catch (e: Exception) {
            e.printStackTrace()
            // throw new RuntimeException(e); // debug, после отладки надо будет убрать возбуждение ошибки
        }
    }

    private fun cameraStart(holder: SurfaceHolder) {
        try {
            camera!!.setPreviewDisplay(holder)
            active = true

            camera!!.startPreview()
            cameraStarted = true
            autoFocusRunnable.run()
        } catch (e: Exception) {
            e.printStackTrace()
            // throw new RuntimeException(e); // debug, после отладки надо будет убрать возбуждение ошибки
        }
    }

    private fun getCameraId(): Int {
        if (cameraId < 0) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK // задняя камера
        }
        return cameraId
    }

    private fun setCameraDisplayOrientation() {
        val cameraId = getCameraId()
        // определяем насколько повернут экран от нормального положения
        val rotation = display!!.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        // получаем инфо по камере cameraId
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)

        isFrontalCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT

        // передняя камера
        if (isFrontalCamera) {
            resultOrientation = 360 - degrees - info.orientation
            resultOrientation += 360
        } else {
            // задняя камера
            resultOrientation = 360 - degrees + info.orientation
        }

        resultOrientation %= 360
        camera!!.setDisplayOrientation(resultOrientation)
    }

    private fun setCameraParameters() {

        val parameters = camera!!.parameters
        // Выставляем профиль для чтения баркодов
        setFocusParams(parameters) //focus_mode_continuous);
        setBarcodeSceneMode(parameters)

        setVideoStabilization(parameters)
        // setFocusArea(parameters);
        // setMetering(parameters);
        //setInvertColor(parameters);

        // Определяем оптимальные размеры Preview из доступных на камере
        cameraResolution = findBestPreviewSizeValue(parameters, screenResolution)
        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y)

        // Прописываем параметры в камеру
        camera!!.parameters = parameters
    }

    private fun setFocusParams(parameters: Camera.Parameters) {
        val supportedFocusModes = parameters.supportedFocusModes

        focusModeAuto = false
        val focusMode = findSettableValue(
            supportedFocusModes,
            Camera.Parameters.FOCUS_MODE_AUTO,
            Camera.Parameters.FOCUS_MODE_MACRO
        )
        focusMode?.apply {
            if (focusMode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE || focusMode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) {
                focusModeAuto = true
            }
            if (focusMode == parameters.focusMode) {
                Log.i(TAG, "Focus mode already set to $focusMode")
            } else {
                parameters.focusMode = focusMode
            }
        }
    }

    private fun setVideoStabilization(parameters: Camera.Parameters) {
        if (parameters.isVideoStabilizationSupported) {
            if (!parameters.videoStabilization) {
                parameters.videoStabilization = true
            }
        }
    }

    private fun setBarcodeSceneMode(parameters: Camera.Parameters) {
        if (Camera.Parameters.SCENE_MODE_BARCODE == parameters.sceneMode) {
            Log.i(TAG, "SCENE_MODE_BARCODE already set")
            return
        }
        val sceneMode = findSettableValue(parameters.supportedSceneModes, Camera.Parameters.SCENE_MODE_BARCODE)
        sceneMode?.apply { parameters.sceneMode = this }
    }

    private fun findSettableValue(
        supportedValues: Collection<String>?,
        vararg desiredValues: String
    ): String? {
        return supportedValues?.let { values ->
            desiredValues.find { values.contains(it) }
        }
    }

    private fun findBestPreviewSizeValue(parameters: Camera.Parameters, screenResolution: Point): Point {

        val defaultSize = parameters.previewSize
        val rawSupportedSizes = parameters.supportedPreviewSizes ?: return Point(defaultSize.width, defaultSize.height)

        val screenAspectRatio = screenResolution.x / screenResolution.y.toDouble()

        // Find a suitable size, with max resolution
        var maxResolution = 0
        var maxResPreviewSize: Camera.Size? = null
        for (size in rawSupportedSizes) {
            val realWidth = size.width
            val realHeight = size.height
            val resolution = realWidth * realHeight
            if (resolution < MIN_PREVIEW_PIXELS) {
                continue
            }

            val isCandidatePortrait = realWidth < realHeight
            val maybeFlippedWidth = if (isCandidatePortrait) realHeight else realWidth
            val maybeFlippedHeight = if (isCandidatePortrait) realWidth else realHeight
            val aspectRatio = maybeFlippedWidth / maybeFlippedHeight.toDouble()
            val distortion = Math.abs(aspectRatio - screenAspectRatio)
            if (distortion > MAX_ASPECT_DISTORTION) {
                continue
            }

            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                return Point(realWidth, realHeight)
            }

            // Resolution is suitable; record the one with max resolution
            if (resolution > maxResolution) {
                maxResolution = resolution
                maxResPreviewSize = size
            }
        }

        // If no exact match, use largest preview size. This was not a great idea on older devices because
        // of the additional computation needed. We're likely to get here on newer Android 4+ devices, where
        // the CPU is much more powerful.
        return maxResPreviewSize
            ?.let { Point(it.width, it.height) }
            ?: Point(defaultSize.width, defaultSize.height)
    }

    private fun createSurface() {
        // надо привести размер области превью к оптимальному размеру, корелирующему с режимом камеры
        val width: Int
        val height: Int
        // вычисляем aspectRation камеры как отношение широкой стороны к узкой стороне
        val cameraAspectRatio =
            Math.max(cameraResolution.y, cameraResolution.x).toFloat() / Math.min(
                cameraResolution.y,
                cameraResolution.x
            ).toFloat()
        // приводим размер превью к aspectRatio камеры
        if (screenResolution.x * cameraAspectRatio > screenResolution.y) {
            width = screenResolution.x
            height = (screenResolution.x * cameraAspectRatio).toInt()
        } else {
            width = (screenResolution.y / cameraAspectRatio).toInt()
            height = screenResolution.y
        }

        val surfaceHolder = surfaceView!!.holder
        surfaceHolder.setFixedSize(width, height)
        surfaceHolder.addCallback(surfaceCallback)
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    private fun safeAutoFocus(autoFocusCallback: Camera.AutoFocusCallback) {
        try {
            camera!!.autoFocus(autoFocusCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun needRotateImage(): Boolean {
        return rotateAngle > 0
    }

    // поворачивает bitmap
    private fun rotateBitmap(source: Bitmap, pictureSize: Camera.Size, angle: Int): Bitmap {
        //Log.e(TAG, "rotate bitmap");
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        val oldWidth = pictureSize.width
        pictureSize.width = pictureSize.height
        pictureSize.height = oldWidth
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // поворачивает картинку в случае неправильной ориентации камеры
    private fun rotatePicture(data: ByteArray, pictureSize: Camera.Size): ByteArray {
        Log.e(TAG, "rotate picture")
        val width = pictureSize.width
        val height = pictureSize.height
        val rotatedData = ByteArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width)
                rotatedData[x * height + height - y - 1] = data[x + y * width]
        }
        pictureSize.width = height
        pictureSize.height = width

        return rotatedData
    }

    interface PictureCameraCallback {
        // byte[] data содержит сжатые данные с камеры (JPEG)
        fun onPicture(jpegData: ByteArray?, frameSize: Point)
    }
}
