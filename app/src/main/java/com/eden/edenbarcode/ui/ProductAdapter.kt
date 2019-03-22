package com.eden.edenbarcode.ui

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.eden.edenbarcode.model.Product
import kotlinx.android.synthetic.main.product_item.view.*
import kotlin.properties.Delegates

class ProductAdapter(
    columns: Int, private val productSelected: (Product) -> Unit
) :
    RecyclerView.Adapter<ProductHolder>() {

    var products: List<Product> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    private val size = products.size + if (products.size % columns == 0) 0 else columns - products.size % columns
    override fun getItemCount(): Int {
        return size
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): ProductHolder {
        return ProductHolder(parent.inflate())
    }

    override fun onBindViewHolder(holder: ProductHolder, position: Int) {
        if (position < products.size) holder.setProduct(products[position], productSelected) else holder.clear()
    }
}

class ProductHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun setProduct(product: Product, productSelected: (Product) -> Unit) {
        itemView.image.setImageBitmap(product.image)
        itemView.code.text = product.code
        itemView.setOnClickListener {
            Log.i("ProductHolder", "onClick")
            productSelected(product)
        }
    }

    fun clear() {
        itemView.code.text = ""
        itemView.image.setImageDrawable(dummyDrawable)
    }
}
