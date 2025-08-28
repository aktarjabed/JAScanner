package com.jascanner.network

import com.jascanner.data.entities.DocumentEntity
import retrofit2.Response
import retrofit2.http.*

interface CloudSyncService {
    
    @GET("documents")
    suspend fun getDocuments(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<DocumentListResponse>
    
    @GET("documents/{id}")
    suspend fun getDocument(
        @Header("Authorization") token: String,
        @Path("id") documentId: String
    ): Response<CloudDocument>
    
    @POST("documents")
    suspend fun uploadDocument(
        @Header("Authorization") token: String,
        @Body document: UploadDocumentRequest
    ): Response<CloudDocument>
    
    @PUT("documents/{id}")
    suspend fun updateDocument(
        @Header("Authorization") token: String,
        @Path("id") documentId: String,
        @Body document: UpdateDocumentRequest
    ): Response<CloudDocument>
    
    @DELETE("documents/{id}")
    suspend fun deleteDocument(
        @Header("Authorization") token: String,
        @Path("id") documentId: String
    ): Response<DeleteResponse>
    
    @POST("auth/login")
    suspend fun login(
        @Body credentials: LoginRequest
    ): Response<AuthResponse>
    
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Header("Authorization") token: String,
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>
    
    @GET("sync/status")
    suspend fun getSyncStatus(
        @Header("Authorization") token: String
    ): Response<SyncStatusResponse>
    
    @POST("sync/push")
    suspend fun pushChanges(
        @Header("Authorization") token: String,
        @Body changes: SyncChangesRequest
    ): Response<SyncResponse>
    
    @POST("sync/pull")
    suspend fun pullChanges(
        @Header("Authorization") token: String,
        @Body request: PullChangesRequest
    ): Response<SyncResponse>
}

// Data classes for API requests and responses
data class DocumentListResponse(
    val documents: List<CloudDocument>,
    val totalCount: Int,
    val page: Int,
    val hasMore: Boolean
)

data class CloudDocument(
    val id: String,
    val title: String,
    val textContent: String,
    val filePath: String?,
    val sessionId: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val syncStatus: String,
    val checksum: String
)

data class UploadDocumentRequest(
    val title: String,
    val textContent: String,
    val fileData: String?, // Base64 encoded file data
    val sessionId: String?,
    val checksum: String
)

data class UpdateDocumentRequest(
    val title: String?,
    val textContent: String?,
    val fileData: String?,
    val checksum: String
)

data class DeleteResponse(
    val success: Boolean,
    val message: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserInfo
)

data class UserInfo(
    val id: String,
    val email: String,
    val name: String,
    val plan: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class SyncStatusResponse(
    val lastSyncTime: Long,
    val pendingChanges: Int,
    val conflictCount: Int,
    val storageUsed: Long,
    val storageLimit: Long
)

data class SyncChangesRequest(
    val changes: List<DocumentChange>,
    val lastSyncTime: Long
)

data class PullChangesRequest(
    val lastSyncTime: Long
)

data class SyncResponse(
    val success: Boolean,
    val changes: List<DocumentChange>,
    val conflicts: List<SyncConflict>,
    val newSyncTime: Long
)

data class DocumentChange(
    val documentId: String,
    val changeType: String, // "CREATE", "UPDATE", "DELETE"
    val timestamp: Long,
    val checksum: String,
    val data: CloudDocument?
)

data class SyncConflict(
    val documentId: String,
    val localVersion: CloudDocument,
    val remoteVersion: CloudDocument,
    val conflictType: String
)

// Extension functions for converting between local and cloud entities
fun DocumentEntity.toCloudDocument(): CloudDocument {
    return CloudDocument(
        id = this.id.toString(),
        title = this.title,
        textContent = this.textContent,
        filePath = this.filePath,
        sessionId = this.sessionId?.toString(),
        createdAt = this.createdAt,
        modifiedAt = this.modifiedAt,
        syncStatus = "PENDING",
        checksum = generateChecksum()
    )
}

fun CloudDocument.toDocumentEntity(): DocumentEntity {
    return DocumentEntity(
        id = this.id.toLongOrNull() ?: 0L,
        title = this.title,
        textContent = this.textContent,
        filePath = this.filePath ?: "",
        sessionId = this.sessionId?.toLongOrNull(),
        createdAt = this.createdAt,
        modifiedAt = this.modifiedAt
    )
}

private fun DocumentEntity.generateChecksum(): String {
    val content = "$title$textContent$filePath$createdAt$modifiedAt"
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray())
        .joinToString("") { "%02x".format(it) }
}