package com.eden.edenbarcode.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.graphics.Bitmap
import android.graphics.Point
import android.view.View
import com.eden.edenbarcode.EdenApp
import com.eden.edenbarcode.R
import com.eden.edenbarcode.model.EdenModel
import com.eden.edenbarcode.model.Product
import com.eden.edenbarcode.qr.BarcodeRecognizer
import javax.inject.Inject

@Suppress("PropertyName")
class MainViewModel : ViewModel() {
    val products: MutableLiveData<List<Product>> = MutableLiveData()
    val infoText = ObservableField<String>()
    var barcodeLabelText = ObservableField<String>()
    var barcodeLabelVisibility = ObservableBoolean(false)
    val cancelBtVisibility = ObservableBoolean(false)
    val confirmBtGroupVisibility = ObservableBoolean(false)
    val linkMenuVisibility = ObservableBoolean(false)
    var productSelected: (product: Product) -> Unit = {}
    private var onCancelBt: ((View) -> Unit) = {}
    private var onCancelBt2: ((View) -> Unit) = {}
    private var onOkBt: ((View) -> Unit) = {}
    @Inject
    lateinit var model: EdenModel
    @Inject
    lateinit var recognizer: BarcodeRecognizer
    @SuppressLint("StaticFieldLeak")
    @Inject
    lateinit var context: Context

    init {
        EdenApp.appComponent.inject(this)
    }

    fun init() {
        products.value = model.getProducts()
        setShowProductMode()
    }

    fun onBarCode(barcode: String?) {
        barcode ?: return
        setLinkProductMode(barcode)
    }

    private fun setShowProductMode() {
        clearMode()
    }

    private fun setLinkProductMode(barcode: String) {
        clearMode()
        infoText.set(context.resources.getText(R.string.select_cog_request).toString())
        barcodeLabelText.set(barcode)
        barcodeLabelVisibility.set(true)
        linkMenuVisibility.set(true)
        onCancelBt = {
            setShowProductMode()
        }
        cancelBtVisibility.set(true)
        productSelected = { product ->
            setConfirmProductMode(product, barcode)
        }
    }

    private fun setConfirmProductMode(product: Product, barcode: String) {
        clearMode()
        infoText.set(textOf(R.string.confirm_cog_request))
        barcodeLabelText.set(barcode)
        barcodeLabelVisibility.set(true)
        onCancelBt2 = {
            setShowProductMode()
        }
        onOkBt = {
            product.code = barcode
            model.setProduct(product)
            products.value = products.value
            setShowProductMode()
        }
        confirmBtGroupVisibility.set(true)
        linkMenuVisibility.set(true)
    }

    private fun selectProductForQRCodeMode(
        onCodeSelected: (String?) -> Unit
    ) {
        clearMode()
        infoText.set(textOf(R.string.select_product))
        cancelBtVisibility.set(true)
        onCancelBt = {
            setShowProductMode()
        }
        productSelected = { product ->
            clearMode()
            onCodeSelected(product.code)
        }
        linkMenuVisibility.set(true)
    }

    private fun clearMode() {
        barcodeLabelVisibility.set(false)
        cancelBtVisibility.set(false)
        confirmBtGroupVisibility.set(false)
        linkMenuVisibility.set(false)
        onCancelBt = {}
        onCancelBt2 = {}
        onOkBt = {}
        productSelected = {}
    }

    fun generateQRCode(point: Point, onBitmap: (Bitmap?) -> Unit) {
        //показать панель с запросом на выбор товара
        selectProductForQRCodeMode { code ->
            code?.also {
                //по положительному ответу генерировать код (ширина, высота) -> bitmap
                val bitmap = recognizer.generateBarCode(it, point.x, point.y)
                //отобразить код
                onBitmap(bitmap)
            }
        }
    }

    fun onCancelClick(view: View) {
        onCancelBt(view)
    }

    fun onCancel2Click(view: View) {
        onCancelBt2(view)
    }

    fun onOkBtClick(view: View) {
        onOkBt(view)
    }

    private fun textOf(textId: Int): String? {
        return context.resources.getText(textId).toString()
    }

    fun addNewBitmap(bitmapPath: String, thumbnail: Bitmap) {
        model.setProduct(Product().apply {
            imagePath = bitmapPath
            image = thumbnail
        })
        products.value = model.getProducts()
    }
}
