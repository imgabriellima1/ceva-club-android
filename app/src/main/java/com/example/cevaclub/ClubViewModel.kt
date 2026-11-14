package com.example.cevaclub

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cevaclub.data.ClubStore
import com.example.cevaclub.data.Plan
import java.util.concurrent.Executors

internal class ClubViewModel(application: Application) : AndroidViewModel(application) {
    enum class Event { LOADED, LOGIN, REGISTERED, DUPLICATE, INVALID_LOGIN, QUIZ, SUBSCRIBED, CANCELLED, LOGOUT, ERROR }
    data class Update(
        val busy: Boolean = false,
        val ready: Boolean = true,
        val member: ClubStore.Member? = null,
        val event: Event? = null,
    )

    private val store = ClubStore.get(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(Update(ready = false))
    val state: LiveData<Update> = mutableState

    fun load() {
        if (mutableState.value?.ready != true) run { Update(member = store.currentMember(), event = Event.LOADED) }
    }

    fun login(email: String, password: String) = run {
        val member = store.login(email, password)
        Update(member = member, event = if (member == null) Event.INVALID_LOGIN else Event.LOGIN)
    }

    fun register(name: String, email: String, password: String) = run {
        val event = if (store.register(name, email, password)) Event.REGISTERED else Event.DUPLICATE
        Update(event = event)
    }

    fun finishQuiz(answers: List<Int>) = run { Update(member = store.saveQuiz(answers), event = Event.QUIZ) }
    fun subscribe(plan: Plan) = run { Update(member = store.subscribe(plan), event = Event.SUBSCRIBED) }
    fun cancel() = run { Update(member = store.cancel(), event = Event.CANCELLED) }

    fun logout() = run {
        store.logout()
        Update(event = Event.LOGOUT)
    }

    fun consumeEvent() {
        val update = mutableState.value ?: return
        mutableState.value = update.copy(event = null)
    }

    private fun run(operation: () -> Update) {
        val previous = mutableState.value ?: Update()
        if (previous.busy) return
        mutableState.value = previous.copy(busy = true, event = null)
        worker.execute {
            val next = try {
                operation()
            } catch (error: Exception) {
                Log.w("CevaClub", "Falha ao processar dados locais", error)
                previous.copy(event = Event.ERROR)
            }
            mutableState.postValue(next)
        }
    }

    override fun onCleared() {
        worker.shutdown()
        super.onCleared()
    }
}
