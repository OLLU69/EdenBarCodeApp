package com.eden.edenbarcode.ui

import android.arch.lifecycle.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.eden.edenbarcode.R

val dummyDrawable = ColorDrawable(Color.WHITE)
fun ViewGroup.inflate(): View {
    return LayoutInflater.from(context)
        .inflate(R.layout.product_item, this, false)
}

inline fun <reified T : ViewModel> Fragment.viewModel(body: T.() -> Unit): T {
    ViewModelProviders.of(this)[T::class.java].apply {
        body()
        return this
    }
}

fun <T, L : LiveData<T>> LifecycleOwner.observe(liveData: L, body: (T?) -> Unit) =
    liveData.observe(this, Observer(body))

