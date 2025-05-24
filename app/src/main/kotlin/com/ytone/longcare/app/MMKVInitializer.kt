package com.ytone.longcare.app

import android.content.Context
import androidx.startup.Initializer
import com.tencent.mmkv.MMKV

class MMKVInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        MMKV.initialize(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other Initializers in this case
        return emptyList()
    }
}