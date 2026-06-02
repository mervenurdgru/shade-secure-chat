package com.shade.app.domain.usecase.group

import com.shade.app.crypto.SenderKeyCryptoManager
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.domain.repository.SenderKeyRepository
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

/**
 * Returns this device's [OwnSenderKeyEntity] for [groupId], generating a fresh
 * `key_id = 0` chain on first use.
 *
 * The Send Algorithm requires both an HKDF chain root and an Ed25519 signing
 * keypair; both are created here so callers can ratchet immediately.
 */
class EnsureOwnSenderKeyUseCase @Inject constructor(
    private val senderKeyRepository: SenderKeyRepository,
    private val crypto: SenderKeyCryptoManager,
) {
    suspend operator fun invoke(groupId: String): OwnSenderKeyEntity {
        senderKeyRepository.getOwn(groupId)?.let { return it }

        val chainKey = crypto.generateChainKey()
        val signingKp = crypto.generateSigningKeyPair()

        val key = OwnSenderKeyEntity(
            groupId = groupId,
            keyId = 0L,
            chainKeyHex = Hex.toHexString(chainKey),
            chainIndex = 0L,
            signingPublicKeyHex = Hex.toHexString(signingKp.publicKey),
            signingPrivateKeyHex = Hex.toHexString(signingKp.privateKey),
            createdAt = System.currentTimeMillis(),
        )
        senderKeyRepository.saveOwn(key)
        return key
    }
}
