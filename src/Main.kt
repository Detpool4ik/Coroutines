import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

data class Order(
    val id: String,
    val dishName: String,
    var cookingProgress: Int = 0,
    var status: String = "Ожидает приготовления"
)

data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)

// Подготовка ингредиента на Dispatchers.Default.
suspend fun prepareIngredient(ingredientName: String): String =
    withContext(Dispatchers.Default) {
        delay(1500)

        when (ingredientName.lowercase()) {
            "лук" -> "Нарезанный лук"
            "масло" -> "Растопленное масло"
            "сливки" -> "Взбитые сливки"
            else -> "$ingredientName подготовлен"
        }
    }

// Три основные задачи заказа работают параллельно.
// SupervisorScope не даёт ошибке одной дочерней задачи отменить остальные.
suspend fun cookOrder(order: Order) = coroutineScope {
    supervisorScope {
        // 1. Готовка
        launch {
            try {
                order.status = "Готовится"

                while (isActive && order.cookingProgress < 100) {
                    delay(500)
                    if (!isActive) break

                    order.cookingProgress =
                        (order.cookingProgress + 10).coerceAtMost(100)

                    order.status =
                        if (order.cookingProgress >= 100) "Готово" else "Готовится"

                    println(
                        "[${order.id}] ${order.dishName}: " +
                                "${order.cookingProgress}% — ${order.status}"
                    )
                }

                if (isActive && order.cookingProgress >= 100) {
                    println("[${order.id}] Готовка завершена.")
                }
            } catch (e: CancellationException) {
                order.status = "Отменён"
                println("[${order.id}] Процесс готовки остановлен.")
                throw e
            }
        }

        // 2. Контроль температуры
        launch {
            try {
                delay(2000)

                if (isActive) {
                    println("[${order.id}] Температура проверена: всё в норме.")
                }
            } catch (e: CancellationException) {
                println("[${order.id}] Контроль температуры остановлен.")
                throw e
            }
        }

        // 3. Мониторинг готовности.
        // Для заказа с ID "FAIL" намеренно создаём ошибку,
        // чтобы показать, что она не останавливает остальные задачи.
        launch {
            try {
                while (isActive && order.cookingProgress < 80) {
                    delay(250)

                    if (order.id == "FAIL" && order.cookingProgress >= 30) {
                        throw IllegalStateException("Блюдо подгорает!")
                    }
                }

                if (isActive && order.cookingProgress >= 80) {
                    println(
                        "[${order.id}] Блюдо почти готово, " +
                                "пора на финишную обработку"
                    )
                }
            } catch (e: CancellationException) {
                println("[${order.id}] Мониторинг готовности остановлен.")
                throw e
            } catch (e: IllegalStateException) {
                println(
                    "[${order.id}] Ошибка мониторинга: ${e.message} " +
                            "Остальные задачи продолжают работу."
                )
            }
        }
    }
}

// Возвращает Deferred, чтобы результат можно было получить через await().
suspend fun checkDishQuality(orderId: String): Deferred<DishResult> {
    val scope = CoroutineScope(currentCoroutineContext())

    return scope.async {
        delay(3000)

        DishResult(
            orderId = orderId,
            dishName = "Блюдо заказа $orderId",
            qualityScore = 9.4
        )
    }
}

// Отменяет приготовление заказа через 2 секунды.
suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000)
    job.cancel()
    order.status = "Отменён"

    println(
        "Заказ ${order.id} отменён, блюдо «${order.dishName}» " +
                "снято с приготовления"
    )
}

fun main() = runBlocking {
    println("========== 1. Подготовка ингредиентов ==========")

    // Три ингредиента подготавливаются параллельно.
    val ingredients = listOf(
        async { prepareIngredient("лук") },
        async { prepareIngredient("масло") },
        async { prepareIngredient("сливки") }
    ).awaitAll()

    ingredients.forEach { println(it) }

    println("\n========== 2. Параллельная готовка ==========")

    val normalOrder = Order("A-101", "Ризотто")
    val failureOrder = Order("FAIL", "Соус")
    val cancelledOrder = Order("C-303", "Стейк")

    val normalJob = launch {
        cookOrder(normalOrder)
    }

    val failureJob = launch {
        cookOrder(failureOrder)
    }

    val cancelledJob = launch {
        cookOrder(cancelledOrder)
    }

    // Этот запуск отменит только приготовление стейка.
    val cancellationTask = launch {
        cancelOrder(cancelledOrder, cancelledJob)
    }

    joinAll(normalJob, failureJob, cancelledJob, cancellationTask)

    println("\nСтатусы заказов:")
    println("${normalOrder.id}: ${normalOrder.status}")
    println("${failureOrder.id}: ${failureOrder.status}")
    println("${cancelledOrder.id}: ${cancelledOrder.status}")

    println("\n========== 3. Проверка качества через async/await ==========")

    val qualityDeferred = checkDishQuality(normalOrder.id)
    val qualityResult = qualityDeferred.await()

    println(
        "Качество ${qualityResult.dishName}: " +
                "${qualityResult.qualityScore}/10"
    )

    println("\n========== 4. Канал кухни и раздачи ==========")

    val channel = Channel<DishResult>(Channel.UNLIMITED)

    val producer = launch {
        val readyDishes = listOf(
            DishResult("K-1", "Ризотто", 9.5),
            DishResult("K-2", "Стейк", 9.2),
            DishResult("K-3", "Суп", 8.8),
            DishResult("K-4", "Паста", 9.7),
            DishResult("K-5", "Десерт", 9.6)
        )

        for (result in readyDishes) {
            channel.send(result)
            println("[Кухня] Отправлено на раздачу: ${result.dishName}")
        }

        channel.close()
    }

    val consumer = launch {
        for (result in channel) {
            println(
                "[Метрдотель] Выдано: ${result.dishName}; " +
                        "заказ ${result.orderId}; " +
                        "оценка ${result.qualityScore}/10"
            )
        }

        println("[Метрдотель] Все блюда из канала получены.")
    }

    joinAll(producer, consumer)

    println("\nВсе демонстрации завершены.")
}
