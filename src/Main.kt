import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

// ==================== ДАННЫЕ РЕСТОРАНА ====================

// data class — класс, который хранит данные о заказе.
// val — значение нельзя переназначить; var — можно изменять.
data class Order(
    val id: String,                          // Номер заказа (String — строка)
    val dishName: String,                    // Название блюда
    var cookingProgress: Int = 0,            // Готовность в процентах (Int — целое число)
    var status: String = "Ожидает приготовления" // Текущий статус
)

// Результат проверки качества блюда.
// Double — число с дробной частью, например 9.4.
data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)

// ==================== 1. ПОДГОТОВКА ИНГРЕДИЕНТОВ ====================

// suspend — функция может использовать приостанавливающие операции (например, delay).
// ingredientName: String — входной параметр, название ингредиента.
// : String после скобок — тип результата, который вернёт функция.
// = — тело функции записано выражением, без обычного { return ... }.
suspend fun prepareIngredient(ingredientName: String): String =
    // withContext — выполняет блок с указанным контекстом и возвращает его результат.
    // Dispatchers.Default — диспетчер для вычислительных задач (общий пул потоков).
    withContext(Dispatchers.Default) {
        // delay(1500) — приостанавливает корутину на 1,5 секунды, НЕ блокируя поток.
        delay(1500)

        // when — выбор результата в зависимости от названия ингредиента.
        // lowercase() — переводим название в нижний регистр.
        when (ingredientName.lowercase()) {
            "лук" -> "Нарезанный лук"
            "масло" -> "Растопленное масло"
            "сливки" -> "Взбитые сливки"
            else -> "$ingredientName подготовлен" // else — для остальных вариантов
        }
        // Последнее выражение when становится результатом withContext и функции.
    }

// ==================== 2. ПРИГОТОВЛЕНИЕ ЗАКАЗА ====================

// coroutineScope — область корутин: запускает дочерние задачи и ждёт их завершения.
// Если ошибка из дочерней корутины не обработана, другие задачи могут отмениться.
// supervisorScope — другая область: ошибка одной дочерней задачи сама по себе
// не отменяет соседние. Здесь она не нужна: ошибку мы обрабатываем через try/catch.
suspend fun cookOrder(order: Order) = coroutineScope {

    // launch №1 — запускает корутину приготовления, не возвращающую результат блюда.
    launch {
        try {
            order.status = "Готовится"

            // while — повторяем, пока корутина активна И готовность меньше 100%.
            // isActive — проверяет, не отменили ли корутину.
            // && — логическое «И»: оба условия должны быть верными.
            while (isActive && order.cookingProgress < 100) {
                delay(500) // Каждые полсекунды увеличиваем готовность.

                if (!isActive) break // Если отменили — выходим из цикла.

                order.cookingProgress += 10 // += 10 означает «прибавить 10».

                // if — выбираем статус в зависимости от процента готовности.
                order.status = if (order.cookingProgress >= 100) "Готово" else "Готовится"

                println("[${order.id}] ${order.dishName}: ${order.cookingProgress}% — ${order.status}")
            }

            if (isActive && order.cookingProgress == 100) {
                println("[${order.id}] Приготовление завершено.")
            }
        } catch (e: CancellationException) {
            // catch — обрабатываем отмену, чтобы показать сообщение.
            order.status = "Отменён"
            println("[${order.id}] Готовка остановлена.")
            throw e // Передаём отмену дальше: не делаем вид, что задача завершилась обычно.
        }
    }

    // launch №2 — отдельная корутина контроля температуры.
    // Она выполняется конкурентно с приготовлением.
    launch {
        try {
            delay(2000) // Проверяем температуру спустя 2 секунды.
            if (isActive) {
                println("[${order.id}] Температура проверена: всё в норме.")
            }
        } catch (e: CancellationException) {
            println("[${order.id}] Контроль температуры остановлен.")
            throw e
        }
    }

    // launch №3 — отдельная корутина, следящая за готовностью блюда.
    launch {
        try {
            while (isActive && order.cookingProgress < 80) {
                delay(250) // Проверяем готовность каждые 0,25 секунды.

                // Для заказа FAIL специально вызываем ошибку — пункт задания.
                if (order.id == "FAIL" && order.cookingProgress >= 30) {
                    throw IllegalStateException("Блюдо подгорает!")
                }
            }

            if (isActive && order.cookingProgress >= 80) {
                println("[${order.id}] Блюдо почти готово, пора на финишную обработку.")
            }
        } catch (e: CancellationException) {
            println("[${order.id}] Мониторинг остановлен.")
            throw e
        } catch (e: IllegalStateException) {
            // try/catch перехватывает ошибку внутри этой корутины.
            // Поэтому остальные корутины (готовка и температура) продолжают работать.
            println("[${order.id}] Ошибка мониторинга: ${e.message}")
            println("[${order.id}] Остальные задачи продолжают работу.")
        }
    }
}

// ==================== 3. ПРОВЕРКА КАЧЕСТВА ====================

// Deferred<DishResult> — «обещание» будущего результата типа DishResult.
// async создаёт корутину с результатом; await() потом этот результат получает.
suspend fun checkDishQuality(orderId: String): Deferred<DishResult> {
    // Берём контекст текущей корутины, чтобы новая задача была связана с ней.
    // Это техническая строка нужна, чтобы вернуть Deferred, не дожидаясь проверки.
    val scope = CoroutineScope(currentCoroutineContext())

    return scope.async {
        delay(3000) // Проверка качества занимает 3 секунды.

        // Последнее выражение async — результат, который получит await().
        DishResult(
            orderId = orderId,
            dishName = "Блюдо заказа $orderId",
            qualityScore = 9.4
        )
    }
}

// ==================== 4. ОТМЕНА ЗАКАЗА ====================

// Job — объект для управления запущенной корутиной.
// cancel() запрашивает отмену, join() дожидается её завершения.
suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000) // Через 2 секунды клиент отменяет заказ.

    job.cancel() // Отменяем корутину приготовления этого заказа.
    job.join()   // Ждём, пока она действительно остановится.

    order.status = "Отменён"
    println("Заказ ${order.id} отменён, блюдо «${order.dishName}» снято с приготовления.")
}

// ==================== 5. ЗАПУСК ПРОГРАММЫ ====================

// main — точка входа: отсюда программа начинает работу.
// runBlocking — позволяет запускать корутины из обычного main
// и блокирует главный поток, пока его дочерние задачи не завершатся.
fun main() = runBlocking {
    println("========== ПОДГОТОВКА ИНГРЕДИЕНТОВ ==========")

    // async — запускает корутину и возвращает Deferred с будущим результатом.
    // Все три подготовки начинаются, не дожидаясь друг друга.
    val onion = async { prepareIngredient("лук") }
    val butter = async { prepareIngredient("масло") }
    val cream = async { prepareIngredient("сливки") }

    // await() — дождаться результата async и получить его значение.
    println(onion.await())
    println(butter.await())
    println(cream.await())

    println("\n========== ПАРАЛЛЕЛЬНАЯ ГОТОВКА ==========")

    // Создаём три объекта Order — три разных заказа.
    val normalOrder = Order("A-101", "Ризотто")
    val failureOrder = Order("FAIL", "Соус")  // Здесь будет ошибка мониторинга.
    val cancelledOrder = Order("C-303", "Стейк") // Этот заказ отменим.

    // launch возвращает Job. Сохраняем Job в переменную,
    // чтобы позже дождаться окончания или отменить нужную корутину.
    val normalJob = launch { cookOrder(normalOrder) }
    val failureJob = launch { cookOrder(failureOrder) }
    val cancelledJob = launch { cookOrder(cancelledOrder) }

    // Ещё одна корутина: спустя 2 секунды отменит только заказ C-303.
    val cancellationTask = launch { cancelOrder(cancelledOrder, cancelledJob) }

    // join() — дождаться завершения указанной корутины (Job).
    normalJob.join()
    failureJob.join()
    cancelledJob.join()
    cancellationTask.join()

    println("\nСтатусы заказов:")
    println("${normalOrder.id}: ${normalOrder.status}")
    println("${failureOrder.id}: ${failureOrder.status}")
    println("${cancelledOrder.id}: ${cancelledOrder.status}")

    println("\n========== ПРОВЕРКА КАЧЕСТВА ==========")

    // Функция вернёт Deferred, а не готовый DishResult.
    val qualityDeferred = checkDishQuality(normalOrder.id)

    // await() приостанавливает текущую корутину до готовности результата.
    val qualityResult = qualityDeferred.await()
    println("Качество ${qualityResult.dishName}: ${qualityResult.qualityScore}/10")

    println("\n========== КАНАЛ КУХНИ И РАЗДАЧИ ==========")

    // Channel — канал передачи данных между корутинами.
    // <DishResult> означает, что передаём только объекты DishResult.
    val channel = Channel<DishResult>()

    // Producer («производитель») — кухня отправляет готовые блюда в канал.
    val producer = launch {
        val readyDishes = listOf(
            DishResult("K-1", "Ризотто", 9.5),
            DishResult("K-2", "Стейк", 9.2),
            DishResult("K-3", "Суп", 8.8),
            DishResult("K-4", "Паста", 9.7),
            DishResult("K-5", "Десерт", 9.6)
        )

        for (result in readyDishes) {
            channel.send(result) // send() — передать блюдо в канал.
            println("[Кухня] Отправлено на раздачу: ${result.dishName}")
        }

        channel.close() // Больше блюд не будет; закрываем канал.
    }

    // Consumer («потребитель») — метрдотель получает блюда из канала.
    val consumer = launch {
        // for по каналу читает результаты до тех пор, пока канал не закроется
        // и все отправленные элементы не будут получены.
        for (result in channel) {
            println("[Метрдотель] Выдано: ${result.dishName}; заказ ${result.orderId}; оценка ${result.qualityScore}/10")
        }

        println("[Метрдотель] Все блюда получены.")
    }

    producer.join()
    consumer.join()

    println("\nВсе демонстрации завершены.")
}
