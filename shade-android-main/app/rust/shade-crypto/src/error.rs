use std::fmt;

#[derive(Debug)]
pub enum CryptoError {
    InvalidKeyLength(usize),
    InvalidNonceLength(usize),
    EncryptionFailed,
    DecryptionFailed,
    HexDecodeError(String),
}

impl fmt::Display for CryptoError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CryptoError::InvalidKeyLength(len) => {
                write!(f, "Invalid key lenght: {} (expected 32)", len)
            }
            CryptoError::InvalidNonceLength(len) => {
                write!(f, "Invalid nonce length: {} (expected 12)", len)
            }
            CryptoError::EncryptionFailed => write!(f, "ChaCha20-Poly1305 encryption failed"),
            CryptoError::DecryptionFailed => write!(f, "ChaCha20-Poly1305 decryption failed"),
            CryptoError::HexDecodeError(msg) => write!(f, "Hex decode error: {}", msg),
        }
    }
}

impl std::error::Error for CryptoError {}
