package com.example.domain.backup

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DriveBackupFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String
)

object GoogleDriveService {

    private const val TAG = "GoogleDriveService"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if a folder named [folderName] exists in Drive; if not, creates it.
     * Returns the folder's Google Drive ID.
     */
    suspend fun getOrCreateBackupFolder(accessToken: String, folderName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Search for existing folder
            val query = "name = '$folderName' and mimeType = '$FOLDER_MIME' and trashed = false"
            val searchUrl = "$DRIVE_API_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val searchResponse = httpClient.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string() ?: ""

            if (!searchResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Drive search failed: ${searchResponse.code} $searchBody"))
            }

            val searchJson = JSONObject(searchBody)
            val filesArray = searchJson.optJSONArray("files") ?: JSONArray()
            if (filesArray.length() > 0) {
                val folderId = filesArray.getJSONObject(0).getString("id")
                return@withContext Result.success(folderId)
            }

            // Create new folder
            val folderMetadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", FOLDER_MIME)
            }
            val createRequest = Request.Builder()
                .url(DRIVE_API_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(folderMetadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val createResponse = httpClient.newCall(createRequest).execute()
            val createBody = createResponse.body?.string() ?: ""

            if (!createResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Drive folder creation failed: ${createResponse.code} $createBody"))
            }

            val createJson = JSONObject(createBody)
            val newFolderId = createJson.getString("id")
            Result.success(newFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getOrCreateBackupFolder", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads the full database JSON backup to the specified Drive folder.
     */
    suspend fun uploadBackupFile(
        accessToken: String,
        folderId: String,
        backupJsonString: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "grocery_backup_${sdf.format(Date())}.json"

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
                put("mimeType", "application/json")
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .addPart(
                    backupJsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(DRIVE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Upload failed (${response.code}): $responseBody"))
            }

            val respJson = JSONObject(responseBody)
            val fileId = respJson.optString("id")
            Result.success("Uploaded successfully ($fileName, ID: $fileId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadBackupFile", e)
            Result.failure(e)
        }
    }

    /**
     * Lists all backup JSON files currently inside the Drive backup folder.
     */
    suspend fun listRemoteBackups(accessToken: String, folderId: String): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val query = "'$folderId' in parents and trashed = false"
            val url = "$DRIVE_API_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,size,modifiedTime)&orderBy=modifiedTime desc"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to list files: ${response.code} $responseBody"))
            }

            val json = JSONObject(responseBody)
            val filesArray = json.optJSONArray("files") ?: JSONArray()
            val list = mutableListOf<DriveBackupFile>()
            for (i in 0 until filesArray.length()) {
                val f = filesArray.getJSONObject(i)
                list.add(
                    DriveBackupFile(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        sizeBytes = f.optLong("size", 0L),
                        modifiedTime = f.optString("modifiedTime", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error in listRemoteBackups", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads file content from Drive and restores the database.
     */
    suspend fun downloadAndRestoreBackup(
        accessToken: String,
        fileId: String,
        db: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_API_URL/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }

            BackupManager.validateAndRestoreBackup(responseBody, db)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading and restoring", e)
            Result.failure(e)
        }
    }
}
