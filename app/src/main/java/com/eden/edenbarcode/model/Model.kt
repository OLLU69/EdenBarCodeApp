package com.eden.edenbarcode.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.eden.edenbarcode.repository.Products
import com.eden.edenbarcode.repository.Repository
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.random.Random

interface EdenModel {
    fun getProducts(): List<Product>
    fun setProduct(product: Product): Long?
}

class RoomEdenModel @Inject constructor(val context: Context) : EdenModel {
    @Inject
    lateinit var repository: Repository

    override fun getProducts(): List<Product> {
        return repository.getProducts().ifEmpty {
            val random = Random(seed = 12376768)
            images.forEach { imagePath ->
                Products().apply {
                    code = "${random.nextInt(100000, 1000000)}"
                    imageData = context.resources.assets.open(imagePath).readBytes()
                    repository.setProduct(this)
                }
            }
            repository.getProducts()
        }.map { products ->
            Product(products)
        }
    }

    override fun setProduct(product: Product): Long? {
        val id = repository.setProduct(Products().apply {
            id = product.id
            code = product.code
            imagePath = product.imagePath
            imageData = getArray(product.image)
        })
        println("setProduct() = $id")
        return id
    }
}

class Product() {
    constructor(products: Products) : this() {
        id = products.id
        code = products.code
        imagePath = products.imagePath
        image = getBitmap(products.imageData)
    }

    var id: Int? = null
    var image: Bitmap? = null
    var imagePath: String? = null
    var code: String? = null
}

private val images = arrayOf(
    "product/01-lipstick-icon.png",
    "product/02-lips-icon.png",
    "product/03-tube-icon.png",
    "product/04-blob-icon.png",
    "product/05-pink-pencil-icon.png",
    "product/06-black-pencil-icon.png",
    "product/07-cosmetic-brush-icon.png",
    "product/08-mascara-icon.png",
    "product/09-cream-icon.png",
    "product/10-nail-polish-icon.png",
    "product/12-mirror-icon.png",
    "product/bags.jpeg",
    "product/bottles.jpeg",
    "product/nail polish.png",
    "product/organiser.jpg",
    "product/shadow.jpg",
    "product/stickers.png",
    "product/tube.jpeg"
)

private fun getArray(bitmap: Bitmap?): ByteArray? {
    bitmap?.also { b ->
        val toStream = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 0, toStream)
        return toStream.toByteArray()
    }
    return null
}

private fun getBitmap(bitmapArray: ByteArray?): Bitmap? {
    bitmapArray?.also { array ->
        return BitmapFactory.decodeByteArray(array, 0, array.size)
    }
    return null
}
