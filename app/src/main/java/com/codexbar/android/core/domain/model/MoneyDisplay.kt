package com.codexbar.android.core.domain.model

/** Same units and unknown-value wording on dashboard, widgets and notifications. */
fun ReportedMoney.balanceText(): String = "Balance: " + (balance?.let { "$currency ${String.format("%.2f", it)}" } ?: "unavailable")

fun ReportedMoney.spendText(): String = "${period ?: "Reported spend"}: " +
    (spent?.let { "$currency ${String.format("%.2f", it)}" } ?: "unavailable")
