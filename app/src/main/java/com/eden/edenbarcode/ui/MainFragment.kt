package com.eden.edenbarcode.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
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
import com.eden.edenbarcode.R
import com.eden.edenbarcode.databinding.MainFragmentBinding
import com.eden.edenbarcode.model.Product
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.qr_bar_panel.view.*
import java.io.File
import java.io.IOException

private const val SCAN_QR_CODE_REQUEST = 100
private const val TAKE_PHOTO_REQUEST = 101
private const val DECORATION_SPACE = 3
private const val COLUMNS_COUNT = 3

class MainFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainFragmentBinding
    private lateinit var adapter: ProductAdapter

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
        products_list.layoutManager = GridLayoutManager(context, COLUMNS_COUNT)
        products_list.addItemDecoration(SpacesItemDecoration(DECORATION_SPACE, COLUMNS_COUNT))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = viewModel {
            observe(products, ::renderProduct)
        }
        binding.vm = viewModel
        adapter = ProductAdapter(COLUMNS_COUNT) { product -> productSelected(product) }
        products_list.adapter = adapter
        viewModel.init()
    }

    private fun renderProduct(productList: List<Product>?) {
        adapter.products = productList.orEmpty()
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
                    getPhotoURI()?.also { photoData ->
                        val (extraOutputUrl, path) = photoData
                        putExtra(MediaStore.EXTRA_OUTPUT, extraOutputUrl)
                        activity?.intent?.putExtra("path", path)
                    }
                    startActivityForResult(this, TAKE_PHOTO_REQUEST)
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
                (data?.extras?.get("data") as? Bitmap)?.also { bitmap ->
                    viewModel.addNewBitmap("", bitmap)
                    return
                }
                val path = activity?.intent?.getStringExtra("path") ?: return
                val bitmap = BitmapFactory.decodeFile(path)
                val thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 100, 100)
                viewModel.addNewBitmap(path, thumbnail)
            }
        }
    }

    private fun getPhotoURI(): Pair<Uri?, String?>? {
        return try {
            getImageFile()?.run {
                Pair(
                    FileProvider.getUriForFile(
                        context ?: return null,
                        "com.eden.edenbarcode.fileprovider",
                        this
                    ),
                    absolutePath
                )
            }
        } catch (ex: IOException) {
            null
        }
    }

    private fun getImageFile(): File? {
        // Create an image file name
        context?.getExternalFilesDir(null)
            ?.takeIf { it.exists() }
            ?.apply { return File(this, "JPEG_${System.currentTimeMillis()}.jpg") }

        return context?.filesDir
            ?.takeIf { it.exists() }
            ?.let { File(it, "JPEG_${System.currentTimeMillis()}.jpg") }
    }

    private fun getGeometry(): Point {
        Point(view!!.width, view!!.height).apply {
            if (x > y) x = y else y = x
            return this
        }
    }

    private fun productSelected(product: Product) {
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

