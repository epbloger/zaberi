package ru.epbloger.zaberi

import java.time.LocalDate

internal const val MAX_ORDER_AMOUNT = 999999999999L

/** The same invariants apply to UI writes and imported backups. */
internal fun validateOrder(order: Order) {
    require(order.id.isNotBlank() && order.id.length <= 128) { "Некорректный идентификатор заказа" }
    require(order.name.isNotBlank() && order.name.length <= 200) { "Укажите название товара (до 200 символов)" }
    require(order.store.isNotBlank() && order.store.length <= 200) { "Укажите магазин (до 200 символов)" }
    require(order.point.length <= 200 && order.category.length <= 200) { "Адрес или категория слишком длинные" }
    require(order.link.length <= 8192 && order.note.length <= 10000) { "Ссылка или заметка слишком длинные" }
    require(validPoint(order.pointLat, order.pointLon) && (order.pointLat == null || order.point.isNotBlank())) { "Некорректная точка выдачи" }
    require(order.paid in 0..MAX_ORDER_AMOUNT && (order.price == null || order.price in order.paid..MAX_ORDER_AMOUNT)) { "Проверьте стоимость и оплату" }
    require(order.expectedRefund in 0..order.paid && order.receivedRefund in 0..order.expectedRefund) { "Проверьте суммы возврата" }
    require(order.state != Status.REFUND || order.receivedRefund < order.expectedRefund) { "Возврат уже завершён" }
    LocalDate.parse(order.created)
    order.deadline?.let { LocalDate.parse(it) }
}

internal fun validateNewOrder(order: Order) {
    validateOrder(order)
    require(order.state in setOf(Status.TRANSIT, Status.READY, Status.RECEIVED)) { "Недопустимый начальный статус" }
    require(order.expectedRefund == 0L && order.receivedRefund == 0L && !order.cancelled && !order.returned) { "Недопустимое состояние нового заказа" }
}
