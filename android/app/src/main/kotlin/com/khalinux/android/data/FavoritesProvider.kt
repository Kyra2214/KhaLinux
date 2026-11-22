package com.khalinux.android.data

import android.content.Context
import com.khalinux.runtime.favorites.FavoritesStore
import java.io.File

/** Instância única do `FavoritesStore` compartilhada entre os ViewModels (arquivo em filesDir). */
object FavoritesProvider {
    @Volatile private var store: FavoritesStore? = null

    fun get(context: Context): FavoritesStore =
        store ?: synchronized(this) {
            store ?: FavoritesStore(File(context.applicationContext.filesDir, "favorites.tsv")).also { store = it }
        }
}
