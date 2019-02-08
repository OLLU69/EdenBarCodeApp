package com.eden.edenbarcode.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.LiveData
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
import com.eden.edenbarcode.repository.Products
import javax.inject.Inject

@Suppress("PropertyName")
class MainViewModel : ViewModel() {
    val info_text = ObservableField<String>()
    var barcode_label_text = ObservableField<String>()
    var barcode_label_visibility = ObservableBoolean(false)
    val cancel_bt_visibility = ObservableBoolean(false)
    val confirm_bt_group_visibility = ObservableBoolean(false)
    val link_menu_visibility = ObservableBoolean(false)
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
        setShowProductMode()
    }

    fun getLiveProducts(): LiveData<List<Products>> {
        return model.getLiveProducts()
    }

    fun getProducts(products: List<Products>): List<Product> {
        return model.getProducts(products)
    }

    fun onBarCode(barcode: String?) {
        setLinkProductMode(barcode ?: return)
    }

    private fun setShowProductMode() {
        clearMode()
    }

    private fun setLinkProductMode(barcode: String) {
        clearMode()
        info_text.set(context.resources.getText(R.string.select_cog_request).toString())
        barcode_label_text.set(barcode)
        barcode_label_visibility.set(true)
        link_menu_visibility.set(true)
        onCancelBt = {
            setShowProductMode()
        }
        cancel_bt_visibility.set(true)
        productSelected = { product ->
            setConfirmProductMode(product, barcode)
        }
    }

    private fun setConfirmProductMode(product: Product, barcode: String) {
        clearMode()
        info_text.set(textOf(R.string.confirm_cog_request))
        barcode_label_text.set(barcode)
        barcode_label_visibility.set(true)
        onCancelBt2 = {
            setShowProductMode()
        }
        onOkBt = {
            product.code = barcode
            model.setProduct(product)
            setShowProductMode()
        }
        confirm_bt_group_visibility.set(true)
        link_menu_visibility.set(true)
    }

    private fun selectProductForQRCodeMode(
        onCodeSelected: (String?) -> Unit
    ) {
        clearMode()
        info_text.set(textOf(R.string.select_product))
        cancel_bt_visibility.set(true)
        onCancelBt = {
            setShowProductMode()
        }
        productSelected = { product ->
            clearMode()
            onCodeSelected(product.code)
        }
        link_menu_visibility.set(true)
    }

    private fun clearMode() {
        barcode_label_visibility.set(false)
        cancel_bt_visibility.set(false)
        confirm_bt_group_visibility.set(false)
        link_menu_visibility.set(false)
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
    }
}
