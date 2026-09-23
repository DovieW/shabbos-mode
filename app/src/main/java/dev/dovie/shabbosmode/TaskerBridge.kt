package dev.dovie.shabbosmode

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

@TaskerInputRoot
class ShabbosEventInput

@TaskerOutputObject
class ShabbosEventOutput(
    @get:TaskerOutputVariable("shabbosevent", R.string.tasker_event)
    val event: String
)

@TaskerInputRoot
class ShabbosEventUpdate @JvmOverloads constructor(
    @field:TaskerInputField("event") var event: String? = null
)

class ShabbosEventRunner :
    TaskerPluginRunnerConditionEvent<ShabbosEventInput, ShabbosEventOutput, ShabbosEventUpdate>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<ShabbosEventInput>,
        update: ShabbosEventUpdate?
    ): TaskerPluginResultCondition<ShabbosEventOutput> =
        if (update?.event.isNullOrBlank()) TaskerPluginResultConditionUnsatisfied()
        else TaskerPluginResultConditionSatisfied(context, ShabbosEventOutput(update.event!!))
}

class ShabbosEventHelper(config: TaskerPluginConfig<ShabbosEventInput>) :
    TaskerPluginConfigHelper<ShabbosEventInput, ShabbosEventOutput, ShabbosEventRunner>(config) {
    override val runnerClass = ShabbosEventRunner::class.java
    override val inputClass = ShabbosEventInput::class.java
    override val outputClass = ShabbosEventOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<ShabbosEventInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Selected Shabbos Mode times")
    }
}

class TaskerEventConfigActivity : Activity(), TaskerPluginConfig<ShabbosEventInput> {
    override val context: Context get() = applicationContext
    override val inputForTasker get() = TaskerInput(ShabbosEventInput())
    override fun assignFromInput(input: TaskerInput<ShabbosEventInput>) = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShabbosEventHelper(this).finishForTasker()
    }
}

object TaskerBridge {
    fun fire(context: Context, key: String) {
        TaskerEventConfigActivity::class.java.requestQuery(context, ShabbosEventUpdate(key))
    }
}
