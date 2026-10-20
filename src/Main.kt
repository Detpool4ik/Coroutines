


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