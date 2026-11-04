package io.github.andreygladkov.inkmessages.data

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Loader {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
}
