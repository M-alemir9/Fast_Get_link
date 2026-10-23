package com.fastget.downloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fastget.downloader.data.local.dao.DownloadDao
import com.fastget.downloader.data.local.entity.DownloadChunkEntity
import com.fastget.downloader.data.local.entity.DownloadTaskEntity

@Database(
    entities = [
        DownloadTaskEntity::class,
        DownloadChunkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FastGetDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "fastget_downloads.db"
    }
}
