package com.mikimouse.operator

object Redactor {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("\\+?\\d[\\d\\s-]{7,}\\d")
    private val otp = Regex("(?i)\\b(otp|code|pin)[\\s:]*\\d{4,8}")

    fun mask(text: String): String {
        var s = email.replace(text, "***@***")
        s = otp.replace(s) { m -> m.value.replace(Regex("\\d{4,8}"), "••••") }
        s = phone.replace(s, "•••")
        return s
    }

    fun looksSensitive(text: String): Boolean =
        email.containsMatchIn(text) || otp.containsMatchIn(text)
}
