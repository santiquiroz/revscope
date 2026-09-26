package com.revscope.core.obd.di

import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.TallerRepositoryRoom
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TallerModule {

    @Binds
    abstract fun bindTallerRepository(impl: TallerRepositoryRoom): TallerRepository
}
