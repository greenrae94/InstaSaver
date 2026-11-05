package com.raegreen.instasaver

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object ItemActions {

    // Deleted needs no further action; NeedsConfirmation carries the IntentSender Android supplies
    // for a file this app does not own; Failed covers a delete that removed nothing and any
    // exception that is not the SecurityException confirmation flow expects
    sealed interface DeleteResult {
        object Deleted : DeleteResult
        data class NeedsConfirmation(val intentSender: IntentSender) : DeleteResult
        object Failed : DeleteResult
    }

    // No package set deliberately: Instagram claims its own links, so this lands in the app
    // where the owner is already signed in, and falls back to a browser if it is not installed
    fun openPostIntent(postUrl: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(postUrl))

    fun shareIntent(item: SavedItem): Intent = Intent(Intent.ACTION_SEND).apply {
        type = item.type.mimeType
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Files this app saved delete outright. Files it does not own (the owner's older downloads)
    // throw, and Android supplies a confirmation the user must approve — returned for the caller
    // to launch. A delete that removes zero rows, or any other failure, is reported rather than
    // treated as success.
    fun deleteOrRequest(context: Context, uri: Uri): DeleteResult = try {
        val rowsDeleted = context.contentResolver.delete(uri, null, null)

        if (rowsDeleted > 0) DeleteResult.Deleted else DeleteResult.Failed
    } catch (error: SecurityException) {
        val intentSender = (error as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            ?: MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender

        DeleteResult.NeedsConfirmation(intentSender)
    } catch (error: Exception) {
        Log.d("InstaSaver", "delete failed for $uri: $error")

        DeleteResult.Failed
    }
}
