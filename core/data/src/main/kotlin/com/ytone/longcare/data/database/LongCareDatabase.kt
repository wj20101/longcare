package com.ytone.longcare.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.entity.OrderElderInfoEntityDb
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.data.database.entity.OrderLocalStateEntityDb
import com.ytone.longcare.data.database.entity.OrderProjectEntityDb

/**
 * LongCare应用数据库
 * 
 * 包含以下表：
 * - orders: 订单核心信息
 * - order_elder_info: 订单老人信息
 * - order_local_states: 订单本地状态
 * - order_projects: 订单项目列表
 * - order_images: 订单图片
 *
 * 定位上报是订单会话内的实时网络行为，不属于本地数据库。
 */
@Database(
    entities = [
        OrderEntityDb::class,
        OrderElderInfoEntityDb::class,
        OrderLocalStateEntityDb::class,
        OrderProjectEntityDb::class,
        OrderImageEntityDb::class
    ],
    version = 3,
    exportSchema = true
)
abstract class LongCareDatabase : RoomDatabase() {
    
    abstract fun orderDao(): OrderDao
    abstract fun orderElderInfoDao(): OrderElderInfoDao
    abstract fun orderLocalStateDao(): OrderLocalStateDao
    abstract fun orderProjectDao(): OrderProjectDao
    abstract fun orderImageDao(): OrderImageDao
    
    companion object {
        const val DATABASE_NAME = "longcare_database"
        const val DATABASE_VERSION = 3
    }
}
