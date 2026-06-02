mod chacha;
mod error;
mod pbkdf2;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jbyteArray;
use jni::JNIEnv;
use std::panic::{self, AssertUnwindSafe};

fn jni_safe<F>(env: &mut JNIEnv, default: jbyteArray, f: F) -> jbyteArray
where
    F: FnOnce(&mut JNIEnv) -> Result<jbyteArray, String>,
{
    match panic::catch_unwind(AssertUnwindSafe(|| f(env))) {
        Ok(Ok(result)) => result,
        Ok(Err(msg)) => {
            let _ = env.throw_new("java/lang/RuntimeException", &msg);
            default
        }
        Err(_) => {
            let _ = env.throw_new("java/lang/RuntimeException", "Rust panic in native crypto");
            default
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_shade_app_crypto_NativeCryptoManager_nativePbkdf2(
    mut env: JNIEnv,
    _class: JClass,
    passphrase: JString,
    salt: JByteArray,
    iterations: i32,
    key_len_bits: i32,
) -> jbyteArray {
    let null = std::ptr::null_mut();
    jni_safe(&mut env, null, |env| {
        let passphrase: String = env
            .get_string(&passphrase)
            .map_err(|e| format!("Failed to get passphrase string: {}", e))?
            .into();

        let salt_bytes = env
            .convert_byte_array(&salt)
            .map_err(|e| format!("Failed to get salt bytes: {}", e))?;

        let key_len_bytes = (key_len_bits as usize) / 8;
        let key = pbkdf2::derive_key(
            passphrase.as_bytes(),
            &salt_bytes,
            iterations as u32,
            key_len_bytes,
        );

        let output = env
            .byte_array_from_slice(&key)
            .map_err(|e| format!("Failed to create output array: {}", e))?;

        Ok(output.into_raw())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shade_app_crypto_NativeCryptoManager_nativeChaChaEncrypt(
    mut env: JNIEnv,
    _class: JClass,
    plaintext: JByteArray,
    key: JByteArray,
) -> jbyteArray {
    let null = std::ptr::null_mut();
    jni_safe(&mut env, null, |env| {
        let plain_bytes = env
            .convert_byte_array(&plaintext)
            .map_err(|e| format!("Failed to get plaintext bytes: {}", e))?;

        let key_bytes = env
            .convert_byte_array(&key)
            .map_err(|e| format!("Failed to get key bytes: {}", e))?;

        let (ciphertext, nonce) =
            chacha::encrypt(&plain_bytes, &key_bytes).map_err(|e: crate::error::CryptoError| e.to_string())?;

        // Output format: [12-byte nonce | ciphertext + tag]
        let mut combined = Vec::with_capacity(nonce.len() + ciphertext.len());
        combined.extend_from_slice(&nonce);
        combined.extend_from_slice(&ciphertext);

        let output = env
            .byte_array_from_slice(&combined)
            .map_err(|e| format!("Failed to create output array: {}", e))?;

        Ok(output.into_raw())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shade_app_crypto_NativeCryptoManager_nativeChaChaDecrypt(
    mut env: JNIEnv,
    _class: JClass,
    ciphertext: JByteArray,
    nonce: JByteArray,
    key: JByteArray,
) -> jbyteArray {
    let null = std::ptr::null_mut();
    jni_safe(&mut env, null, |env| {
        let cipher_bytes = env
            .convert_byte_array(&ciphertext)
            .map_err(|e| format!("Failed to get ciphertext bytes: {}", e))?;

        let nonce_bytes = env
            .convert_byte_array(&nonce)
            .map_err(|e| format!("Failed to get nonce bytes: {}", e))?;

        let key_bytes = env
            .convert_byte_array(&key)
            .map_err(|e| format!("Failed to get key bytes: {}", e))?;

        let plaintext =
            chacha::decrypt(&cipher_bytes, &nonce_bytes, &key_bytes).map_err(|e| e.to_string())?;

        let output = env
            .byte_array_from_slice(&plaintext)
            .map_err(|e| format!("Failed to create output array: {}", e))?;

        Ok(output.into_raw())
    })
}
