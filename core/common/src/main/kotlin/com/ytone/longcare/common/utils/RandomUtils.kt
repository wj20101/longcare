package com.ytone.longcare.common.utils

import java.security.SecureRandom
import java.util.Random
import kotlin.random.asKotlinRandom

object RandomUtils {

    private const val ALLOWED_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val secureRandom = SecureRandom() // 用于密码学安全的随机数，如果不需要那么高的安全性，可以使用下面的普通Random
    private val random = Random()
    private val kotlinRandom = random.asKotlinRandom() // Kotlin 风格的 Random

    /**
     * 生成指定长度的随机字符串，包含大小写字母和数字。
     *
     * @param length 目标字符串的长度。
     * @return 生成的随机字符串。
     * @throws IllegalArgumentException 如果 length 小于等于 0。
     */
    fun generateRandomString(length: Int): String {
        require(length > 0) { "Length must be greater than 0" }
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val randomIndex = secureRandom.nextInt(ALLOWED_CHARACTERS.length)
            sb.append(ALLOWED_CHARACTERS[randomIndex])
        }
        return sb.toString()
    }

    /**
     * 生成指定长度的随机字符串，使用 Kotlin 的 Random API。
     * 包含大小写字母和数字。
     *
     * @param length 目标字符串的长度。
     * @return 生成的随机字符串。
     * @throws IllegalArgumentException 如果 length 小于等于 0。
     */
    fun generateRandomStringKotlin(length: Int): String {
        require(length > 0) { "Length must be greater than 0" }
        return (1..length)
            .map { kotlinRandom.nextInt(0, ALLOWED_CHARACTERS.length) }
            .map(ALLOWED_CHARACTERS::get)
            .joinToString("")
    }

    /**
     * 生成一个指定范围内的随机整数 (包含 min 和 max)。
     *
     * @param min 最小可能值 (包含)。
     * @param max 最大可能值 (包含)。
     * @return 范围内的随机整数。
     * @throws IllegalArgumentException 如果 min 大于 max。
     */
    fun getRandomInt(min: Int, max: Int): Int {
        require(min <= max) { "Min value must be less than or equal to max value" }
        // random.nextInt(max - min + 1) 会生成 [0, max - min] 范围的数
        return random.nextInt(max - min + 1) + min
    }

    /**
     * 使用 Kotlin Random 生成一个指定范围内的随机整数 (包含 start 和 endInclusive)。
     *
     * @param start 最小可能值 (包含)。
     * @param endInclusive 最大可能值 (包含)。
     * @return 范围内的随机整数。
     */
    fun getRandomIntKotlin(start: Int, endInclusive: Int): Int {
        return kotlinRandom.nextInt(start, endInclusive + 1) // nextInt 的上界是排他的，所以要 +1
    }


    /**
     * 生成一个指定范围内的随机长整数 (包含 min 和 max)。
     *
     * @param min 最小可能值 (包含)。
     * @param max 最大可能值 (包含)。
     * @return 范围内的随机长整数。
     * @throws IllegalArgumentException 如果 min 大于 max。
     */
    fun getRandomLong(min: Long, max: Long): Long {
        require(min <= max) { "Min value must be less than or equal to max value" }
        // nextLong() 返回整个 Long 范围的数，需要调整到 [min, max]
        if (min == max) return min
        return min + (random.nextDouble() * (max - min + 1)).toLong()
    }

    /**
     * 使用 Kotlin Random 生成一个指定范围内的随机长整数 (包含 start 和 endInclusive)。
     *
     * @param start 最小可能值 (包含)。
     * @param endInclusive 最大可能值 (包含)。
     * @return 范围内的随机长整数。
     */
    fun getRandomLongKotlin(start: Long, endInclusive: Long): Long {
        return kotlinRandom.nextLong(start, endInclusive + 1L) // nextLong 的上界是排他的，所以要 +1
    }

    /**
     * 生成一个随机布尔值。
     *
     * @return true 或 false。
     */
    fun getRandomBoolean(): Boolean {
        return random.nextBoolean()
    }

    /**
     * 使用 Kotlin Random 生成一个随机布尔值。
     *
     * @return true 或 false。
     */
    fun getRandomBooleanKotlin(): Boolean {
        return kotlinRandom.nextBoolean()
    }

    /**
     * 从列表中随机选择一个元素。
     *
     * @param T 列表中元素的类型。
     * @param list 要从中选择的列表。
     * @return 随机选择的元素，如果列表为空则返回 null。
     */
    fun <T> getRandomElementFromList(list: List<T>): T? {
        if (list.isEmpty()) {
            return null
        }
        return list[random.nextInt(list.size)]
    }

    /**
     * 使用 Kotlin Random 从列表中随机选择一个元素。
     *
     * @param T 列表中元素的类型。
     * @param list 要从中选择的列表。
     * @return 随机选择的元素。
     * @throws NoSuchElementException 如果列表为空。
     */
    fun <T> getRandomElementFromListKotlin(list: List<T>): T {
        return list.random(kotlinRandom) // list.random() 扩展函数，可以传入 Random 实例
    }

    /**
     * 从数组中随机选择一个元素。
     *
     * @param T 数组中元素的类型。
     * @param array 要从中选择的数组。
     * @return 随机选择的元素，如果数组为空则返回 null。
     */
    fun <T> getRandomElementFromArray(array: Array<T>): T? {
        if (array.isEmpty()) {
            return null
        }
        return array[random.nextInt(array.size)]
    }

    /**
     * 使用 Kotlin Random 从数组中随机选择一个元素。
     *
     * @param T 数组中元素的类型。
     * @param array 要从中选择的数组。
     * @return 随机选择的元素。
     * @throws NoSuchElementException 如果数组为空。
     */
    fun <T> getRandomElementFromArrayKotlin(array: Array<T>): T {
        return array.random(kotlinRandom)
    }

    /**
     * 生成一个0.0 (包含) 到 1.0 (不包含) 之间的随机浮点数。
     *
     * @return 随机浮点数。
     */
    fun getRandomDouble(): Double {
        return random.nextDouble()
    }

    /**
     * 使用 Kotlin Random 生成一个0.0 (包含) 到 1.0 (不包含) 之间的随机浮点数。
     *
     * @return 随机浮点数。
     */
    fun getRandomDoubleKotlin(): Double {
        return kotlinRandom.nextDouble()
    }

    /**
     * 生成一个指定范围内的随机 Double (包含 min，不包含 max)。
     * 如果需要包含 max，则需要对 max 做微调或使用不同的逻辑。
     *
     * @param min 最小可能值 (包含)。
     * @param max 最大可能值 (不包含)。
     * @return 范围内的随机 Double。
     * @throws IllegalArgumentException 如果 min 大于或等于 max。
     */
    fun getRandomDouble(min: Double, max: Double): Double {
        require(min < max) { "Min value must be less than max value for a range." }
        return min + (random.nextDouble() * (max - min))
    }

    /**
     * 使用 Kotlin Random 生成一个指定范围内的随机 Double (包含 start，不包含 until)。
     *
     * @param start 最小可能值 (包含)。
     * @param until 最大可能值 (不包含)。
     * @return 范围内的随机 Double。
     */
    fun getRandomDoubleKotlin(start: Double, until: Double): Double {
        return kotlinRandom.nextDouble(start, until)
    }

}