// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language override. The absence of a saved tag means "follow system".
 *
 * To add a translation, create res/values-xx/strings.xml and add its tag to
 * R.array.app_languages; the picker builds its labels from the locales.
 */
object AppLanguage {
  private const val PREFS = "language_prefs"
  private const val KEY = "language_tag"

  fun selectedTag(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(KEY, null)
      ?.takeIf { it.isNotBlank() }

  fun setSelectedTag(context: Context, tag: String?) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit()
      .apply {
        if (tag.isNullOrBlank()) remove(KEY) else putString(KEY, tag)
      }
      .apply()
  }

  /** Wraps [context] with the saved locale, or returns it unchanged. */
  fun wrap(context: Context): Context {
    val tag = selectedTag(context) ?: return context
    val locale = Locale.forLanguageTag(tag)
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration)
  }
}
