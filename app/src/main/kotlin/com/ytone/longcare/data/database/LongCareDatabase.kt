package com.ytone.longcare.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderLocationDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.model.OrderElderInfoEntity
import com.ytone.longcare.model.OrderEntity
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.model.OrderProjectEntity

/**
 * LongCare应用数据库
 * 
 * 包含以下表：
 * - orders: 订单核心信息
 * - order_elder_info: 订单老人信息
 * - order_local_states: 订单本地状态
 * - order_projects: 订单项目列表
 * - order_images: 订单图片
 * - order_locations: 订单定位记录
 */
@Database(
    entities = [
        OrderEntity::class,
        OrderElderInfoEntity::class,
        OrderLocalStateEntity::class,
        OrderProjectEntity::class,
        OrderImageEntity::class,
        OrderLocationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LongCareDatabase : RoomDatabase() {
    
    abstract fun orderDao(): OrderDao
    abstract fun orderElderInfoDao(): OrderElderInfoDao
    abstract fun orderLocalStateDao(): OrderLocalStateDao
    abstract fun orderProjectDao(): OrderProjectDao
    abstract fun orderImageDao(): OrderImageDao
    abstract fun orderLocationDao(): OrderLocationDao
    
    companion object {
        const val DATABASE_NAME = "longcare_database"
    }
}
