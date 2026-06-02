package com.shade.app.domain.usecase.group

import com.shade.app.crypto.SenderKeyCryptoManager
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.domain.repository.SenderKeyRepository
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

/**
 * Rotates the caller's [OwnSenderKeyEntity] for [groupId].
 *
 * Triggered when the caller observes a `GroupMembershipEvent` with kind
 * `LEFT` / `REMOVED` and `subject_id ≠ self`. Rotation invalidates any
 * chain_key snapshot the departing member might have retained: bumps
 * `key_id`, generates a fresh chain_key + fresh Ed25519 signing keypair, and
 * resets `chain_index` to 0.
 *
 * The caller is responsible for then re-distributing the new SKDM to the
 * remaining members.
 */
class RotateOwnSenderKeyUseCase @Inject constructor(
    private val senderKeyRepository: SenderKeyRepository,
    private val crypto: SenderKeyCryptoManager,
) {
    suspend operator fun invoke(groupId: String): OwnSenderKeyEntity {
        val previous = senderKeyRepository.getOwn(groupId)
        val nextKeyId = (previous?.keyId ?: -1L) + 1L
        val chainKey = crypto.generateChainKey()
        val signingKp = crypto.generateSigningKeyPair()

        val rotated = OwnSenderKeyEntity(
            groupId = groupId,
            keyId = nextKeyId,
            chainKeyHex = Hex.toHexString(chainKey),
            chainIndex = 0L,
            signingPublicKeyHex = Hex.toHexString(signingKp.publicKey),
            signingPrivateKeyHex = Hex.toHexString(signingKp.privateKey),
            createdAt = System.currentTimeMillis(),
        )
        senderKeyRepository.saveOwn(rotated)

        // Prior dispatch records reference the now-stale keyId; drop them so
        // the next distribute call ships the new SKDM unconditionally.
        senderKeyRepository.purgeStaleDispatched(groupId, keepKeyId = nextKeyId)
        return rotated
    }
}
