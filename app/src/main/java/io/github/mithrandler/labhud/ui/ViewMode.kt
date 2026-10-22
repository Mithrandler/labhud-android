package io.github.mithrandler.labhud.ui

/** What this phone opens as: the server's choice for its role, or its own. */
enum class ViewChoice { Auto, Glance, Actions, Full }

/**
 * How much the app shows (labhud `[roles.<role>] view`): Glance is compact tiles and no buttons,
 * Actions the normal cards with their buttons, Full the same (settings are always one tap away,
 * so a view never locks anyone out).
 */
enum class ViewMode { Glance, Actions, Full }

private fun parse(s: String?): ViewMode? = when (s) {
    "glance" -> ViewMode.Glance
    "actions" -> ViewMode.Actions
    "full" -> ViewMode.Full
    else -> null
}

/** The phone's own choice wins; Auto follows what the server says for the role (`view` in the state), else Full. */
fun effectiveView(choice: ViewChoice, serverView: String?): ViewMode = when (choice) {
    ViewChoice.Glance -> ViewMode.Glance
    ViewChoice.Actions -> ViewMode.Actions
    ViewChoice.Full -> ViewMode.Full
    ViewChoice.Auto -> parse(serverView) ?: ViewMode.Full
}

/** Wall mode shows the glance page when this screen's profile says so, or the server's view for the role is glance. */
fun wallGlance(profileGlance: Boolean, serverView: String?): Boolean = profileGlance || parse(serverView) == ViewMode.Glance

/** The cards' buttons show unless the view is glance. */
fun ViewMode.buttons(): Boolean = this != ViewMode.Glance
