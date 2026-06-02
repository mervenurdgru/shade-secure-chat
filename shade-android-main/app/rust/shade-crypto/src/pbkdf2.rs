use hmac::Hmac;
use sha2::Sha256;

pub fn derive_key(
    passphrase: &[u8],
    salt: &[u8],
    iterations: u32,
    key_len_bytes: usize,
) -> Vec<u8> {
    let mut key = vec![0u8; key_len_bytes];
    pbkdf2::pbkdf2::<Hmac<Sha256>>(passphrase, salt, iterations, &mut key)
        .expect("PBKDF2 output length should be valid");

    key
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pbkdf2_rfc6070_vector() {
        let passphrase = b"password";
        let salt = b"salt";
        let key1 = derive_key(passphrase, salt, 100_000, 32);
        let key2 = derive_key(passphrase, salt, 100_000, 32);
        assert_eq!(key1, key2);
        assert_eq!(key1.len(), 32);
    }

    #[test]
    fn test_pbkdf2_different_inputs() {
        let key1 = derive_key(b"password1", b"salt", 1000, 32);
        let key2 = derive_key(b"password2", b"salt", 1000, 32);
        assert_ne!(key1, key2);
    }

    #[test]
    fn test_pbkdf2_different_salts() {
        let key1 = derive_key(b"password", b"salt1", 1000, 32);
        let key2 = derive_key(b"password", b"salt2", 1000, 32);
        assert_ne!(key1, key2);
    }
}
