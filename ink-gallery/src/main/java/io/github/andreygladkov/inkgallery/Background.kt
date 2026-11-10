package io.github.andreygladkov.inkgallery

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One thread for each kind of work, so decoding a large photo never holds up a list or the thumbnails. */
object Background {
    val queries: ExecutorService = Executors.newSingleThreadExecutor()
    val thumbnails: ExecutorService = Executors.newSingleThreadExecutor()
    val frames: ExecutorService = Executors.newSingleThreadExecutor()
}
