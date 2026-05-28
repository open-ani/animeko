package me.him188.ani.app.data.persistent.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import me.him188.ani.app.platform.Context
import org.w3c.dom.Worker

actual fun Context.createDatabaseBuilder(): RoomDatabase.Builder<AniDatabase> {
    return Room.databaseBuilder<AniDatabase>(name = "ani_room_database_main.db")
}

actual fun Context.createDatabaseDriver(): SQLiteDriver = WebWorkerSQLiteDriver(createSQLiteWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSQLiteWorker(): Worker =
    js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
