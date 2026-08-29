package com.ytone.longcare.di

import com.ytone.longcare.common.image.ManagedImageFileStore
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.data.repository.DefaultSessionInvalidationHandler
import com.ytone.longcare.data.repository.DefaultUserSessionRepository
import com.ytone.longcare.data.repository.IdentificationRepositoryImpl
import com.ytone.longcare.data.repository.ImageRepository
import com.ytone.longcare.data.repository.LocationRepositoryImpl
import com.ytone.longcare.data.repository.LoginRepositoryImpl
import com.ytone.longcare.data.repository.OrderRepositoryImpl
import com.ytone.longcare.data.repository.ProfileRepositoryImpl
import com.ytone.longcare.data.repository.SaleRepositoryImpl
import com.ytone.longcare.data.repository.SessionFaceSetupRequestRepository
import com.ytone.longcare.data.repository.SystemRepositoryImpl
import com.ytone.longcare.data.repository.TencentFaceRepositoryImpl
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.data.repository.UserListRepositoryImpl
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.data.userstorage.ManagedSessionFilesCleanupHook
import com.ytone.longcare.data.userstorage.DefaultUserRehydrationCoordinator
import com.ytone.longcare.data.userstorage.ScopedUserFaceArtifactStorage
import com.ytone.longcare.data.userstorage.UserManagedImageFileStore
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.data.userstorage.RoomUserServiceReminderRepository
import com.ytone.longcare.data.userstorage.RoomUserCountdownTaskRepository
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.FaceSetupRequestRepository
import com.ytone.longcare.domain.facecache.UserFaceArtifactStorage
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.domain.userlist.UserListRepository
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeReadyHook
import com.ytone.longcare.domain.userstorage.UserRehydrationCoordinator
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import com.ytone.longcare.domain.userstorage.UserCountdownTaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserStorageLeaseAccess(
        impl: UserStorageRegistry,
    ): UserStorageLeaseAccess

    @Multibinds
    abstract fun sessionRuntimeCleanupHooks(): Set<SessionRuntimeCleanupHook>

    @Multibinds
    abstract fun sessionRuntimeReadyHooks(): Set<SessionRuntimeReadyHook>

    @Binds
    @Singleton
    abstract fun bindUserSessionRepository(impl: DefaultUserSessionRepository): UserSessionRepository

    @Binds
    @Singleton
    abstract fun bindUserRehydrationCoordinator(
        impl: DefaultUserRehydrationCoordinator,
    ): UserRehydrationCoordinator

    @Binds
    @Singleton
    abstract fun bindUserServiceReminderRepository(
        impl: RoomUserServiceReminderRepository,
    ): UserServiceReminderRepository

    @Binds
    @Singleton
    abstract fun bindUserCountdownTaskRepository(
        impl: RoomUserCountdownTaskRepository,
    ): UserCountdownTaskRepository

    @Binds
    @IntoSet
    abstract fun bindUserRehydrationReadyHook(
        impl: DefaultUserRehydrationCoordinator,
    ): SessionRuntimeReadyHook

    @Binds
    @IntoSet
    abstract fun bindUserRehydrationCleanupHook(
        impl: DefaultUserRehydrationCoordinator,
    ): SessionRuntimeCleanupHook

    @Binds
    @Singleton
    abstract fun bindSessionSecretProvider(impl: DefaultUserSessionRepository): SessionSecretProvider

    @Binds
    @Singleton
    abstract fun bindSessionInvalidationHandler(
        impl: DefaultSessionInvalidationHandler,
    ): SessionInvalidationHandler

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
    @IntoSet
    abstract fun bindTencentCredentialRuntimeCleanupHook(
        impl: TencentFaceRepositoryImpl,
    ): SessionRuntimeCleanupHook

    @Binds
    @Singleton
    abstract fun bindFaceSetupRequestRepository(
        impl: SessionFaceSetupRequestRepository,
    ): FaceSetupRequestRepository

    @Binds
    @Singleton
    abstract fun bindUserFaceArtifactStorage(
        impl: ScopedUserFaceArtifactStorage,
    ): UserFaceArtifactStorage

    @Binds
    @Singleton
    abstract fun bindManagedImageFileStore(
        impl: UserManagedImageFileStore,
    ): ManagedImageFileStore

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindUserListRepository(impl: UserListRepositoryImpl): UserListRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(impl: SaleRepositoryImpl): SaleRepository

    @Binds
    @Singleton
    abstract fun bindSystemRepository(impl: SystemRepositoryImpl): SystemRepository

    @Binds
    @Singleton
    abstract fun bindIdentificationRepository(impl: IdentificationRepositoryImpl): IdentificationRepository

    @Binds
    @Singleton
    abstract fun bindOrderDetailRepository(impl: UnifiedOrderRepository): OrderDetailRepository

    @Binds
    @Singleton
    abstract fun bindOrderImageRepository(impl: ImageRepository): OrderImageRepository

    @Binds
    @IntoSet
    abstract fun bindManagedSessionFilesCleanupHook(
        impl: ManagedSessionFilesCleanupHook,
    ): SessionRuntimeCleanupHook

    @Binds
    @IntoSet
    abstract fun bindOrderMemoryCleanupHook(
        impl: UnifiedOrderRepository,
    ): SessionRuntimeCleanupHook
}
