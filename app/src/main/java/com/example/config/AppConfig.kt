package com.example.config

import android.content.Context
import com.example.R

/**
 * Centralized dynamic configuration helper.
 * All repository URLs, legal links, and project metadata are resolved dynamically
 * through Android resources so that renaming the repository or changing developer handles
 * only requires updating a single entry in `strings.xml`.
 */
object AppConfig {
    fun getRepoUrl(context: Context): String {
        return context.getString(R.string.repo_url).trimEnd('/')
    }

    fun getPrivacyPolicyUrl(context: Context): String {
        return "${getRepoUrl(context)}/blob/main/PRIVACY_POLICY.md"
    }

    fun getTermsUrl(context: Context): String {
        return "${getRepoUrl(context)}/blob/main/TERMS.md"
    }

    fun getLicenseUrl(context: Context): String {
        return "${getRepoUrl(context)}/blob/main/LICENSE"
    }

    fun getSponsorUrl(context: Context): String {
        return context.getString(R.string.github_sponsor_url)
    }

    fun getCoffeeUrl(context: Context): String {
        return context.getString(R.string.coffee_sponsor_url)
    }

    fun getLiberapayUrl(context: Context): String {
        return context.getString(R.string.liberapay_url)
    }

    fun getDeveloperBio(context: Context): String {
        return context.getString(R.string.developer_bio)
    }
}
