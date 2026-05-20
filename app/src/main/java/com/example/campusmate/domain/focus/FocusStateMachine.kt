package com.example.campusmate.domain.focus

/** Centralizes legal focus session state transitions. */
class FocusStateMachine(initialState: FocusState = FocusState.IDLE) {
    var state: FocusState = initialState
        private set

    fun dispatch(event: FocusEvent): FocusState {
        val next = when (state) {
            FocusState.IDLE -> when (event) {
                FocusEvent.PREPARE -> FocusState.READY_WAITING_FACE_DOWN
                FocusEvent.CANCEL -> FocusState.CANCELLED
                else -> null
            }
            FocusState.READY_WAITING_FACE_DOWN -> when (event) {
                FocusEvent.FACE_DOWN_STABLE, FocusEvent.START_MANUAL -> FocusState.RUNNING
                FocusEvent.CANCEL -> FocusState.CANCELLED
                else -> null
            }
            FocusState.RUNNING -> when (event) {
                FocusEvent.PICKED_UP -> FocusState.PAUSED_BY_PICKUP
                FocusEvent.PAUSE_MANUAL -> FocusState.PAUSED_MANUAL
                FocusEvent.FINISH -> FocusState.FINISHED
                FocusEvent.CANCEL -> FocusState.CANCELLED
                else -> null
            }
            FocusState.PAUSED_BY_PICKUP -> when (event) {
                FocusEvent.FACE_DOWN_STABLE -> FocusState.RUNNING
                FocusEvent.PAUSE_MANUAL -> FocusState.PAUSED_MANUAL
                FocusEvent.FINISH -> FocusState.FINISHED
                FocusEvent.CANCEL -> FocusState.CANCELLED
                else -> null
            }
            FocusState.PAUSED_MANUAL -> when (event) {
                FocusEvent.RESUME_MANUAL -> FocusState.RUNNING
                FocusEvent.FINISH -> FocusState.FINISHED
                FocusEvent.CANCEL -> FocusState.CANCELLED
                else -> null
            }
            FocusState.FINISHED,
            FocusState.CANCELLED -> null
        }
        next?.let { state = it }
        return state
    }
}

enum class FocusState {
    IDLE,
    READY_WAITING_FACE_DOWN,
    RUNNING,
    PAUSED_BY_PICKUP,
    PAUSED_MANUAL,
    FINISHED,
    CANCELLED
}

enum class FocusEvent {
    PREPARE,
    FACE_DOWN_STABLE,
    PICKED_UP,
    START_MANUAL,
    PAUSE_MANUAL,
    RESUME_MANUAL,
    FINISH,
    CANCEL
}
