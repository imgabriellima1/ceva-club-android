package com.example.cevaclub

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.example.cevaclub.ClubViewModel.Event
import com.example.cevaclub.data.BeerQuiz
import com.example.cevaclub.data.BeerProfile
import com.example.cevaclub.data.ClubStore
import com.example.cevaclub.data.Plan
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private enum class Screen(val layout: Int, val title: Int) {
        LOGIN(R.layout.screen_auth, R.string.login_title),
        REGISTER(R.layout.screen_auth, R.string.register_title),
        HOME(R.layout.screen_home, R.string.app_name),
        QUIZ(R.layout.screen_quiz, R.string.quiz),
        RESULT(R.layout.screen_result, R.string.quiz_result),
        PLANS(R.layout.screen_plans, R.string.plans),
        SUBSCRIPTION(R.layout.screen_subscription, R.string.subscription),
        PROFILE(R.layout.screen_profile, R.string.profile),
    }

    private lateinit var model: ClubViewModel
    private lateinit var container: FrameLayout
    private lateinit var content: View
    private var current: Screen? = null
    private var member: ClubStore.Member? = null
    private var busy = false
    private var initialized = false
    private var restoredEmail = ""
    private var restoredName = ""
    private val history = mutableListOf<Screen>()
    private val answers = IntArray(BeerQuiz.questions.size) { -1 }
    private var questionIndex = 0
    private val optionIds = listOf(R.id.option_1, R.id.option_2, R.id.option_3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        savedInstanceState?.let { state ->
            current = state.getString("screen")?.let(Screen::valueOf)
            state.getStringArrayList("history")?.mapTo(history, Screen::valueOf)
            state.getIntArray("answers")?.copyInto(answers)
            questionIndex = state.getInt("question")
            restoredEmail = state.getString("email").orEmpty()
            restoredName = state.getString("name").orEmpty()
        }
        findViewById<View>(R.id.back).setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        model = ViewModelProvider(this)[ClubViewModel::class.java]
        model.state.observe(this, ::update)
        findViewById<ProgressBar>(R.id.loading).isInvisible = false
        model.load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        current?.let { outState.putString("screen", it.name) }
        outState.putStringArrayList("history", ArrayList(history.map(Screen::name)))
        if (current == Screen.QUIZ && ::content.isInitialized) recordAnswer()
        outState.putIntArray("answers", answers.copyOf())
        outState.putInt("question", questionIndex)
        if (::content.isInitialized && (current == Screen.LOGIN || current == Screen.REGISTER)) {
            outState.putString("email", field(R.id.email).text.toString())
            outState.putString("name", field(R.id.full_name).text.toString())
        } else if (!::content.isInitialized) {
            outState.putString("email", restoredEmail)
            outState.putString("name", restoredName)
        }
    }

    private fun update(state: ClubViewModel.Update) {
        busy = state.busy
        findViewById<ProgressBar>(R.id.loading).isInvisible = !busy
        member = state.member
        val event = state.event
        if (!initialized) {
            if (!state.ready && event == Event.ERROR) {
                model.consumeEvent()
                MaterialAlertDialogBuilder(this).setMessage(R.string.storage_error)
                    .setPositiveButton(R.string.retry) { _, _ -> model.load() }
                    .setOnCancelListener { finish() }.show()
                return
            }
            if (!state.ready) return
            initialized = true
            val restored = current
            val destination = when {
                member == null && restored == Screen.REGISTER -> Screen.REGISTER
                member == null -> Screen.LOGIN
                restored == null || restored == Screen.LOGIN || restored == Screen.REGISTER -> Screen.HOME
                restored == Screen.RESULT && member?.beerProfile == null -> Screen.HOME
                else -> restored
            }
            if (member == null || destination == Screen.HOME) history.clear()
            show(destination)
        }
        if (event != null) {
            when (event) {
                Event.LOGIN -> resetTo(Screen.HOME)
                Event.REGISTERED -> {
                    restoredEmail = field(R.id.email).text.toString()
                    resetTo(Screen.LOGIN)
                    toast(R.string.account_created)
                }
                Event.DUPLICATE -> setError(field(R.id.email), R.string.duplicate_email)
                Event.INVALID_LOGIN -> setError(field(R.id.password), R.string.incorrect_login)
                Event.QUIZ -> {
                    if (current == Screen.QUIZ) show(Screen.RESULT) else navigate(Screen.RESULT)
                }
                Event.SUBSCRIBED -> {
                    history.clear()
                    history.add(Screen.HOME)
                    show(Screen.SUBSCRIPTION)
                    toast(R.string.subscription_saved)
                }
                Event.CANCELLED -> {
                    show(Screen.SUBSCRIPTION)
                    toast(R.string.cancellation_saved)
                }
                Event.LOGOUT -> {
                    restoredEmail = ""
                    restoredName = ""
                    answers.fill(-1)
                    questionIndex = 0
                    resetTo(Screen.LOGIN)
                }
                Event.ERROR -> toast(R.string.storage_error)
                Event.LOADED -> Unit
            }
            model.consumeEvent()
        }
    }

    private fun resetTo(screen: Screen) {
        history.clear()
        show(screen)
    }

    private fun navigate(screen: Screen) {
        val originatingScreen = current
        if (originatingScreen != null && originatingScreen != screen) history.add(originatingScreen)
        show(screen)
    }

    private fun goBack() {
        if (busy) return
        if (current == Screen.QUIZ && questionIndex > 0) {
            recordAnswer()
            questionIndex--
            show(Screen.QUIZ)
            return
        }
        val previous = history.removeLastOrNull()
        when {
            previous != null -> show(previous)
            current == Screen.REGISTER -> show(Screen.LOGIN)
            current != null && current != Screen.LOGIN && current != Screen.HOME -> show(Screen.HOME)
            else -> finish()
        }
    }

    private fun show(screen: Screen) {
        current = screen
        container.removeAllViews()
        content = layoutInflater.inflate(screen.layout, container, false)
        container.addView(content)
        val scroll = findViewById<NestedScrollView>(R.id.scroll)
        scroll.scrollTo(0, 0)
        val back = findViewById<MaterialButton>(R.id.back)
        back.isInvisible = screen == Screen.LOGIN || screen == Screen.HOME
        ViewCompat.setAccessibilityPaneTitle(content, getString(screen.title))
        when (screen) {
            Screen.LOGIN, Screen.REGISTER -> bindAuth(screen == Screen.REGISTER)
            Screen.HOME -> bindHome(requireNotNull(member))
            Screen.QUIZ -> bindQuiz()
            Screen.RESULT -> bindResult(requireNotNull(member?.beerProfile))
            Screen.PLANS -> bindPlans(requireNotNull(member))
            Screen.SUBSCRIPTION -> bindSubscription(requireNotNull(member))
            Screen.PROFILE -> bindProfile(requireNotNull(member))
        }
    }

    private fun bindAuth(registering: Boolean) {
        val name = field(R.id.full_name)
        val email = field(R.id.email)
        val password = field(R.id.password)
        text(R.id.auth_title).setText(if (registering) R.string.register_title else R.string.login_title)
        inputLayout(name).isVisible = registering
        inputLayout(password).hint = getString(if (registering) R.string.new_password else R.string.password)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            password.setAutofillHints(if (registering) "newPassword" else "password")
        }
        name.setText(restoredName)
        email.setText(restoredEmail)
        restoredName = ""
        restoredEmail = ""
        button(R.id.auth_toggle, if (registering) R.string.go_to_login else R.string.register) {
            restoredEmail = email.text.toString()
            hideKeyboard()
            show(if (registering) Screen.LOGIN else Screen.REGISTER)
        }
        button(R.id.auth_submit, if (registering) R.string.register else R.string.login) {
            val nameValue = name.text.toString().trim()
            val emailValue = email.text.toString().trim().lowercase(Locale.ROOT)
            val passwordValue = password.text.toString()
            listOf(name, email, password).forEach { inputLayout(it).error = null }
            when {
                registering && nameValue.isBlank() -> setError(name, R.string.required_name)
                !Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() -> setError(email, R.string.invalid_email)
                passwordValue.isBlank() -> setError(password, R.string.required_password)
                registering && passwordValue.length < 8 -> setError(password, R.string.short_password)
                else -> {
                    hideKeyboard()
                    if (registering) model.register(nameValue, emailValue, passwordValue)
                    else model.login(emailValue, passwordValue)
                }
            }
        }
    }

    private fun bindHome(account: ClubStore.Member) {
        text(R.id.greeting).text = getString(R.string.greeting, account.name.substringBefore(' '))
        val profile = account.beerProfile
        text(R.id.home_quiz_status).text = if (profile == null) getString(R.string.quiz_invite)
            else getString(R.string.profile_status, profile.title)
        button(R.id.open_quiz, if (profile == null) R.string.open_quiz else R.string.view_result) {
            if (profile == null) startQuiz() else navigate(Screen.RESULT)
        }
        button(R.id.view_plans) { navigate(Screen.PLANS) }
        button(R.id.open_subscription) { navigate(Screen.SUBSCRIPTION) }
        button(R.id.open_profile) { navigate(Screen.PROFILE) }
    }

    private fun startQuiz() {
        answers.fill(-1)
        questionIndex = 0
        navigate(Screen.QUIZ)
    }

    private fun bindQuiz() {
        val question = BeerQuiz.questions[questionIndex]
        text(R.id.quiz_progress).text = getString(R.string.quiz_progress, questionIndex + 1, answers.size)
        content.findViewById<LinearProgressIndicator>(R.id.quiz_progress_bar).progress = questionIndex + 1
        text(R.id.quiz_question).text = question.title
        val group = content.findViewById<RadioGroup>(R.id.quiz_options)
        optionIds.forEachIndexed { index, id ->
            content.findViewById<RadioButton>(id).text = question.options[index]
        }
        optionIds.getOrNull(answers[questionIndex])?.let(group::check)
        group.setOnCheckedChangeListener { _, _ -> text(R.id.quiz_error).isVisible = false }
        val lastQuestion = questionIndex == answers.lastIndex
        button(R.id.quiz_next, if (lastQuestion) R.string.view_result else R.string.next) {
            recordAnswer()
            when {
                answers[questionIndex] < 0 -> text(R.id.quiz_error).isVisible = true
                lastQuestion -> model.finishQuiz(answers.toList())
                else -> {
                    questionIndex++
                    show(Screen.QUIZ)
                }
            }
        }
        button(R.id.quiz_previous) { goBack() }.isVisible = questionIndex > 0
    }

    private fun recordAnswer() {
        val group = content.findViewById<RadioGroup>(R.id.quiz_options)
        answers[questionIndex] = optionIds.indexOf(group.checkedRadioButtonId)
    }

    private fun bindResult(profile: BeerProfile) {
        text(R.id.result_title).text = profile.title
        text(R.id.result_description).text = profile.description
        text(R.id.recommended_plan).text = getString(R.string.recommended_plan, profile.suggestedPlan.title)
        button(R.id.view_plans) { navigate(Screen.PLANS) }
        button(R.id.open_quiz) { startQuiz() }
    }

    private fun bindPlans(account: ClubStore.Member) {
        val list = content.findViewById<LinearLayout>(R.id.plan_list)
        Plan.entries.forEach { plan ->
            val card = layoutInflater.inflate(R.layout.item_plan, list, false)
            list.addView(card)
            card.findViewById<TextView>(R.id.plan_title).text = plan.title
            card.findViewById<TextView>(R.id.plan_price).text = getString(R.string.monthly_price, plan.price())
            card.findViewById<TextView>(R.id.plan_benefits).text = plan.benefits
            val action = card.findViewById<MaterialButton>(R.id.select_plan)
            if (account.active && account.plan == plan) {
                action.setText(R.string.current_plan)
                action.isEnabled = false
            } else {
                action.contentDescription = getString(R.string.select_named_plan, plan.title)
                action.setOnClickListener {
                    if (!busy) confirmPlan(plan, account.plan?.takeIf { account.active })
                }
            }
        }
    }

    private fun confirmPlan(plan: Plan, activePlan: Plan?) {
        val title = getString(if (activePlan == null) R.string.confirm_plan_title else R.string.replace_plan_title, plan.title)
        val message = if (activePlan == null) getString(R.string.confirm_plan_message, plan.price())
            else getString(R.string.replace_plan_message, activePlan.title, plan.price())
        confirm(title, message) { model.subscribe(plan) }
    }

    private fun bindSubscription(account: ClubStore.Member) {
        val plan = account.plan
        text(R.id.subscription_title).text = plan?.title ?: getString(R.string.subscription_empty)
        text(R.id.subscription_price).text = if (plan == null) getString(R.string.subscription_empty_message)
            else getString(R.string.monthly_price, plan.price())
        text(R.id.subscription_status).apply {
            isVisible = plan != null
            setText(if (account.active) R.string.subscription_active else R.string.subscription_cancelled)
        }
        button(R.id.view_plans, if (account.active) R.string.change_plan else R.string.view_plans) {
            navigate(Screen.PLANS)
        }
        button(R.id.cancel_subscription) {
            confirm(getString(R.string.cancel_title), getString(R.string.cancel_message)) { model.cancel() }
        }.isVisible = account.active
    }

    private fun bindProfile(account: ClubStore.Member) {
        text(R.id.member_name).text = account.name
        text(R.id.member_email).text = account.email
        val profile = account.beerProfile
        text(R.id.profile_status).text = if (profile == null) getString(R.string.profile_pending)
            else getString(R.string.profile_status, profile.title)
        button(R.id.open_result) { navigate(Screen.RESULT) }.isVisible = profile != null
        button(R.id.open_quiz, if (profile == null) R.string.open_quiz else R.string.retake_quiz) { startQuiz() }
        button(R.id.logout) { model.logout() }
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
            .setNegativeButton(R.string.dismiss, null)
            .setPositiveButton(R.string.confirm) { _, _ -> if (!busy) action() }
            .show()
    }

    private fun button(id: Int, label: Int? = null, action: () -> Unit): MaterialButton =
        content.findViewById<MaterialButton>(id).apply {
            label?.let(::setText)
            setOnClickListener { if (!busy) action() }
        }

    private fun text(id: Int): TextView = content.findViewById(id)

    private fun field(id: Int): TextInputEditText = content.findViewById(id)

    private fun inputLayout(field: TextInputEditText): TextInputLayout =
        requireNotNull(generateSequence(field.parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull())

    private fun setError(field: TextInputEditText, message: Int) {
        inputLayout(field).error = getString(message)
        field.requestFocus()
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(container.windowToken, 0)
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
