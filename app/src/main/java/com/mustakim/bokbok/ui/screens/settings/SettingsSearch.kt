package com.mustakim.bokbok.ui.screens.settings

fun filterQuickActions(
    actions: List<SettingsQuickAction>,
    query: String,
): List<SettingsQuickAction> {
    if (query.isBlank()) return actions
    return actions.filter { it.label.contains(query, ignoreCase = true) }
}

fun filterSettingsGroups(
    groups: List<SettingsGroup>,
    query: String,
): List<SettingsGroup> {
    if (query.isBlank()) return groups
    return groups.mapNotNull { group ->
        if (group.title.contains(query, ignoreCase = true)) {
            group
        } else {
            val filtered = group.items.filter { matchesGroupItem(it, query) }
            if (filtered.isEmpty()) null else group.copy(items = filtered)
        }
    }
}

fun matchesGroupItem(
    item: SettingsGroupItem,
    query: String,
): Boolean = when (item) {
    is SettingsGroupItem.Nav -> matchesQuery(item.item, query)
    is SettingsGroupItem.Toggle -> {
        item.title.contains(query, ignoreCase = true) ||
            item.subtitle?.contains(query, ignoreCase = true) == true
    }
}

fun matchesQuery(
    item: SettingsItem,
    query: String,
): Boolean {
    if (item.title.contains(query, ignoreCase = true)) return true
    if (item.subtitle?.contains(query, ignoreCase = true) == true) return true
    if (item.badge?.contains(query, ignoreCase = true) == true) return true
    return item.keywords.any { keyword ->
        keyword.contains(query, ignoreCase = true) ||
            query.contains(keyword, ignoreCase = true)
    }
}
