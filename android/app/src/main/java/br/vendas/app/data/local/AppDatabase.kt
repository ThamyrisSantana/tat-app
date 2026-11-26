package br.vendas.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local copy of the server data. It holds nothing that is not on the server,
 * so a schema change simply drops it and the next sync fills it again.
 */
@Database(
    entities = [
        CustomerEntity::class,
        ProductEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        SyncInfoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun salesDao(): SalesDao

    companion object {
        const val NAME = "vendas.db"
    }
}
