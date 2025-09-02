package org.jetbrains.bazel.action.registered

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import javax.swing.JComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.action.SuspendableAction
import org.jetbrains.bazel.assets.BazelPluginIcons
import org.jetbrains.bazel.config.BazelPluginBundle

/**
 * Action that opens a dialog for configuring X-specific settings.
 * Similar to OpenBazelQueryToolWindowAction but opens a configuration popup instead.
 */
class OpenXSettingsDialogAction :
    SuspendableAction(
        BazelPluginBundle.message("action.open.x.settings.dialog.text"),
        BazelPluginIcons.x,
    ) {

    override suspend fun actionPerformed(project: Project, e: AnActionEvent) {
        withContext(Dispatchers.EDT) {
            XSettingsDialog(project).show()
        }
    }

    /**
     * Dialog for configuring X-specific settings.
     */
    private class XSettingsDialog(private val project: Project) : DialogWrapper(project) {

        // Registry keys
        private val indexAllKey = "bazel.x.index.all"
        private val targetLimitKey = "bazel.x.target.limit"
        private val pythonTargetsLimitKey = "bazel.python.targets.upper.limit"

        // Current values
        private var indexAll = Registry.`is`(indexAllKey)
        private var targetLimit = Registry.intValue(targetLimitKey, 12111).toString()
        private var pythonTargetsLimit = Registry.intValue(pythonTargetsLimitKey, 929).toString()

        init {
            title = BazelPluginBundle.message("dialog.x.settings.title")
            init()
        }

        override fun createCenterPanel(): JComponent {
            return panel {
                group("Index Settings") {
                    row {
                        checkBox("Index all source code for full-text search")
                            .bindSelected({ indexAll }, { indexAll = it })
                    }
                    row {
                        comment(
                            """
                                    If this is `false` and one specifies `directories` in .bazelproject, intellij will 
                                    only index the specified directories for full text search -- useful for laser focus
                                    on specific directories. 
                                    If this is `true` intellij will index all source code for full text search to be able
                                    to work with the entire monorepo -- indexing will take more time during `arc feature`
                                    when picking up changes from master
                                    """.trimIndent()
                        )
                    }
                }

                group("Safeguard Limits for Bazel Import") {
                    row {
                        comment(
                            """
                                    When importing too many targets, intellij can start to freeze, 
                                    below are reasonable limits for importing. 
                                    With intellij updates the problem may go away, feel free to try different values. 
                                    Please share how it goes into #devex-support
                                    """.trimIndent()
                        )
                    }

                    row("Targets import limit:") {
                        textField()
                            .bindText({ targetLimit }, { targetLimit = it })
                            .comment("Default: 12111")
                    }

                    row("Python targets import limit:") {
                        textField()
                            .bindText({ pythonTargetsLimit }, { pythonTargetsLimit = it })
                            .comment("Default: 929")
                    }
                }
            }
        }

        override fun doOKAction() {
            applyFields()
            // Save settings to registry
            val registryIndexAll = Registry.get(indexAllKey)
            registryIndexAll.setValue(indexAll)

            try {
                val targetLimitValue = targetLimit.toInt()
                val registryTargetLimit = Registry.get(targetLimitKey)
                registryTargetLimit.setValue(targetLimitValue)
            } catch (e: NumberFormatException) {
                // Ignore invalid input and keep previous value
            }

            try {
                val pythonTargetsLimitValue = pythonTargetsLimit.toInt()
                val registryPythonTargetsLimit = Registry.get(pythonTargetsLimitKey)
                registryPythonTargetsLimit.setValue(pythonTargetsLimitValue)
            } catch (e: NumberFormatException) {
                // Ignore invalid input and keep previous value
            }

            super.doOKAction()
        }
    }
}