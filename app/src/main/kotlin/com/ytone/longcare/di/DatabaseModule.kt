package com.ytone.longcare.di

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Room
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderLocationDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW

/**
 * 数据库DI Module
 *
 * 提供Room数据库及其DAO的依赖注入。
 * 包含完整的异常处理机制：
 * - 数据库损坏自动重建
 * - 迁移失败回退
 * - 文件访问异常处理
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "LongCareDatabase"

    @Provides
    @Singleton
    fun provideLongCareDatabase(
        @ApplicationContext context: Context
    ): LongCareDatabase {
        return try {
            buildDatabase(context)
        } catch (e: SQLiteDatabaseCorruptException) {
            // 数据库损坏，删除后重建
            logE("Database corrupted, deleting and recreating", tag = TAG, throwable = e)
            deleteDatabase(context)
            buildDatabase(context)
        } catch (e: Exception) {
            // 其他异常，尝试删除重建
            logE("Database creation failed, attempting recovery", tag = TAG, throwable = e)
            try {
                deleteDatabase(context)
                buildDatabase(context)
            } catch (e2: Exception) {
                logE("Database recovery failed", tag = TAG, throwable = e2)
                throw e2
            }
        }
    }

    private fun buildDatabase(context: Context): LongCareDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = LongCareDatabase::class.java,
            name = LongCareDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true).build()
    }

    /**
     * 删除数据库文件
     * 
     * 使用 Context.deleteDatabase() 系统API，它会：
     * 1. 安全地删除主数据库文件
     * 2. 自动删除所有相关文件（-wal, -shm, -journal）
     * 3. 处理文件锁定等边缘情况
     */
    private fun deleteDatabase(context: Context): Boolean {
        val dbName = LongCareDatabase.DATABASE_NAME
        
        // 使用系统API删除数据库，这是最安全的方式
        val deleted = context.deleteDatabase(dbName)
        
        if (deleted) {
            logW("Database deleted successfully: $dbName", tag = TAG)
        } else {
            logW("Database deletion returned false, may not exist: $dbName", tag = TAG)
            // 即使deleteDatabase返回false，也尝试手动清理可能残留的文件
            cleanupDatabaseFiles(context, dbName)
        }
        
        return deleted
    }
    
    /**
     * 手动清理可能残留的数据库文件（备用方案）
     */
    private fun cleanupDatabaseFiles(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        val filesToDelete = listOf(
            dbFile,
            File(dbFile.absolutePath + "-wal"),
            File(dbFile.absolutePath + "-shm"),
            File(dbFile.absolutePath + "-journal")
        )
        
        filesToDelete.forEach { file ->
            if (file.exists()) {
                val success = file.delete()
                logW("Manual cleanup ${file.name}: ${if (success) "success" else "failed"}", tag = TAG)
            }
        }
    }

    @Provides
    fun provideOrderDao(database: LongCareDatabase): OrderDao {
        return database.orderDao()
    }

    @Provides
    fun provideOrderElderInfoDao(database: LongCareDatabase): OrderElderInfoDao {
        return database.orderElderInfoDao()
    }

    @Provides
    fun provideOrderLocalStateDao(database: LongCareDatabase): OrderLocalStateDao {
        return database.orderLocalStateDao()
    }

    @Provides
    fun provideOrderProjectDao(database: LongCareDatabase): OrderProjectDao {
        return database.orderProjectDao()
    }

    @Provides
    fun provideOrderImageDao(database: LongCareDatabase): OrderImageDao {
        return database.orderImageDao()
    }

    @Provides
    fun provideOrderLocationDao(database: LongCareDatabase): OrderLocationDao {
        return database.orderLocationDao()
    }
}
