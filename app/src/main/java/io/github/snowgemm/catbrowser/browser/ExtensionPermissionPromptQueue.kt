package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.WebExtension

internal class ExtensionPermissionPromptQueue {
    private data class Pending(
        val extensionId: String,
        val prompt: ExtensionPermissionPrompt,
        val decision: (Boolean, Boolean) -> Unit,
    )

    private val queue = ExtensionPromptQueue<Pending>()

    val activePrompt: ExtensionPermissionPrompt?
        get() = queue.active?.prompt

    fun enqueue(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
        kind: ExtensionPermissionPrompt.Kind,
        decision: (Boolean, Boolean) -> Unit,
    ): ExtensionPermissionPrompt? {
        val prompt =
            Pending(
                extensionId = extension.id,
                prompt =
                    ExtensionPermissionPrompt(
                        extensionName = extension.metaData.name?.takeIf(String::isNotBlank) ?: extension.id,
                        description = extension.metaData.description.orEmpty(),
                        permissions = permissions.toList(),
                        origins = origins.toList(),
                        dataCollectionPermissions = dataCollectionPermissions.toList(),
                        kind = kind,
                    ),
                decision = decision,
            )
        return queue.enqueue(prompt)?.prompt
    }

    fun resolve(
        approved: Boolean,
        allowDataCollection: Boolean,
    ): ExtensionPermissionPrompt? {
        val current = queue.active ?: return null
        queue.resolveActive()
        runCatching { current.decision(approved, allowDataCollection) }
        return queue.active?.prompt
    }

    fun reject(extensionId: String) {
        val rejected = queue.removeAll { it.extensionId == extensionId }
        rejected.forEach { pending ->
            runCatching { pending.decision(false, false) }
        }
    }
}
