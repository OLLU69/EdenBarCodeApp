package com.eden.edenbarcode.repository

import android.arch.persistence.room.*
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ----
 * Created by Лукащук Олег(oleg) on 07.02.19.
 */
interface Repository {
    fun getProducts(): List<Products>
    fun setProduct(product: Products): Long?
}

private const val DB_NAME = "products_db"

@Singleton
class RoomRepository @Inject constructor(val context: Context) : Repository {

    private val db = Room.databaseBuilder(context, ProductsDB::class.java, DB_NAME)
        .allowMainThreadQueries()
        .build()

    override fun getProducts(): List<Products> {
        return db.productDao.getProducts()
    }

    override fun setProduct(product: Products): Long? {
        return db.productDao.setProduct(product)
    }
}

@Database(entities = [Products::class], version = 1, exportSchema = true)
abstract class ProductsDB : RoomDatabase() {
    abstract val productDao: ProductsDao
}

@Dao
interface ProductsDao {
    @Query("SELECT * FROM Products")
    fun getProducts(): List<Products>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setProduct(product: Products): Long?
}

@Entity
class Products {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
    var imagePath: String? = null
    var imageData: ByteArray? = null
    var code: String? = null
}