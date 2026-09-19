package com.ytone.longcare.common.utils

import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.common.json.UnitJsonAdapter
import com.ytone.longcare.common.json.UriJsonAdapter

val DefaultMoshi: Moshi = Moshi.Builder()
    .add(Unit::class.java, UnitJsonAdapter)
    .add(Uri::class.java, UriJsonAdapter())
    .add(KotlinJsonAdapterFactory())
    .build()
