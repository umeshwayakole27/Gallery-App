package com.uw.simplegallery.viewmodel.coordinator

import android.content.IntentSender
import com.uw.simplegallery.data.repository.MediaManager

internal class GalleryDeleteCoordinator(
    private val mediaCoordinator: GalleryMediaCoordinator
) {
    suspend fun delete(ids: List<Long>): DeleteOutcome {
        return when (val result = mediaCoordinator.deleteMediaItems(ids)) {
            is MediaManager.DeleteResult.Success -> DeleteOutcome.Completed
            is MediaManager.DeleteResult.Failure -> DeleteOutcome.Failed
            is MediaManager.DeleteResult.RequiresConfirmation -> {
                DeleteOutcome.RequiresConfirmation(
                    intentSender = result.intentSender,
                    pendingIds = result.pendingIds
                )
            }
        }
    }

    suspend fun handleConfirmation(approved: Boolean, ids: List<Long>): DeleteOutcome {
        if (!approved) {
            return DeleteOutcome.Cancelled
        }
        mediaCoordinator.removeDeletedItemsFromCache(ids)
        return DeleteOutcome.Completed
    }

    sealed class DeleteOutcome {
        data object Completed : DeleteOutcome()
        data object Failed : DeleteOutcome()
        data object Cancelled : DeleteOutcome()
        data class RequiresConfirmation(
            val intentSender: IntentSender,
            val pendingIds: List<Long>
        ) : DeleteOutcome()
    }
}
