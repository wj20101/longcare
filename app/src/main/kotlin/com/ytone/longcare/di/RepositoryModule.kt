package com.ytone.longcare.di

import com.ytone.longcare.data.repository.DefaultUserSessionRepository
import com.ytone.longcare.data.repository.LocationRepositoryImpl
import com.ytone.longcare.data.repository.LoginRepositoryImpl
import com.ytone.longcare.data.repository.OrderRepositoryImpl
import com.ytone.longcare.data.repository.ProfileRepositoryImpl
import com.ytone.longcare.data.repository.TencentFaceRepositoryImpl
import com.ytone.longcare.data.repository.SystemRepositoryImpl
import com.ytone.longcare.data.repository.UserListRepositoryImpl
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.domain.userlist.UserListRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserSessionRepository(impl: DefaultUserSessionRepository): UserSessionRepository

    @Binds
    @Singleton
    abstract fun bindLoginRepository(impl: LoginRepositoryImpl): LoginRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository
    
    @Binds
    @Singleton
    abstract fun bindTencentFaceRepository(impl: TencentFaceRepositoryImpl): TencentFaceRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindUserListRepository(impl: UserListRepositoryImpl): UserListRepository

    @Binds
    @Singleton
    abstract fun bindSystemRepository(impl: SystemRepositoryImpl): SystemRepository
}