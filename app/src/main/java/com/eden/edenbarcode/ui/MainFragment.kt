package com.eden.edenbarcode.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.eden.edenbarcode.R
import com.eden.edenbarcode.databinding.MainFragmentBinding
import com.eden.edenbarcode.model.Product
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.product_item.view.*
import kotlinx.android.synthetic.main.qr_bar_panel.view.*
import java.io.File
import java.io.IOException

private const val SCAN_QR_CODE_REQUEST = 100
private const val TAKE_PHOTO_REQUEST = 101

class MainFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private val columns = 3
    private lateinit var binding: MainFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.main_fragment, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        products_list.layoutManager = GridLayoutManager(context, columns)
        products_list.addItemDecoration(SpacesItemDecoration(3, columns))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        binding.vm = viewModel
        viewModel.getLiveProducts().observe(this, Observer { productList ->
            productList?.also { dbProducts ->
                val products = viewModel.getProducts(dbProducts)
                products_list.adapter = ProductAdapter(this, products, columns)
            }
        })
        viewModel.init()
    }

    @SuppressLint("InflateParams")
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.menu_scan -> {
                startActivityForResult(Intent(activity, ScanActivity::class.java), SCAN_QR_CODE_REQUEST)
                true
            }
            R.id.menu_get_qr -> {
                viewModel.generateQRCode(getGeometry()) { bitmap ->
                    val imageLayout = layoutInflater.inflate(R.layout.qr_bar_panel, null)
                    imageLayout.qr_bar_image.setImageBitmap(bitmap)
                    AlertDialog.Builder(context!!)
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .setView(imageLayout)
                        .create()
                        .show()
                }
                true
            }
            R.id.menu_add_product -> {
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    resolveActivity(activity?.packageManager ?: return false)
                    getPhotoURI()?.also { pair ->
                        putExtra(MediaStore.EXTRA_OUTPUT, pair.first)
                        activity?.intent?.putExtra("path", pair.second)
                        startActivityForResult(this, TAKE_PHOTO_REQUEST)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            SCAN_QR_CODE_REQUEST -> {
                Log.e(TAG, "finish recognize--")
                setBarcode(data?.getStringExtra("barcode") ?: return)
            }
            TAKE_PHOTO_REQUEST -> {
                //            val imageBitmap = data.extras.get("data") as Bitmap
                val path = activity?.intent?.getStringExtra("path") ?: return
                val bitmap = BitmapFactory.decodeFile(path)
                val thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 100, 100)
                viewModel.addNewBitmap(path, thumbnail)
            }
        }
    }

    private fun getPhotoURI(): Pair<Uri?, String?>? {
        context ?: return null
        return try {
            createImageFile()?.let { file ->
                Pair(
                    FileProvider.getUriForFile(
                        context!!,
                        "com.eden.edenbarcode.fileprovider",
                        file
                    ),
                    file.absolutePath
                )
            }
        } catch (ex: IOException) {
            null
        }
    }

    private fun createImageFile(): File? {
        // Create an image file name
        val storageDir = context?.getExternalFilesDir(null) ?: return null
        if (!storageDir.exists()) return null
        return File(storageDir, "JPEG_${System.currentTimeMillis()}.jpg")
    }

    private fun getGeometry(): Point {
        Point(view!!.width, view!!.height).apply {
            if (x > y) x = y else y = x
            return this
        }
    }

    fun productSelected(product: Product) {
        viewModel.productSelected(product)
    }

    private fun setBarcode(barcode: String?) {
        viewModel.onBarCode(barcode)
    }

    companion object {
        private const val TAG = "MainFragment"
        fun newInstance() = MainFragment()
    }
}

//http://www.ohandroid.com/gridlayoutmanager-x4.html
class SpacesItemDecoration(private val space: Int, private val columns: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = space
        outRect.right = space
        outRect.bottom = space
        if ((0 until columns).contains(parent.getChildLayoutPosition(view))) {
            outRect.top = space
        } else {
            outRect.top = 0
        }
    }
}

class ProductAdapter(private val mf: MainFragment, private val products: List<Product>, columns: Int) :
    RecyclerView.Adapter<ProductHolder>() {

    private val size = products.size + if (products.size % columns == 0) 0 else columns - products.size % columns
    override fun getItemCount(): Int {
        return size
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): ProductHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.product_item, parent, false)
        return ProductHolder(mf, view)
    }

    override fun onBindViewHolder(holder: ProductHolder, position: Int) {
        if (position < products.size) {
            holder.setProduct(products[position])
        } else {
            holder.clear()
        }
    }
}

class ProductHolder(private val mf: MainFragment, view: View) : RecyclerView.ViewHolder(view) {
    private val image: ImageView? = view.image
    private val code: TextView? = view.code

    fun setProduct(product: Product) {
        image?.setImageBitmap(product.image)
        code?.text = product.code
        itemView.setOnClickListener {
            Log.i("ProductHolder", "onClick")
            mf.productSelected(product)
        }
    }

    fun clear() {
        code?.text = ""
        image?.setImageDrawable(dummyDrawable)
    }

    companion object {
        private val dummyDrawable = ColorDrawable(Color.WHITE)
    }
}