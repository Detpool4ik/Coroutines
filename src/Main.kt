
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