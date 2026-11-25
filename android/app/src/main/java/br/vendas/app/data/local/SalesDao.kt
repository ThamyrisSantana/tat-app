package br.vendas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Every table of the local copy, read and replaced together. */
data class LocalData(
    val customers: List<CustomerEntity>,
    val products: List<ProductEntity>,
    val purchases: List<PurchaseEntity>,
    val items: List<PurchaseItemEntity>,
    val installments: List<InstallmentEntity>,
)

@Dao
abstract class SalesDao {

    @Query("SELECT * FROM customers")
    protected abstract suspend fun customers(): List<CustomerEntity>

    @Query("SELECT * FROM products")
    protected abstract suspend fun products(): List<ProductEntity>

    @Query("SELECT * FROM purchases ORDER BY sort_order")
    protected abstract suspend fun purchases(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items")
    protected abstract suspend fun items(): List<PurchaseItemEntity>

    @Query("SELECT * FROM installments ORDER BY number")
    protected abstract suspend fun installments(): List<InstallmentEntity>

    @Query("SELECT * FROM sync_info WHERE id = 0")
    abstract suspend fun syncInfo(): SyncInfoEntity?

    @Transaction
    open suspend fun load(): LocalData = LocalData(customers(), products(), purchases(), items(), installments())

    /** Swaps the whole local copy for [data] in one transaction. */
    @Transaction
    open suspend fun replaceAll(data: LocalData, syncInfo: SyncInfoEntity) {
        clearTables()
        insertCustomers(data.customers)
        insertProducts(data.products)
        insertPurchases(data.purchases)
        insertItems(data.items)
        insertInstallments(data.installments)
        insertSyncInfo(syncInfo)
    }

    @Transaction
    open suspend fun clearAll() {
        clearTables()
        deleteSyncInfo()
    }

    private suspend fun clearTables() {
        deleteInstallments()
        deleteItems()
        deletePurchases()
        deleteProducts()
        deleteCustomers()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCustomers(rows: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProducts(rows: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPurchases(rows: List<PurchaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(rows: List<PurchaseItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertInstallments(rows: List<InstallmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSyncInfo(row: SyncInfoEntity)

    @Query("DELETE FROM customers")
    protected abstract suspend fun deleteCustomers()

    @Query("DELETE FROM products")
    protected abstract suspend fun deleteProducts()

    @Query("DELETE FROM purchases")
    protected abstract suspend fun deletePurchases()

    @Query("DELETE FROM purchase_items")
    protected abstract suspend fun deleteItems()

    @Query("DELETE FROM installments")
    protected abstract suspend fun deleteInstallments()

    @Query("DELETE FROM sync_info")
    protected abstract suspend fun deleteSyncInfo()
}
