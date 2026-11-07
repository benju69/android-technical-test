package fr.leboncoin.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Small dataset (static JSON, ~hundreds of rows) — a simple, migration-free
 * database. exportSchema is disabled for this exercise; a production app
 * would export schemas and add migration tests.
 */
@Database(
    entities = [AlbumEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
}
