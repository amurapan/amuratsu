package org.skepsun.kototoro.core.ui.text

/** Project destinations shared by Android settings, desktop settings and their search indexes. */
enum class AppProjectLink(
    val preferenceKey: String,
    val tag: String,
    val titleKey: String,
    val urlKey: String,
) {
    MANUAL("about_help", "manual", "user_manual", "url_user_manual"),
    SOURCE("about_github", "github", "source_code", "url_github"),
    ISSUES("about_issues", "issues", "about_report_issue", "url_issues"),
    KOTOTORO("about_upstream_kototoro", "kototoro", "about_upstream_kototoro", "url_upstream_kototoro"),
    KOTATSU("about_upstream_kotatsu", "kotatsu", "about_upstream_kotatsu", "url_upstream_kotatsu"),
}
