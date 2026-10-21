plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied at the root for the git hook tasks (addKtlintFormatGitPreCommitHook).
    alias(libs.plugins.ktlint)
}
