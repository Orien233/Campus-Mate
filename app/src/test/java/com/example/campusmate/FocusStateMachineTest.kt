package com.example.campusmate

import com.example.campusmate.domain.focus.FocusEvent
import com.example.campusmate.domain.focus.FocusState
import com.example.campusmate.domain.focus.FocusStateMachine
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusStateMachineTest {
    @Test
    fun stateMachine_handlesFaceDownPickupAndFinishFlow() {
        val machine = FocusStateMachine()

        assertEquals(FocusState.READY_WAITING_FACE_DOWN, machine.dispatch(FocusEvent.PREPARE))
        assertEquals(FocusState.RUNNING, machine.dispatch(FocusEvent.FACE_DOWN_STABLE))
        assertEquals(FocusState.PAUSED_BY_PICKUP, machine.dispatch(FocusEvent.PICKED_UP))
        assertEquals(FocusState.RUNNING, machine.dispatch(FocusEvent.FACE_DOWN_STABLE))
        assertEquals(FocusState.FINISHED, machine.dispatch(FocusEvent.FINISH))
        assertEquals(FocusState.FINISHED, machine.dispatch(FocusEvent.CANCEL))
    }

    @Test
    fun stateMachine_keepsIllegalTransitionsStable() {
        val machine = FocusStateMachine()

        assertEquals(FocusState.IDLE, machine.dispatch(FocusEvent.FINISH))
        assertEquals(FocusState.READY_WAITING_FACE_DOWN, machine.dispatch(FocusEvent.PREPARE))
        assertEquals(FocusState.READY_WAITING_FACE_DOWN, machine.dispatch(FocusEvent.PICKED_UP))
        assertEquals(FocusState.CANCELLED, machine.dispatch(FocusEvent.CANCEL))
    }
}
