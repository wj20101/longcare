package com.ytone.longcare.common.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.AlgorithmParameterSpec

/**
 * RSA 加密结果数据类（精简版）
 * 只存储核心数据，其他格式按需生成以节省内存
 *
 * @property cipherText 密文字节数组
 */
data class RSAEncryptResult(
    val cipherText: ByteArray
) {
    /**
     * 获取Base64编码的密文
     */
    fun getCipherTextBase64(): String = Base64.encodeToString(cipherText, Base64.NO_WRAP)

    /**
     * 获取16进制编码的密文
     */
    fun getCipherTextHex(): String = CryptoUtils.bytesToHex(cipherText)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RSAEncryptResult
        return cipherText.contentEquals(other.cipherText)
    }

    override fun hashCode(): Int {
        return cipherText.contentHashCode()
    }
}

/**
 * AES 加密结果数据类（精简版）
 * 只存储核心数据，其他格式按需生成以节省内存
 *
 * @property cipherText 密文字节数组
 * @property iv 初始化向量字节数组（如果使用）
 */
data class AESEncryptResult(
    val cipherText: ByteArray,
    val iv: ByteArray?
) {
    /**
     * 获取Base64编码的密文
     */
    fun getCipherTextBase64(): String = Base64.encodeToString(cipherText, Base64.NO_WRAP)

    /**
     * 获取16进制编码的密文
     */
    fun getCipherTextHex(): String = CryptoUtils.bytesToHex(cipherText)

    /**
     * 获取Base64编码的IV
     */
    fun getIvBase64(): String? = iv?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    /**
     * 获取16进制编码的IV
     */
    fun getIvHex(): String? = iv?.let { CryptoUtils.bytesToHex(it) }

    /**
     * 获取Base64编码的组合数据（IV + 密文）
     */
    fun getCombinedBase64(): String = if (iv != null) {
        Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    } else {
        Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    /**
     * 获取16进制编码的组合数据（IV + 密文）
     */
    fun getCombinedHex(): String = if (iv != null) {
        CryptoUtils.bytesToHex(iv + cipherText)
    } else {
        CryptoUtils.bytesToHex(cipherText)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AESEncryptResult
        if (!cipherText.contentEquals(other.cipherText)) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 哈希算法枚举
 */
enum class HashAlgorithm(val algorithm: String) {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512")
}

/**
 * 密钥长度枚举
 */
enum class KeySize(val bits: Int, val bytes: Int) {
    AES_128(128, 16),
    AES_192(192, 24),
    AES_256(256, 32),
    RSA_1024(1024, 128),
    RSA_2048(2048, 256),
    RSA_3072(3072, 384),
    RSA_4096(4096, 512)
}

enum class AESMode(val transformation: String, val requiresIV: Boolean = true) {
    GCM_NO_PADDING("AES/GCM/NoPadding", true),
    CBC_PKCS5_PADDING("AES/CBC/PKCS5Padding", true),
    CBC_PKCS7_PADDING("AES/CBC/PKCS7Padding", true),
    ECB_PKCS5_PADDING("AES/ECB/PKCS5Padding", false),
    ECB_PKCS7_PADDING("AES/ECB/PKCS7Padding", false),
    CFB_PKCS5_PADDING("AES/CFB/PKCS5Padding", true),
    CFB_PKCS7_PADDING("AES/CFB/PKCS7Padding", true),
    CFB_NO_PADDING("AES/CFB/NoPadding", true),
    OFB_PKCS5_PADDING("AES/OFB/PKCS5Padding", true),
    OFB_PKCS7_PADDING("AES/OFB/PKCS7Padding", true),
    OFB_NO_PADDING("AES/OFB/NoPadding", true),
    CTR_NO_PADDING("AES/CTR/NoPadding", true)
}

enum class RSAMode(val transformation: String) {
    NONE_PKCS1_PADDING("RSA/NONE/PKCS1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 11
    },
    ECB_PKCS1_PADDING("RSA/ECB/PKCS1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 11
    },
    ECB_OAEP_WITH_SHA1_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-1AndMGF1Padding") {
        // SHA-1 hash length = 20 bytes. Max data = key_bytes - 2*hash_bytes - 2.
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 20 - 2
    },
    ECB_OAEP_WITH_SHA256_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-256AndMGF1Padding") {
        // SHA-256 hash length = 32 bytes.
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 32 - 2
    },
    ECB_OAEP_WITH_SHA384_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-384AndMGF1Padding") {
        // SHA-384 hash length = 48 bytes.
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 48 - 2
    },
    ECB_OAEP_WITH_SHA512_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-512AndMGF1Padding") {
        // SHA-512 hash length = 64 bytes.
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 64 - 2
    };

    /**
     * Calculates the maximum number of bytes that can be encrypted with this RSA mode and a given key size.
     * @param keySizeInBits The size of the RSA key in bits.
     * @return The maximum number of bytes that can be encrypted.
     */
    abstract fun getMaxDataSize(keySizeInBits: Int): Int

    companion object {
        /**
         * Default RSA encryption mode, recommended for new applications.
         * OAEP padding is generally more secure than PKCS1Padding.
         */
        val DEFAULT_ENCRYPTION_MODE = ECB_OAEP_WITH_SHA256_AND_MGF1_PADDING
    }
}

/**
 * 加解密工具类，提供常用的加解密算法实现。
 */
object CryptoUtils {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private const val KEY_ALIAS_AES = "aesKeyAlias"
    private const val KEY_ALIAS_RSA = "rsaKeyAlias"

    private const val GCM_IV_LENGTH = 12 // bytes
    private const val GCM_TAG_LENGTH = 128 // bits

    /**
     * 通用哈希函数。
     *
     * @param input 要计算哈希值的字符串。
     * @param algorithm 哈希算法枚举。
     * @return 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun hash(input: String, algorithm: HashAlgorithm): String? {
        if (input.isEmpty()) return null
        return try {
            val digest = MessageDigest.getInstance(algorithm.algorithm)
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            bytesToHex(hashBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 计算字符串的 MD5 哈希值。
     * 注意：MD5 算法已被认为不安全，仅用于非安全场景。
     *
     * @param input 要计算哈希值的字符串。
     * @return MD5 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun md5(input: String?): String? {
        if (input.isNullOrEmpty()) return null
        return hash(input, HashAlgorithm.MD5)
    }

    /**
     * 计算字符串的 SHA-1 哈希值。
     * 注意：SHA-1 算法已被认为不安全，仅用于兼容性场景。
     *
     * @param input 要计算哈希值的字符串。
     * @return SHA-1 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun sha1(input: String): String? = hash(input, HashAlgorithm.SHA1)

    /**
     * 计算字符串的 SHA-256 哈希值。
     *
     * @param input 要计算哈希值的字符串。
     * @return SHA-256 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun sha256(input: String?): String? {
        if (input.isNullOrEmpty()) return null
        return hash(input, HashAlgorithm.SHA256)
    }

    /**
     * 计算字符串的 SHA-384 哈希值。
     *
     * @param input 要计算哈希值的字符串。
     * @return SHA-384 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun sha384(input: String): String? = hash(input, HashAlgorithm.SHA384)

    /**
     * 计算字符串的 SHA-512 哈希值。
     *
     * @param input 要计算哈希值的字符串。
     * @return SHA-512 哈希值的十六进制字符串；如果计算失败，则返回 null。
     */
    fun sha512(input: String?): String? {
        if (input.isNullOrEmpty()) return null
        return hash(input, HashAlgorithm.SHA512)
    }

    // --- AES Encryption/Decryption (GCM Mode recommended for Android) ---

    /**
     * 生成指定长度的 AES 密钥。
     *
     * @param keySize 密钥大小枚举。
     * @return Base64 编码的 AES 密钥字符串；如果生成失败，则返回 null。
     */
    fun generateAESKey(keySize: KeySize = KeySize.AES_256): String? {
        require(keySize in listOf(KeySize.AES_128, KeySize.AES_192, KeySize.AES_256)) {
            "Invalid AES key size: ${keySize.bits}. Supported sizes are 128, 192, 256."
        }
        return try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(keySize.bits)
            val secretKey = keyGenerator.generateKey()
            Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成指定长度的 AES 密钥（兼容旧版本）。
     *
     * @param keyLength 密钥长度（位），支持 128、192、256。
     * @return Base64 编码的 AES 密钥字符串；如果生成失败，则返回 null。
     */
    @Deprecated(
        "Use generateAESKey(KeySize) instead",
        ReplaceWith("generateAESKey(KeySize.AES_256)")
    )
    fun generateAESKey(keyLength: Int): String? {
        val keySize = when (keyLength) {
            128 -> KeySize.AES_128
            192 -> KeySize.AES_192
            256 -> KeySize.AES_256
            else -> throw IllegalArgumentException("Invalid AES key length: $keyLength. Supported lengths are 128, 192, 256.")
        }
        return generateAESKey(keySize)
    }

    /**
     * 核心AES加密函数，减少代码重复
     */
    private fun aesEncryptCore(
        plainData: ByteArray,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        iv: ByteArray? = null
    ): AESEncryptResult? {
        if (plainData.isEmpty() || keyString.isEmpty()) return null

        return try {
            val keyBytes = keyString.toByteArray()
            require(
                keyBytes.size in listOf(
                    16,
                    24,
                    32
                )
            ) { "Invalid AES key size: ${keyBytes.size * 8} bits" }

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(mode.transformation)

            val actualIV: ByteArray?
            val parameterSpec: AlgorithmParameterSpec?

            when (mode) {
                AESMode.GCM_NO_PADDING -> {
                    actualIV = iv ?: ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
                    require(actualIV.size == GCM_IV_LENGTH) { "GCM mode requires ${GCM_IV_LENGTH}-byte IV" }
                    parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, actualIV)
                }

                AESMode.CBC_PKCS5_PADDING, AESMode.CBC_PKCS7_PADDING,
                AESMode.CFB_PKCS5_PADDING, AESMode.CFB_PKCS7_PADDING, AESMode.CFB_NO_PADDING,
                AESMode.OFB_PKCS5_PADDING, AESMode.OFB_PKCS7_PADDING, AESMode.OFB_NO_PADDING,
                AESMode.CTR_NO_PADDING -> {
                    val blockSize = 16 // AES block size is always 16 bytes
                    actualIV = iv ?: ByteArray(blockSize).also { SecureRandom().nextBytes(it) }
                    require(actualIV.size == blockSize) { "${mode.name} mode requires ${blockSize}-byte IV" }
                    parameterSpec = IvParameterSpec(actualIV)
                }

                AESMode.ECB_PKCS5_PADDING, AESMode.ECB_PKCS7_PADDING -> {
                    actualIV = null
                    parameterSpec = null
                }
            }

            if (parameterSpec != null) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            }

            val cipherText = cipher.doFinal(plainData)
            AESEncryptResult(cipherText, actualIV)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 AES 加密数据。
     *
     * @param plainText 要加密的明文。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 加密模式。
     * @param iv 初始化向量，如果为 null 则自动生成（对于需要 IV 的模式）。
     * @return 包含 IV 和密文的结果对象；如果加密失败，则返回 null。
     */
    fun aesEncrypt(
        plainText: String,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        iv: ByteArray? = null
    ): AESEncryptResult? {
        return aesEncryptCore(plainText.toByteArray(), keyString, mode, iv)
    }

    /**
     * 使用 AES 加密数据（支持 ByteArray 输入）。
     *
     * @param plainData 要加密的原始数据。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 加密模式。
     * @param iv 初始化向量，如果为 null 则自动生成（对于需要 IV 的模式）。
     * @return 包含 IV 和密文的结果对象；如果加密失败，则返回 null。
     */
    fun aesEncrypt(
        plainData: ByteArray,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        iv: ByteArray? = null
    ): AESEncryptResult? {
        return aesEncryptCore(plainData, keyString, mode, iv)
    }

    /**
     * AES 解密核心函数（内部使用）。
     *
     * @param cipherBytes 密文字节数组。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 解密模式。
     * @param ivBytes 初始化向量字节数组，对于需要 IV 的模式是必需的。
     * @return 解密后的明文字节数组；如果解密失败，则返回 null。
     */
    private fun aesDecryptCore(
        cipherBytes: ByteArray,
        keyString: String,
        mode: AESMode,
        ivBytes: ByteArray?
    ): ByteArray? {
        if (cipherBytes.isEmpty() || keyString.isEmpty()) return null

        return try {
            val keyBytesDecoded = Base64.decode(keyString, Base64.NO_WRAP)
            require(
                keyBytesDecoded.size in listOf(
                    16,
                    24,
                    32
                )
            ) { "Invalid AES key size: ${keyBytesDecoded.size * 8} bits" }

            val secretKey = SecretKeySpec(keyBytesDecoded, "AES")
            val cipher = Cipher.getInstance(mode.transformation)

            val parameterSpec: AlgorithmParameterSpec?

            when (mode) {
                AESMode.GCM_NO_PADDING -> {
                    require(ivBytes != null && ivBytes.size == GCM_IV_LENGTH) { "GCM mode requires ${GCM_IV_LENGTH}-byte IV" }
                    parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)
                }

                AESMode.CBC_PKCS5_PADDING, AESMode.CBC_PKCS7_PADDING,
                AESMode.CFB_PKCS5_PADDING, AESMode.CFB_PKCS7_PADDING, AESMode.CFB_NO_PADDING,
                AESMode.OFB_PKCS5_PADDING, AESMode.OFB_PKCS7_PADDING, AESMode.OFB_NO_PADDING,
                AESMode.CTR_NO_PADDING -> {
                    val blockSize = 16 // AES block size is always 16 bytes
                    require(ivBytes != null && ivBytes.size == blockSize) { "${mode.name} mode requires ${blockSize}-byte IV" }
                    parameterSpec = IvParameterSpec(ivBytes)
                }

                AESMode.ECB_PKCS5_PADDING, AESMode.ECB_PKCS7_PADDING -> {
                    parameterSpec = null
                }
            }

            if (parameterSpec != null) {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            } else {
                cipher.init(Cipher.DECRYPT_MODE, secretKey)
            }

            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 AES 解密数据。
     *
     * @param base64CipherText Base64 编码的密文。如果 iv 参数为 null 且模式需要 IV (如 GCM, CBC 等),
     *                         则此参数应为 IV 和密文的 Base64 编码组合 (IV 在前，密文在后)。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 解密模式。
     * @param iv Base64 编码的初始化向量。如果模式需要 IV 且此参数不为 null，则使用此 IV。
     *           如果模式需要 IV 且此参数为 null，则会尝试从 base64CipherText 中提取 IV。
     *           如果模式不需要 IV (如 ECB)，则此参数被忽略。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun aesDecrypt(
        base64CipherText: String,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        iv: String? = null
    ): String? {
        if (base64CipherText.isEmpty() || keyString.isEmpty()) return null

        return try {
            val decodedCipherText = Base64.decode(base64CipherText, Base64.NO_WRAP)

            val actualIV: ByteArray?
            val actualCipherText: ByteArray

            when (mode) {
                AESMode.GCM_NO_PADDING -> {
                    if (iv != null) {
                        actualIV = Base64.decode(iv, Base64.NO_WRAP)
                        actualCipherText = decodedCipherText
                    } else {
                        require(decodedCipherText.size > GCM_IV_LENGTH) { "Invalid ciphertext length for GCM mode" }
                        actualIV = decodedCipherText.copyOfRange(0, GCM_IV_LENGTH)
                        actualCipherText =
                            decodedCipherText.copyOfRange(GCM_IV_LENGTH, decodedCipherText.size)
                    }
                }

                AESMode.CBC_PKCS5_PADDING, AESMode.CBC_PKCS7_PADDING,
                AESMode.CFB_PKCS5_PADDING, AESMode.CFB_PKCS7_PADDING, AESMode.CFB_NO_PADDING,
                AESMode.OFB_PKCS5_PADDING, AESMode.OFB_PKCS7_PADDING, AESMode.OFB_NO_PADDING,
                AESMode.CTR_NO_PADDING -> {
                    val blockSize = 16 // AES block size is always 16 bytes
                    if (iv != null) {
                        actualIV = Base64.decode(iv, Base64.NO_WRAP)
                        actualCipherText = decodedCipherText
                    } else {
                        require(decodedCipherText.size > blockSize) { "Invalid ciphertext length for ${mode.name} mode" }
                        actualIV = decodedCipherText.copyOfRange(0, blockSize)
                        actualCipherText =
                            decodedCipherText.copyOfRange(blockSize, decodedCipherText.size)
                    }
                }

                AESMode.ECB_PKCS5_PADDING, AESMode.ECB_PKCS7_PADDING -> {
                    actualIV = null
                    actualCipherText = decodedCipherText
                }
            }

            val plainBytes = aesDecryptCore(actualCipherText, keyString, mode, actualIV)
            plainBytes?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 AES 解密数据（支持 16进制输入）。
     *
     * @param hexCipherText 16进制编码的密文。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 解密模式。
     * @param hexIV 16进制编码的初始化向量，如果为 null 则从密文中提取（适用于组合格式）。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun aesDecryptFromHex(
        hexCipherText: String,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        hexIV: String? = null
    ): String? {
        if (hexCipherText.isEmpty() || keyString.isEmpty()) return null

        return try {
            val cipherBytes = hexToBytes(hexCipherText)
            val ivBytes = hexIV?.let { hexToBytes(it) }

            val plainBytes = aesDecryptCore(cipherBytes, keyString, mode, ivBytes)
            plainBytes?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 AES 解密数据（返回 ByteArray）。
     *
     * @param base64CipherText Base64 编码的密文。如果 iv 参数为 null 且模式需要 IV (如 GCM, CBC 等),
     *                         则此参数应为 IV 和密文的 Base64 编码组合 (IV 在前，密文在后)。
     * @param keyString Base64 编码的 AES 密钥字符串。
     * @param mode AES 解密模式。
     * @param iv Base64 编码的初始化向量。如果模式需要 IV 且此参数不为 null，则使用此 IV。
     *           如果模式需要 IV 且此参数为 null，则会尝试从 base64CipherText 中提取 IV。
     *           如果模式不需要 IV (如 ECB)，则此参数被忽略。
     * @return 解密后的原始数据；如果解密失败，则返回 null。
     */
    fun aesDecryptToBytes(
        base64CipherText: String,
        keyString: String,
        mode: AESMode = AESMode.GCM_NO_PADDING,
        iv: String? = null
    ): ByteArray? {
        if (base64CipherText.isEmpty() || keyString.isEmpty()) return null

        return try {
            val decodedCipherText = Base64.decode(base64CipherText, Base64.NO_WRAP)

            val actualIV: ByteArray?
            val actualCipherText: ByteArray

            when (mode) {
                AESMode.GCM_NO_PADDING -> {
                    if (iv != null) {
                        actualIV = Base64.decode(iv, Base64.NO_WRAP)
                        actualCipherText = decodedCipherText
                    } else {
                        require(decodedCipherText.size > GCM_IV_LENGTH) { "Invalid ciphertext length for GCM mode" }
                        actualIV = decodedCipherText.copyOfRange(0, GCM_IV_LENGTH)
                        actualCipherText =
                            decodedCipherText.copyOfRange(GCM_IV_LENGTH, decodedCipherText.size)
                    }
                }

                AESMode.CBC_PKCS5_PADDING, AESMode.CBC_PKCS7_PADDING,
                AESMode.CFB_PKCS5_PADDING, AESMode.CFB_PKCS7_PADDING, AESMode.CFB_NO_PADDING,
                AESMode.OFB_PKCS5_PADDING, AESMode.OFB_PKCS7_PADDING, AESMode.OFB_NO_PADDING,
                AESMode.CTR_NO_PADDING -> {
                    val blockSize = 16 // AES block size is always 16 bytes
                    if (iv != null) {
                        actualIV = Base64.decode(iv, Base64.NO_WRAP)
                        actualCipherText = decodedCipherText
                    } else {
                        require(decodedCipherText.size > blockSize) { "Invalid ciphertext length for ${mode.name} mode" }
                        actualIV = decodedCipherText.copyOfRange(0, blockSize)
                        actualCipherText =
                            decodedCipherText.copyOfRange(blockSize, decodedCipherText.size)
                    }
                }

                AESMode.ECB_PKCS5_PADDING, AESMode.ECB_PKCS7_PADDING -> {
                    actualIV = null
                    actualCipherText = decodedCipherText
                }
            }

            aesDecryptCore(actualCipherText, keyString, mode, actualIV)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- RSA Encryption/Decryption ---

    private fun getKeySizeInBits(key: java.security.Key): Int? {
        return when (key) {
            is java.security.interfaces.RSAKey -> key.modulus.bitLength()
            else -> {
                System.err.println("Unsupported key type for size calculation: ${key.javaClass.name}")
                null
            }
        }
    }

    //region RSA Key Generation
    /**
     * 生成指定长度的 RSA 密钥对。
     *
     * @param keySize 密钥大小枚举。
     * @return 包含 Base64 编码的公钥和私钥的 Pair；如果生成失败，则返回 null。
     */
    fun generateRSAKeyPair(keySize: KeySize = KeySize.RSA_2048): Pair<String, String>? {
        require(
            keySize in listOf(
                KeySize.RSA_1024,
                KeySize.RSA_2048,
                KeySize.RSA_3072,
                KeySize.RSA_4096
            )
        ) {
            "Invalid RSA key size: ${keySize.bits}. Supported sizes are 1024, 2048, 3072, 4096."
        }
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(keySize.bits)
            val keyPair = keyPairGenerator.generateKeyPair()

            val publicKeyString = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            val privateKeyString = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

            Pair(publicKeyString, privateKeyString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成指定长度的 RSA 密钥对（兼容旧版本）。
     *
     * @param keyLength 密钥长度（位），支持 1024、2048、3072、4096。
     * @return 包含 Base64 编码的公钥和私钥的 Pair；如果生成失败，则返回 null。
     */
    @Deprecated(
        "Use generateRSAKeyPair(KeySize) instead",
        ReplaceWith("generateRSAKeyPair(KeySize.RSA_2048)")
    )
    fun generateRSAKeyPair(keyLength: Int): Pair<String, String>? {
        val keySize = when (keyLength) {
            1024 -> KeySize.RSA_1024
            2048 -> KeySize.RSA_2048
            3072 -> KeySize.RSA_3072
            4096 -> KeySize.RSA_4096
            else -> throw IllegalArgumentException("Invalid RSA key length: $keyLength. Supported lengths are 1024, 2048, 3072, 4096.")
        }
        return generateRSAKeyPair(keySize)
    }

    /**
     * 将 PublicKey 转换为 Base64 编码的字符串。
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * 将 Base64 编码的字符串转换为 PublicKey。
     */
    fun stringToPublicKey(publicKeyString: String): PublicKey? {
        if (publicKeyString.isEmpty()) return null
        return try {
            val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(spec)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 PrivateKey 转换为 Base64 编码的字符串。
     */
    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    /**
     * 将 Base64 编码的字符串转换为 PrivateKey。
     */
    fun stringToPrivateKey(privateKeyString: String): PrivateKey? {
        if (privateKeyString.isEmpty()) return null
        return try {
            val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(spec)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 核心RSA加密函数
     */
    private fun rsaEncryptCore(
        plainData: ByteArray,
        publicKey: PublicKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        if (plainData.isEmpty()) return null
        val keySize = getKeySizeInBits(publicKey)
        if (keySize == null) {
            System.err.println("Could not determine RSA key size for input validation. Encryption aborted.")
            return null
        }
        val maxData = mode.getMaxDataSize(keySize)
        require(plainData.size <= maxData) {
            "Data too large for RSA encryption with the chosen mode and key size. Maximum data size: $maxData bytes, data size: ${plainData.size} bytes."
        }
        return try {
            val cipher = Cipher.getInstance(mode.transformation)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            cipher.doFinal(plainData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 核心RSA解密函数
     */
    private fun rsaDecryptCore(
        cipherData: ByteArray,
        privateKey: PrivateKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        if (cipherData.isEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(mode.transformation)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(cipherData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 RSA 公钥加密数据。
     *
     * @param plainText 要加密的明文。
     * @param publicKey RSA 公钥。
     * @param mode RSA 加密模式。
     * @return RSA加密结果对象；如果加密失败，则返回 null。
     */
    fun rsaEncrypt(
        plainText: String,
        publicKey: PublicKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): RSAEncryptResult? {
        val plainData = plainText.toByteArray(Charsets.UTF_8)
        val cipherText = rsaEncryptCore(plainData, publicKey, mode)
        return cipherText?.let { RSAEncryptResult(it) }
    }

    /**
     * 使用 RSA 公钥加密数据。
     *
     * @param plainData 要加密的字节数组。
     * @param publicKey RSA 公钥。
     * @param mode RSA 加密模式。
     * @return RSA加密结果对象；如果加密失败，则返回 null。
     */
    fun rsaEncrypt(
        plainData: ByteArray,
        publicKey: PublicKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): RSAEncryptResult? {
        val cipherText = rsaEncryptCore(plainData, publicKey, mode)
        return cipherText?.let { RSAEncryptResult(it) }
    }

    /**
     * 使用 RSA 公钥字符串加密数据。
     *
     * @param plainText 要加密的明文。
     * @param publicKeyString Base64 编码的 RSA 公钥字符串。
     * @param mode RSA 加密模式。
     * @return RSA加密结果对象；如果加密失败，则返回 null。
     */
    fun rsaEncrypt(
        plainText: String,
        publicKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): RSAEncryptResult? {
        val publicKey = stringToPublicKey(publicKeyString) ?: return null
        return rsaEncrypt(plainText, publicKey, mode)
    }

    /**
     * 使用 RSA 公钥字符串加密数据。
     *
     * @param plainData 要加密的字节数组。
     * @param publicKeyString Base64 编码的 RSA 公钥字符串。
     * @param mode RSA 加密模式。
     * @return RSA加密结果对象；如果加密失败，则返回 null。
     */
    fun rsaEncrypt(
        plainData: ByteArray,
        publicKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): RSAEncryptResult? {
        val publicKey = stringToPublicKey(publicKeyString) ?: return null
        return rsaEncrypt(plainData, publicKey, mode)
    }


    /**
     * 使用 RSA 私钥解密数据。
     *
     * @param base64CipherText Base64 编码的密文。
     * @param privateKey RSA 私钥。
     * @param mode RSA 解密模式。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun rsaDecrypt(
        base64CipherText: String,
        privateKey: PrivateKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): String? {
        if (base64CipherText.isEmpty()) return null
        return try {
            val cipherData = Base64.decode(base64CipherText, Base64.NO_WRAP)
            val decryptedBytes = rsaDecryptCore(cipherData, privateKey, mode)
            decryptedBytes?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 RSA 私钥字符串解密数据。
     *
     * @param base64CipherText Base64 编码的密文。
     * @param privateKeyString Base64 编码的 RSA 私钥字符串。
     * @param mode RSA 解密模式。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun rsaDecrypt(
        base64CipherText: String,
        privateKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): String? {
        val privateKey = stringToPrivateKey(privateKeyString) ?: return null
        return rsaDecrypt(base64CipherText, privateKey, mode)
    }

    /**
     * 使用 RSA 私钥解密数据并返回字节数组。
     *
     * @param base64CipherText Base64 编码的密文。
     * @param privateKey RSA 私钥。
     * @param mode RSA 解密模式。
     * @return 解密后的字节数组；如果解密失败，则返回 null。
     */
    fun rsaDecryptToBytes(
        base64CipherText: String,
        privateKey: PrivateKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        if (base64CipherText.isEmpty()) return null
        return try {
            val cipherData = Base64.decode(base64CipherText, Base64.NO_WRAP)
            rsaDecryptCore(cipherData, privateKey, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 RSA 私钥字符串解密数据并返回字节数组。
     *
     * @param base64CipherText Base64 编码的密文。
     * @param privateKeyString Base64 编码的 RSA 私钥字符串。
     * @param mode RSA 解密模式。
     * @return 解密后的字节数组；如果解密失败，则返回 null。
     */
    fun rsaDecryptToBytes(
        base64CipherText: String,
        privateKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        val privateKey = stringToPrivateKey(privateKeyString) ?: return null
        return rsaDecryptToBytes(base64CipherText, privateKey, mode)
    }

    /**
     * 使用 RSA 私钥解密16进制编码的密文。
     *
     * @param hexCipherText 16进制编码的密文。
     * @param privateKey RSA 私钥。
     * @param mode RSA 解密模式。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun rsaDecryptFromHex(
        hexCipherText: String,
        privateKey: PrivateKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): String? {
        if (hexCipherText.isEmpty()) return null
        return try {
            val cipherData = hexToBytes(hexCipherText)
            val decryptedBytes = rsaDecryptCore(cipherData, privateKey, mode)
            decryptedBytes?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 RSA 私钥字符串解密16进制编码的密文。
     *
     * @param hexCipherText 16进制编码的密文。
     * @param privateKeyString Base64 编码的 RSA 私钥字符串。
     * @param mode RSA 解密模式。
     * @return 解密后的明文；如果解密失败，则返回 null。
     */
    fun rsaDecryptFromHex(
        hexCipherText: String,
        privateKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): String? {
        val privateKey = stringToPrivateKey(privateKeyString) ?: return null
        return rsaDecryptFromHex(hexCipherText, privateKey, mode)
    }

    /**
     * 使用 RSA 私钥解密16进制编码的密文并返回字节数组。
     *
     * @param hexCipherText 16进制编码的密文。
     * @param privateKey RSA 私钥。
     * @param mode RSA 解密模式。
     * @return 解密后的字节数组；如果解密失败，则返回 null。
     */
    fun rsaDecryptToBytesFromHex(
        hexCipherText: String,
        privateKey: PrivateKey,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        if (hexCipherText.isEmpty()) return null
        return try {
            val cipherData = hexToBytes(hexCipherText)
            rsaDecryptCore(cipherData, privateKey, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用 RSA 私钥字符串解密16进制编码的密文并返回字节数组。
     *
     * @param hexCipherText 16进制编码的密文。
     * @param privateKeyString Base64 编码的 RSA 私钥字符串。
     * @param mode RSA 解密模式。
     * @return 解密后的字节数组；如果解密失败，则返回 null。
     */
    fun rsaDecryptToBytesFromHex(
        hexCipherText: String,
        privateKeyString: String,
        mode: RSAMode = RSAMode.DEFAULT_ENCRYPTION_MODE
    ): ByteArray? {
        val privateKey = stringToPrivateKey(privateKeyString) ?: return null
        return rsaDecryptToBytesFromHex(hexCipherText, privateKey, mode)
    }

    // --- Utility Functions ---

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 要转换的字节数组。
     * @return 十六进制字符串。
     */
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt()
            result.append(hexChars[i shr 4 and 0x0F])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    /**
     * 将十六进制字符串转换为字节数组。
     *
     * @param hex 十六进制字符串。
     * @return 字节数组。
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "").uppercase()
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) {
            cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    fun getInitializationVectorConcise(): ByteArray {
        return byteArrayOf(
            0x41.toByte(),
            0x72.toByte(),
            0x65.toByte(),
            0x79.toByte(),
            0x6F.toByte(),
            0x75.toByte(),
            0x6D.toByte(),
            0x79.toByte(),
            0x53.toByte(),
            0x6E.toByte(),
            0x6F.toByte(),
            0x77.toByte(),
            0x6D.toByte(),
            0x61.toByte(),
            0x6E.toByte(),
            0x3F.toByte()
        )
    }

}
