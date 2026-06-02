package com.shade.app.crypto

import cash.z.ecc.android.bip39.Mnemonics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MnemonicManager @Inject constructor() {
    fun generateMnemonic(): List<String> {
        val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12)
        return mnemonicCode.words.map { String(it) }
    }
}