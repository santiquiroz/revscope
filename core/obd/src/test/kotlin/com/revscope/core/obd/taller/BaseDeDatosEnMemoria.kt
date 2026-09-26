package com.revscope.core.obd.taller

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.obd.taller.sesion.TallerRepositoryRoom

fun baseDeDatosEnMemoria(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun AppDatabase.repositorioTaller(): TallerRepositoryRoom =
    TallerRepositoryRoom(diagSessionDao(), vehicleKnowledgeDao())
