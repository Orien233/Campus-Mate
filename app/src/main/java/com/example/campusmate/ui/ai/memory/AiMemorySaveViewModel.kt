package com.example.campusmate.ui.ai.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft
import com.example.campusmate.data.repository.AiMemoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps one user-initiated save alive across configuration changes, without retaining an Activity. */
class AiMemorySaveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AiMemoryRepository(application)
    private val mutableState = MutableLiveData(State.IDLE)
    val state: LiveData<State> = mutableState

    val hasUnconfirmedSave: Boolean
        get() = state.value in setOf(State.SAVING, State.SAVED, State.UNCERTAIN)

    /** A fresh ViewModel plus a pending marker means process recreation, not a retry request. */
    fun restorePendingSave(wasPending: Boolean) {
        if (wasPending && mutableState.value == State.IDLE) {
            mutableState.value = State.UNCERTAIN
        }
    }

    fun save(memoryId: Long, draft: AiMemoryDraft) {
        if (mutableState.value !in setOf(State.IDLE, State.FAILED, State.LIMIT)) return
        // Set synchronously before launching so a second click cannot start another insert.
        mutableState.value = State.SAVING
        viewModelScope.launch {
            mutableState.value = try {
                withContext(Dispatchers.IO) {
                    when {
                        memoryId > 0L -> if (repository.updateUserMemory(memoryId, draft)) State.SAVED else State.FAILED
                        repository.getMemoryCount() >= AiMemory.MAX_MEMORY_COUNT -> State.LIMIT
                        repository.addUserMemory(draft) > 0L -> State.SAVED
                        else -> State.FAILED
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                State.FAILED
            }
        }
    }

    enum class State { IDLE, SAVING, SAVED, FAILED, LIMIT, UNCERTAIN }
}
